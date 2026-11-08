package com.novatrade.demo.trading

import com.novatrade.demo.Brand

import com.novatrade.demo.data.MarketRepository
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountMetrics
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.model.CloseReason
import com.novatrade.demo.model.ClosedTrade
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.model.OrderType
import com.novatrade.demo.model.PendingOrder
import com.novatrade.demo.model.Position
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Side
import com.novatrade.demo.model.TransactionKind
import com.novatrade.demo.model.Transaction
import com.novatrade.demo.util.format
import com.novatrade.demo.util.lots as formatLots
import com.novatrade.demo.util.percent
import com.novatrade.demo.util.signedUsd
import com.novatrade.demo.util.usd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random

data class Notice(val time: Long, val message: String)

sealed interface OrderResult {
    data class Success(val message: String) : OrderResult
    data class Error(val message: String) : OrderResult
}

/**
 * Virtual-money trading: market orders, SL/TP, margin and stop-out.
 * Must be used from the main thread only (same as [MarketRepository]).
 */
@OptIn(FlowPreview::class)
class TradingEngine(
    private val scope: CoroutineScope,
    private val market: MarketRepository,
    private val store: AccountStore,
) {
    private val _book = MutableStateFlow(store.load()?.repaired() ?: defaultBook())

    val accounts: StateFlow<List<Account>> = _book.map { it.accounts }
        .stateIn(scope, SharingStarted.Eagerly, _book.value.accounts)

    /** The selected account. */
    val account: StateFlow<Account> = _book.map { it.active }
        .stateIn(scope, SharingStarted.Eagerly, _book.value.active)

    val metrics: StateFlow<AccountMetrics> = combine(_book, market.quotes) { book, quotes -> metricsFor(book.active, quotes) }
        .stateIn(scope, SharingStarted.Eagerly, metricsFor(_book.value.active, emptyMap()))

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Messages for automatic actions (SL, TP, stop out, pending fills). */
    val events = _events.asSharedFlow()

    private val _notifications = MutableStateFlow<List<Notice>>(emptyList())
    /** Recent trading activity, newest first, for the notifications screen. */
    val notifications: StateFlow<List<Notice>> = _notifications.asStateFlow()

    private val active: Account get() = _book.value.active
    private val activeNumber: Long get() = _book.value.activeNumber

    init {
        scope.launch { market.quotes.collect(::checkTriggers) }
        scope.launch {
            _book.drop(1).debounce(400).collect { book ->
                withContext(Dispatchers.IO) { runCatching { store.save(book) } }
            }
        }
    }

    fun switchTo(number: Long) = _book.update { it.copy(activeNumber = number) }

    /** Opens a new, empty account and selects it, like on a real broker: fund it with a deposit. */
    fun createAccount(type: AccountType, leverage: Int, kind: String = "Standard", nickname: String = "", platform: String = Brand.name) {
        val acc = newAccount(type, 0.0, leverage).copy(kind = kind, nickname = nickname.trim(), platform = platform)
        _book.update { it.copy(accounts = it.accounts + acc, activeNumber = acc.number) }
        log("${type.label} account #${acc.number} opened")
    }

    fun rename(number: Long, nickname: String) = edit(number) { it.copy(nickname = nickname.trim()) }

    /** Hides an account from the lists. Only an unselected account with nothing open can be archived. */
    fun archive(number: Long): OrderResult {
        val acc = accountOf(number)
        if (number == activeNumber) return OrderResult.Error("Switch to another account before archiving this one")
        if (acc.positions.isNotEmpty() || acc.pending.isNotEmpty()) return OrderResult.Error("Close the account's positions and orders first")
        edit(number) { it.copy(archived = true) }
        return OrderResult.Success("Account #$number was archived").also(::logResult)
    }

    fun restore(number: Long): OrderResult {
        edit(number) { it.copy(archived = false) }
        return OrderResult.Success("Trading account #$number has been successfully reactivated.").also(::logResult)
    }

    /** Moves virtual funds between two of the user's accounts. */
    fun transfer(from: Long, to: Long, amount: Double): OrderResult {
        if (from == to) return OrderResult.Error("Choose two different accounts")
        if (amount <= 0) return OrderResult.Error("Enter an amount")
        val free = metricsFor(accountOf(from), market.quotes.value).freeMargin
        if (amount > free) return OrderResult.Error("You can transfer up to ${usd(free.coerceAtLeast(0.0))}")
        val now = System.currentTimeMillis()
        edit(from) {
            it.copy(
                balance = it.balance - amount, totalDeposits = it.totalDeposits - amount,
                transactions = listOf(Transaction(now, TransactionKind.TRANSFER_OUT, amount, to)) + it.transactions,
            )
        }
        edit(to) {
            it.copy(
                balance = it.balance + amount, totalDeposits = it.totalDeposits + amount,
                transactions = listOf(Transaction(now, TransactionKind.TRANSFER_IN, amount, from)) + it.transactions,
            )
        }
        return OrderResult.Success("Transferred ${usd(amount)} to #$to").also(::logResult)
    }

    /** Deletes every account and starts over with a single demo account. */
    fun terminate() {
        _book.value = defaultBook()
        _notifications.value = emptyList()
    }

    fun open(symbol: String, side: Side, lots: Double, stopLoss: Double?, takeProfit: Double?): OrderResult {
        val quote = market.quotes.value[symbol] ?: return OrderResult.Error("Market closed: no price yet")
        if (!quote.open) return OrderResult.Error(MARKET_CLOSED)
        validateLots(lots)?.let { return OrderResult.Error(it) }
        validateStops(side, closePrice(side, quote), stopLoss, takeProfit)?.let { return OrderResult.Error(it) }
        return openAt(activeNumber, symbol, side, lots, openPrice(side, quote), stopLoss, takeProfit).also(::logResult)
    }

    /** Places a Limit or Stop order; the type follows from where [price] is relative to the market. */
    fun placePending(symbol: String, side: Side, lots: Double, price: Double, stopLoss: Double?, takeProfit: Double?): OrderResult {
        val inst = Instruments[symbol]
        val quote = market.quotes.value[symbol] ?: return OrderResult.Error("Market closed: no price yet")
        if (!quote.open) return OrderResult.Error(MARKET_CLOSED)
        validateLots(lots)?.let { return OrderResult.Error(it) }
        val marketPrice = openPrice(side, quote)
        if (abs(price - marketPrice) < quote.spread) return OrderResult.Error("Price is too close to the market")
        validateStops(side, price, stopLoss, takeProfit)?.let { return OrderResult.Error(it) }
        val type = when (side) {
            Side.BUY -> if (price < marketPrice) OrderType.LIMIT else OrderType.STOP
            Side.SELL -> if (price > marketPrice) OrderType.LIMIT else OrderType.STOP
        }
        val acc = active
        val order = PendingOrder(
            id = acc.nextId, symbol = symbol, side = side, type = type, lots = lots, price = price,
            createdTime = System.currentTimeMillis(), stopLoss = stopLoss, takeProfit = takeProfit,
        )
        edit(acc.number) { it.copy(pending = it.pending + order, nextId = it.nextId + 1) }
        return OrderResult.Success("${order.label} ${formatLots(lots)} $symbol at ${inst.format(price)} placed").also(::logResult)
    }

    fun cancelPending(id: Long): OrderResult {
        if (active.pending.none { it.id == id }) return OrderResult.Error("Order not found")
        edit(activeNumber) { acc -> acc.copy(pending = acc.pending.filter { it.id != id }) }
        return OrderResult.Success("Order #$id cancelled")
    }

    private fun openAt(
        number: Long, symbol: String, side: Side, lots: Double, price: Double, stopLoss: Double?, takeProfit: Double?,
    ): OrderResult {
        val inst = Instruments[symbol]
        val acc = accountOf(number)
        val margin = marginFor(inst, lots, price, acc.leverage, market.quotes.value)
        val free = metricsFor(acc, market.quotes.value).freeMargin
        if (margin > free) {
            return OrderResult.Error("Not enough money. Required margin ${usd(margin)}, free ${usd(free)}")
        }
        val position = Position(
            id = acc.nextId, symbol = symbol, side = side, lots = lots, openPrice = price,
            openTime = System.currentTimeMillis(), margin = margin, stopLoss = stopLoss, takeProfit = takeProfit,
        )
        edit(number) { it.copy(positions = it.positions + position, nextId = it.nextId + 1) }
        return OrderResult.Success("${side.label} ${formatLots(lots)} $symbol at ${inst.format(price)}")
    }

    fun close(id: Long, reason: CloseReason = CloseReason.MANUAL): OrderResult {
        val position = active.positions.find { it.id == id } ?: return OrderResult.Error("Position not found")
        val quote = market.quotes.value[position.symbol] ?: return OrderResult.Error("No price for ${position.symbol}")
        if (!quote.open) return OrderResult.Error(MARKET_CLOSED)
        val trade = closeAt(activeNumber, position, quote, reason)
        return OrderResult.Success("Closed #${trade.id} ${trade.symbol}: ${signedUsd(trade.profit)}").also(::logResult)
    }

    /** Closes [lots] of a position; the rest stays open with its margin reduced in proportion. */
    fun partialClose(id: Long, lots: Double): OrderResult {
        val position = active.positions.find { it.id == id } ?: return OrderResult.Error("Position not found")
        if (lots >= position.lots - 1e-9) return close(id)
        if (lots < MIN_LOT) return OrderResult.Error("Volume must be at least $MIN_LOT lots")
        val quote = market.quotes.value[position.symbol] ?: return OrderResult.Error("No price")
        if (!quote.open) return OrderResult.Error(MARKET_CLOSED)
        val price = closePrice(position, quote)
        val profit = profitFor(Instruments[position.symbol], position.side, lots, position.openPrice, price, market.quotes.value)
        val trade = ClosedTrade(
            id = position.id, symbol = position.symbol, side = position.side, lots = lots, openPrice = position.openPrice,
            closePrice = price, openTime = position.openTime, closeTime = System.currentTimeMillis(), profit = profit,
            reason = CloseReason.PARTIAL, stopLoss = position.stopLoss, takeProfit = position.takeProfit,
        )
        val left = ((position.lots - lots) * 100).roundToLong() / 100.0
        edit(activeNumber) { acc ->
            acc.copy(
                balance = acc.balance + profit,
                positions = acc.positions.map {
                    if (it.id == id) it.copy(lots = left, margin = it.margin * left / it.lots) else it
                },
                history = listOf(trade) + acc.history,
            )
        }
        return OrderResult.Success("Closed ${formatLots(lots)} of #$id: ${signedUsd(profit)}").also(::logResult)
    }

    /** Closes two opposite positions on the same instrument against each other. */
    fun closeBy(id: Long, oppositeId: Long): OrderResult {
        val a = active.positions.find { it.id == id } ?: return OrderResult.Error("Position not found")
        val b = active.positions.find { it.id == oppositeId } ?: return OrderResult.Error("Position not found")
        if (a.symbol != b.symbol || a.side == b.side) return OrderResult.Error("Choose an opposite position on ${a.symbol}")
        val quote = market.quotes.value[a.symbol] ?: return OrderResult.Error("No price")
        if (!quote.open) return OrderResult.Error(MARKET_CLOSED)
        val lots = minOf(a.lots, b.lots)
        // Closing by cancels the spread: both legs close at the other leg's open price.
        val inst = Instruments[a.symbol]
        val profit = profitFor(inst, a.side, lots, a.openPrice, b.openPrice, market.quotes.value)
        val now = System.currentTimeMillis()
        edit(activeNumber) { acc ->
            val rest = acc.positions.mapNotNull { p ->
                when (p.id) {
                    a.id, b.id -> {
                        val left = ((p.lots - lots) * 100).roundToLong() / 100.0
                        if (left < MIN_LOT) null else p.copy(lots = left, margin = p.margin * left / p.lots)
                    }
                    else -> p
                }
            }
            acc.copy(
                balance = acc.balance + profit,
                positions = rest,
                history = listOf(
                    ClosedTrade(a.id, a.symbol, a.side, lots, a.openPrice, b.openPrice, a.openTime, now, profit, CloseReason.CLOSE_BY, a.stopLoss, a.takeProfit),
                    ClosedTrade(b.id, b.symbol, b.side, lots, b.openPrice, b.openPrice, b.openTime, now, 0.0, CloseReason.CLOSE_BY, b.stopLoss, b.takeProfit),
                ) + acc.history,
            )
        }
        return OrderResult.Success("Closed #$id by #$oppositeId: ${signedUsd(profit)}").also(::logResult)
    }

    /** Closes every open position on [symbol]. */
    fun closeSymbol(symbol: String): OrderResult {
        val positions = active.positions.filter { it.symbol == symbol }
        if (positions.isEmpty()) return OrderResult.Error("No open positions")
        val quote = market.quotes.value[symbol] ?: return OrderResult.Error("No price")
        if (!quote.open) return OrderResult.Error(MARKET_CLOSED)
        val total = positions.sumOf { closeAt(activeNumber, it, quote, CloseReason.MANUAL).profit }
        return OrderResult.Success("Closed ${positions.size} position${if (positions.size > 1) "s" else ""}: ${signedUsd(total)}").also(::logResult)
    }

    fun closeAll(): OrderResult {
        val positions = active.positions
        if (positions.isEmpty()) return OrderResult.Error("No open positions")
        val quotes = market.quotes.value
        val total = positions.sumOf { p -> quotes[p.symbol]?.takeIf { it.open }?.let { closeAt(activeNumber, p, it, CloseReason.MANUAL).profit } ?: 0.0 }
        return OrderResult.Success("Closed ${positions.size} positions: ${signedUsd(total)}").also(::logResult)
    }

    fun modify(id: Long, stopLoss: Double?, takeProfit: Double?): OrderResult {
        val position = active.positions.find { it.id == id } ?: return OrderResult.Error("Position not found")
        val quote = market.quotes.value[position.symbol] ?: return OrderResult.Error("No price")
        validateStops(position.side, closePrice(position.side, quote), stopLoss, takeProfit)?.let { return OrderResult.Error(it) }
        edit(activeNumber) { acc ->
            acc.copy(positions = acc.positions.map {
                if (it.id == id) it.copy(stopLoss = stopLoss, takeProfit = takeProfit) else it
            })
        }
        return OrderResult.Success("Position #$id modified")
    }

    fun setLeverage(leverage: Int) = edit(activeNumber) { it.copy(leverage = leverage) }

    fun deposit(amount: Double, number: Long = activeNumber) {
        edit(number) {
            it.copy(
                balance = it.balance + amount, totalDeposits = it.totalDeposits + amount,
                transactions = listOf(Transaction(System.currentTimeMillis(), TransactionKind.DEPOSIT, amount)) + it.transactions,
            )
        }
        log("Deposit ${usd(amount)} to #$number")
    }

    fun withdraw(amount: Double, number: Long = activeNumber): OrderResult {
        val free = metricsFor(accountOf(number), market.quotes.value).freeMargin
        if (amount > free) return OrderResult.Error("You can withdraw up to ${usd(free.coerceAtLeast(0.0))}")
        edit(number) {
            it.copy(
                balance = it.balance - amount, totalDeposits = it.totalDeposits - amount,
                transactions = listOf(Transaction(System.currentTimeMillis(), TransactionKind.WITHDRAWAL, amount)) + it.transactions,
            )
        }
        return OrderResult.Success("Withdrew ${usd(amount)} (virtual)").also(::logResult)
    }

    /** Clears the selected account's trades and sets a new balance; the account number stays. */
    fun reset(balance: Double) = edit(activeNumber) {
        Account(number = it.number, type = it.type, kind = it.kind, balance = balance, leverage = it.leverage, totalDeposits = balance)
    }

    fun profitOf(position: Position, quote: Quote?): Double {
        quote ?: return 0.0
        return profitFor(Instruments[position.symbol], position.side, position.lots, position.openPrice, closePrice(position, quote), market.quotes.value)
    }

    fun requiredMargin(symbol: String, lots: Double, price: Double): Double =
        marginFor(Instruments[symbol], lots, price, active.leverage, market.quotes.value)

    /** USD gained or lost per 1.0 move in price for [lots]. */
    fun usdPerPoint(inst: Instrument, lots: Double): Double =
        inst.contractSize * lots * usdRate(inst.quoteCcy, market.quotes.value)

    /** USD value of [amount] in currency [ccy] at current rates. */
    fun toUsd(amount: Double, ccy: String): Double = amount * usdRate(ccy, market.quotes.value)

    fun metricsFor(acc: Account, quotes: Map<String, Quote>): AccountMetrics {
        val floating = acc.positions.sumOf { profitOf(it, quotes[it.symbol]) }
        val margin = acc.positions.sumOf { it.margin }
        val equity = acc.balance + floating
        return AccountMetrics(
            balance = acc.balance,
            equity = equity,
            margin = margin,
            freeMargin = equity - margin,
            marginLevel = if (margin > 0) equity / margin * 100 else null,
            floatingProfit = floating,
        )
    }

    private fun accountOf(number: Long) = _book.value.accounts.first { it.number == number }

    private fun edit(number: Long, transform: (Account) -> Account) = _book.update { book ->
        book.copy(accounts = book.accounts.map { if (it.number == number) transform(it) else it })
    }

    /** Adds a line to the notifications list (e.g. a fired price alert). */
    fun notify(message: String) = log(message)

    private fun log(message: String) {
        _notifications.update { (listOf(Notice(System.currentTimeMillis(), message)) + it).take(100) }
    }

    private fun logResult(result: OrderResult) {
        if (result is OrderResult.Success) log(result.message)
    }

    /** Automatic actions: logged and shown as a pop-up. */
    private fun announce(message: String) {
        log(message)
        _events.tryEmit(message)
    }

    private fun closeAt(number: Long, position: Position, quote: Quote, reason: CloseReason): ClosedTrade {
        val price = closePrice(position, quote)
        val trade = ClosedTrade(
            id = position.id, symbol = position.symbol, side = position.side, lots = position.lots,
            openPrice = position.openPrice, closePrice = price, openTime = position.openTime,
            closeTime = System.currentTimeMillis(),
            profit = profitFor(Instruments[position.symbol], position.side, position.lots, position.openPrice, price, market.quotes.value),
            reason = reason, stopLoss = position.stopLoss, takeProfit = position.takeProfit,
        )
        edit(number) { acc ->
            acc.copy(
                balance = acc.balance + trade.profit,
                positions = acc.positions.filter { it.id != position.id },
                history = listOf(trade) + acc.history,
            )
        }
        return trade
    }

    private fun checkTriggers(quotes: Map<String, Quote>) {
        for (number in _book.value.accounts.map { it.number }) checkTriggers(number, quotes)
    }

    private fun checkTriggers(number: Long, quotes: Map<String, Quote>) {
        val tag = if (number == activeNumber) "" else " (#$number)"
        for (o in accountOf(number).pending) {
            val quote = quotes[o.symbol]?.takeIf { it.open } ?: continue
            val price = openPrice(o.side, quote)
            val hit = when (o.side) {
                Side.BUY -> if (o.type == OrderType.LIMIT) price <= o.price else price >= o.price
                Side.SELL -> if (o.type == OrderType.LIMIT) price >= o.price else price <= o.price
            }
            if (!hit) continue
            edit(number) { acc -> acc.copy(pending = acc.pending.filter { it.id != o.id }) }
            val result = openAt(number, o.symbol, o.side, o.lots, price, o.stopLoss, o.takeProfit)
            announce(
                when (result) {
                    is OrderResult.Success -> "${o.label} #${o.id} filled$tag: ${result.message}"
                    is OrderResult.Error -> "${o.label} #${o.id} cancelled$tag: ${result.message}"
                }
            )
        }

        for (p in accountOf(number).positions) {
            val quote = quotes[p.symbol]?.takeIf { it.open } ?: continue
            val price = closePrice(p, quote)
            val reason = when (p.side) {
                Side.BUY -> when {
                    p.stopLoss != null && price <= p.stopLoss -> CloseReason.STOP_LOSS
                    p.takeProfit != null && price >= p.takeProfit -> CloseReason.TAKE_PROFIT
                    else -> null
                }
                Side.SELL -> when {
                    p.stopLoss != null && price >= p.stopLoss -> CloseReason.STOP_LOSS
                    p.takeProfit != null && price <= p.takeProfit -> CloseReason.TAKE_PROFIT
                    else -> null
                }
            } ?: continue
            val trade = closeAt(number, p, quote, reason)
            announce("${reason.label}$tag: #${trade.id} ${trade.symbol} ${signedUsd(trade.profit)}")
        }

        // Stop out: close the biggest loser until the margin level recovers.
        while (true) {
            val acc = accountOf(number)
            val level = metricsFor(acc, quotes).marginLevel ?: break
            if (level >= STOP_OUT_LEVEL || acc.positions.isEmpty()) break
            val worst = acc.positions.minBy { profitOf(it, quotes[it.symbol]) }
            val quote = quotes[worst.symbol] ?: break
            val trade = closeAt(number, worst, quote, CloseReason.STOP_OUT)
            announce("Stop out$tag at ${percent(level)} margin level: #${trade.id} ${signedUsd(trade.profit)}")
        }
    }

    /** [price] is where the position would be closed now (or the pending order's price). */
    private fun validateStops(side: Side, price: Double, stopLoss: Double?, takeProfit: Double?): String? = when (side) {
        Side.BUY -> when {
            stopLoss != null && stopLoss >= price -> "Stop Loss must be below the price"
            takeProfit != null && takeProfit <= price -> "Take Profit must be above the price"
            else -> null
        }
        Side.SELL -> when {
            stopLoss != null && stopLoss <= price -> "Stop Loss must be above the price"
            takeProfit != null && takeProfit >= price -> "Take Profit must be below the price"
            else -> null
        }
    }

    private fun validateLots(lots: Double): String? =
        if (lots < MIN_LOT || lots > MAX_LOT) "Volume must be $MIN_LOT – $MAX_LOT lots" else null

    private fun closePrice(position: Position, quote: Quote) = closePrice(position.side, quote)
    private fun closePrice(side: Side, quote: Quote) = if (side == Side.BUY) quote.bid else quote.ask
    private fun openPrice(side: Side, quote: Quote) = if (side == Side.BUY) quote.ask else quote.bid

    companion object {
        const val DEFAULT_BALANCE = 10_000.0
        const val DEFAULT_LEVERAGE = 500
        const val MIN_LOT = 0.01
        const val MAX_LOT = 100.0
        const val STOP_OUT_LEVEL = 30.0
        val LEVERAGES = listOf(50, 100, 200, 500, 1000, 2000)

        const val MARKET_CLOSED = "Market is closed. Trading resumes when the market opens."

        /** USD per one unit of [ccy], from live quotes (falling back to the built-in reference prices). */
        fun usdRate(ccy: String, quotes: Map<String, Quote>): Double {
            if (ccy == "USD") return 1.0
            quotes["${ccy}USD"]?.let { return it.mid }
            quotes["USD$ccy"]?.let { return 1 / it.mid }
            Instruments.find("${ccy}USD")?.let { return it.fallbackPrice }
            Instruments.find("USD$ccy")?.let { return 1 / it.fallbackPrice }
            return 1.0
        }

        fun profitFor(inst: Instrument, side: Side, lots: Double, open: Double, close: Double, quotes: Map<String, Quote>): Double {
            val diff = if (side == Side.BUY) close - open else open - close
            return diff * inst.contractSize * lots * usdRate(inst.quoteCcy, quotes)
        }

        fun marginFor(inst: Instrument, lots: Double, price: Double, leverage: Int, quotes: Map<String, Quote>): Double {
            val notionalUsd = inst.contractSize * lots * price * usdRate(inst.quoteCcy, quotes)
            return notionalUsd / min(leverage, inst.maxLeverage)
        }

        private fun newAccount(type: AccountType, balance: Double, leverage: Int) = Account(
            number = Random.nextLong(10_000_000_000, 99_999_999_999),
            type = type,
            balance = balance,
            leverage = leverage,
            totalDeposits = balance,
            // Ticket numbers look like a broker's: 8 digits.
            nextId = Random.nextLong(40_000_000, 49_000_000),
        )

        /** Files from older versions had a single demo account; give them a Real one too. */
        /** Loaded book made usable: never empty, and nothing left on removed instruments. Real accounts are only ever opened by the user. */
        private fun AccountBook.repaired(): AccountBook {
            val fixed = if (accounts.isEmpty()) defaultBook() else this
            // Drop positions on instruments that no longer exist.
            return fixed.copy(accounts = fixed.accounts.map { a ->
                a.copy(nextId = if (a.nextId < 10_000_000) Random.nextLong(40_000_000, 49_000_000) else a.nextId, positions = a.positions.filter { Instruments.find(it.symbol) != null }, pending = a.pending.filter { Instruments.find(it.symbol) != null })
            })
        }

        /** A fresh install has only a demo account; a real one has to be opened from Accounts. */
        private fun defaultBook(): AccountBook {
            val demo = newAccount(AccountType.DEMO, DEFAULT_BALANCE, DEFAULT_LEVERAGE)
            return AccountBook(listOf(demo), demo.number)
        }

    }
}
