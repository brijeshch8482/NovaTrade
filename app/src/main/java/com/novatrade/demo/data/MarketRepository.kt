package com.novatrade.demo.data

import android.content.SharedPreferences
import com.novatrade.demo.model.Candle
import com.novatrade.demo.model.Hours
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.model.PriceSource
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.WebSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Produces bid/ask quotes and candle series for every instrument.
 *
 * Crypto and gold stream live from Binance. Everything else is polled from Yahoo Finance; while a
 * market is open, small simulated ticks pull towards the latest polled price, and while it is closed
 * the price stays put, like on a broker platform.
 *
 * All state except [liveMids] is touched only from [scope], which must be single-threaded
 * (the main dispatcher), so no locking is needed. [lastPrices] keeps the latest mid of every
 * instrument so a restart without network starts from the last real price.
 */
class MarketRepository(private val scope: CoroutineScope, private val lastPrices: SharedPreferences) {
    private val api = MarketApi()

    /** Binance symbol -> latest mid. Written from OkHttp threads. */
    private val liveMids = ConcurrentHashMap<String, Double>()
    @Volatile private var lastSocketMessage = 0L
    private var socket: WebSocket? = null

    /** Yahoo ticker -> latest polled quote. */
    private val anchors = HashMap<String, YahooQuote>()
    private val simMids = HashMap<String, Double>()
    private val jitter = HashMap<String, Double>()
    private val dayOpens = HashMap<String, Double>()
    /** Binance-backed instruments that had no data and were switched to simulation. */
    private val fallenBack = HashSet<String>()

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    private val series = HashMap<Pair<String, Timeframe>, MutableStateFlow<List<Candle>?>>()
    private val historyLimit = Semaphore(4)

    private val binanceInstruments = Instruments.all.filter { it.source is PriceSource.Binance }
    private val binanceSymbols = binanceInstruments.map { (it.source as PriceSource.Binance).symbol }
    private val yahooInstruments = Instruments.all.filter { it.source is PriceSource.Yahoo }
    private val yahooTickers = yahooInstruments.map { (it.source as PriceSource.Yahoo).ticker }.distinct()

    fun start() {
        for (inst in yahooInstruments) simMids[inst.symbol] = lastKnown(inst)
        scope.launch { runCatching { applyDayOpens(api.dayOpens(binanceSymbols)) } }
        connectSocket()
        scope.launch { restFallbackLoop() }
        scope.launch { yahooLoop() }
        scope.launch {
            delay(NO_DATA_TIMEOUT_MS)
            for (inst in binanceInstruments) {
                if (binanceMid(inst) == null) {
                    fallenBack += inst.symbol
                    simMids[inst.symbol] = lastKnown(inst)
                    dayOpens.getOrPut(inst.symbol) { lastKnown(inst) }
                }
            }
        }
        scope.launch {
            while (true) {
                tick()
                delay(TICK_MS)
            }
        }
        scope.launch {
            while (true) {
                delay(10_000)
                val editor = lastPrices.edit()
                _quotes.value.forEach { (symbol, q) -> editor.putString(symbol, q.mid.toString()) }
                editor.apply()
            }
        }
    }

    private fun lastKnown(inst: Instrument): Double =
        lastPrices.getString(inst.symbol, null)?.toDoubleOrNull() ?: inst.fallbackPrice

    /** Candle series for a symbol/timeframe; null until history has loaded. */
    fun candles(symbol: String, tf: Timeframe): StateFlow<List<Candle>?> =
        series.getOrPut(symbol to tf) {
            MutableStateFlow<List<Candle>?>(null).also { flow ->
                scope.launch {
                    val history = historyLimit.withPermit { loadHistory(Instruments[symbol], tf) }
                    // Fold in the current price so the last candle meets the price line.
                    val bid = quotes.value[symbol]?.bid
                    val last = history.lastOrNull()
                    flow.value = if (bid == null || last == null) history
                    else history.dropLast(1) + last.copy(high = maxOf(last.high, bid), low = minOf(last.low, bid), close = bid)
                }
            }
        }

    private suspend fun loadHistory(inst: Instrument, tf: Timeframe): List<Candle> {
        val bidFactor = 1 - inst.spreadFraction / 2
        fun List<Candle>.toBid() = map {
            it.copy(open = it.open * bidFactor, high = it.high * bidFactor, low = it.low * bidFactor, close = it.close * bidFactor)
        }
        when (val source = inst.source) {
            is PriceSource.Binance -> if (inst.symbol !in fallenBack) {
                val list = runCatching { api.klines(source.symbol, tf, HISTORY_SIZE) }.getOrNull()
                // Thin markets produce many flat candles; a simulated history looks far more realistic.
                val flatShare = list?.count { it.high == it.low }?.toDouble()?.div(list.size.coerceAtLeast(1)) ?: 1.0
                if (list != null && list.isNotEmpty() && flatShare < 0.25) return list.toBid()
            }
            is PriceSource.Yahoo -> {
                val list = runCatching { api.yahooCandles(source.ticker, tf, HISTORY_SIZE) }.getOrNull()
                if (!list.isNullOrEmpty()) return list.toBid()
            }
        }
        val quote = withTimeoutOrNull(NO_DATA_TIMEOUT_MS + 2_000) {
            quotes.first { it[inst.symbol] != null }[inst.symbol]
        }
        return PriceSimulator.history(
            endPrice = quote?.bid ?: lastKnown(inst),
            tf = tf,
            count = HISTORY_SIZE,
            tickVolatility = inst.tickVolatility,
            now = System.currentTimeMillis(),
        )
    }

    /** Polls Yahoo for every non-crypto instrument; faster while any of those markets is open. */
    private suspend fun yahooLoop() {
        while (true) {
            runCatching { api.yahooQuotes(yahooTickers) }.getOrNull()?.let { fresh ->
                anchors.putAll(fresh)
                for (inst in yahooInstruments) {
                    val q = anchors[(inst.source as PriceSource.Yahoo).ticker] ?: continue
                    dayOpens[inst.symbol] = q.previousClose * (1 - inst.spreadFraction / 2)
                    // While closed the price is exactly the last traded one.
                    if (!inst.hours.isOpen()) simMids[inst.symbol] = q.price
                }
            }
            val anyOpen = Hours.FX.isOpen() || Hours.US_STOCKS.isOpen()
            delay(if (anyOpen) 15_000 else 120_000)
        }
    }

    private fun applyDayOpens(opens: Map<String, Double>) {
        for (inst in binanceInstruments) {
            val open = opens[(inst.source as PriceSource.Binance).symbol] ?: continue
            dayOpens[inst.symbol] = open * (1 - inst.spreadFraction / 2)
        }
    }

    private fun connectSocket() {
        socket = api.bookTickerSocket(
            binanceSymbols,
            onMid = { sym, mid ->
                liveMids[sym] = mid
                lastSocketMessage = System.currentTimeMillis()
            },
            onClosed = {
                socket = null
                scope.launch {
                    delay(RECONNECT_MS)
                    if (socket == null) connectSocket()
                }
            },
        )
    }

    /** Polls REST prices whenever the WebSocket has gone quiet (some networks block sockets). */
    private suspend fun restFallbackLoop() {
        while (true) {
            delay(1_000)
            if (System.currentTimeMillis() - lastSocketMessage > 5_000) {
                runCatching { api.bookMids(binanceSymbols) }.getOrNull()?.let { liveMids.putAll(it) }
            }
        }
    }

    private fun binanceMid(inst: Instrument): Double? =
        liveMids[(inst.source as PriceSource.Binance).symbol]

    private fun tick() {
        val now = System.currentTimeMillis()
        val openNow = Hours.entries.associateWith { it.isOpen(now) }
        val previous = _quotes.value
        val next = HashMap<String, Quote>(previous)
        for (inst in Instruments.all) {
            val open = openNow.getValue(inst.hours)
            var live = true
            val mid = when (val source = inst.source) {
                is PriceSource.Yahoo -> {
                    val anchor = anchors[source.ticker]
                    live = anchor != null
                    val current = simMids[inst.symbol] ?: continue
                    if (open) {
                        // Random walk that keeps returning to the latest polled price.
                        val target = anchor?.price ?: current
                        val pulled = current + (target - current) * 0.08
                        PriceSimulator.step(pulled, inst.tickVolatility).also { simMids[inst.symbol] = it }
                    } else {
                        anchor?.price ?: current
                    }
                }
                is PriceSource.Binance -> {
                    if (inst.symbol in fallenBack && binanceMid(inst) != null) fallenBack -= inst.symbol
                    if (inst.symbol in fallenBack) {
                        live = false
                        val stepped = PriceSimulator.step(simMids[inst.symbol] ?: continue, inst.tickVolatility)
                        simMids[inst.symbol] = stepped
                        stepped
                    } else {
                        val price = binanceMid(inst) ?: continue
                        if (inst.liveJitter > 0) {
                            var offset = jitter[inst.symbol] ?: 0.0
                            if (Math.random() < PriceSimulator.MOVE_PROBABILITY) {
                                offset = offset * 0.9 + PriceSimulator.gaussian() * inst.liveJitter * price
                                jitter[inst.symbol] = offset
                            }
                            price + offset
                        } else {
                            price
                        }
                    }
                }
            }
            val half = mid * inst.spreadFraction / 2
            val bid = mid - half
            val old = previous[inst.symbol]
            val direction = when {
                old == null -> 0
                abs(bid - old.bid) < 1e-12 -> old.direction
                bid > old.bid -> 1
                else -> -1
            }
            next[inst.symbol] = Quote(
                bid = bid,
                ask = mid + half,
                dayOpen = dayOpens[inst.symbol] ?: bid,
                time = now,
                live = live,
                direction = direction,
                open = open,
            )
            if (open && (old == null || old.bid != bid)) updateCandles(inst.symbol, bid, now)
        }
        _quotes.value = next
    }

    private fun updateCandles(symbol: String, price: Double, now: Long) {
        for ((key, flow) in series) {
            if (key.first != symbol) continue
            val list = flow.value ?: continue
            val tf = key.second
            val last = list.lastOrNull()
            flow.value = if (last != null && now < tf.next(last.time)) {
                list.dropLast(1) + last.copy(
                    high = maxOf(last.high, price),
                    low = minOf(last.low, price),
                    close = price,
                )
            } else {
                val open = last?.close ?: price
                (list + Candle(tf.bucket(now), open, maxOf(open, price), minOf(open, price), price))
                    .takeLast(MAX_CANDLES)
            }
        }
    }

    private companion object {
        const val TICK_MS = 250L
        const val NO_DATA_TIMEOUT_MS = 10_000L
        const val RECONNECT_MS = 3_000L
        const val HISTORY_SIZE = 300
        const val MAX_CANDLES = 800
    }
}
