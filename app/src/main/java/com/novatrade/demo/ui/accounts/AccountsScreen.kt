package com.novatrade.demo.ui.accounts

import com.novatrade.demo.Brand

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.novatrade.demo.trading.Notice
import com.novatrade.demo.ui.deposit.DepositStart
import com.novatrade.demo.ui.deposit.PaymentMode
import com.novatrade.demo.ui.deposit.PaymentMethodSheet
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountMetrics
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.model.CloseReason
import com.novatrade.demo.model.ClosedTrade
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.model.PendingOrder
import com.novatrade.demo.model.Position
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Side
import com.novatrade.demo.trading.OrderResult
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.common.InfoRow
import com.novatrade.demo.ui.common.InstrumentIcon
import com.novatrade.demo.ui.common.Pill
import com.novatrade.demo.ui.common.StatCell
import com.novatrade.demo.ui.common.Stepper
import com.novatrade.demo.ui.common.UnderlineTabs
import com.novatrade.demo.ui.common.toPriceOrNull
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.dateTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.novatrade.demo.util.format
import com.novatrade.demo.util.lots
import com.novatrade.demo.util.money
import com.novatrade.demo.util.percent
import com.novatrade.demo.util.signedMoney
import com.novatrade.demo.util.usd

private enum class Sheet { DEPOSIT, PAYMENT, WITHDRAW, PAYMENT_WITHDRAW, SWITCH, CREATE, NOTIFICATIONS }

@Composable
fun AccountsScreen(
    account: Account,
    accounts: List<Account>,
    metrics: AccountMetrics,
    quotes: Map<String, Quote>,
    notifications: List<Notice>,
    engine: TradingEngine,
    onResult: (OrderResult) -> Unit,
    onTrade: () -> Unit,
    onOpenInstrument: (String) -> Unit,
    onOpenDeposit: (DepositStart, PaymentMode) -> Unit,
    onDemoDeposit: () -> Unit,
    onOpenAccount: () -> Unit,
    onAlerts: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var seenNotices by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onAlerts) {
                    Icon(Icons.Outlined.Alarm, contentDescription = "Price alerts")
                }
                IconButton(onClick = {
                    seenNotices = notifications.size
                    sheet = Sheet.NOTIFICATIONS
                }) {
                    BadgedBox(badge = { if (notifications.size > seenNotices) Badge(containerColor = AppColors.Sell) }) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                    }
                }
            }
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Accounts", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.Filled.Add, "Open new account", size = 40, iconSize = 17, onClick = onOpenAccount)
            }
            AccountCard(account, metrics, onTrade, onSwitch = { sheet = Sheet.SWITCH }, onDetails = onDetails) { action ->
                // Real accounts go through the payment-method flow; demo accounts top up directly.
                if (action == Sheet.DEPOSIT && account.type == AccountType.DEMO) {
                    onDemoDeposit()
                    return@AccountCard
                }
                sheet = when {
                    account.type != AccountType.REAL -> action
                    action == Sheet.DEPOSIT -> Sheet.PAYMENT
                    action == Sheet.WITHDRAW -> Sheet.PAYMENT_WITHDRAW
                    else -> action
                }
            }
            Spacer(Modifier.height(26.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp, modifier = Modifier.align(Alignment.BottomStart))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TradeTabs(
                        tabs = listOf("Open" to account.positions.size, "Pending" to account.pending.size, "Closed" to 0),
                        selected = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = "Sort",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { newestFirst = !newestFirst }
                            .padding(8.dp)
                            .size(22.dp),
                    )
                }
            }
        }
        when (tab) {
            0 -> openPositions(account, metrics, quotes, engine, newestFirst, onResult, onTrade, onOpenInstrument) { selectedId = it }
            1 -> pendingOrders(account.pending, quotes, engine, newestFirst, onResult, onTrade, onOpenInstrument)
            else -> closedTrades(if (newestFirst) account.history else account.history.asReversed(), onOpenInstrument)
        }
    }

    val selected = account.positions.find { it.id == selectedId }
    if (selected != null) {
        com.novatrade.demo.ui.instrument.PositionDetailsSheet(
            position = selected,
            others = account.positions,
            inst = Instruments[selected.symbol],
            quote = quotes[selected.symbol],
            engine = engine,
            onResult = onResult,
            onViewChart = {
                selectedId = null
                onOpenInstrument(selected.symbol)
            },
            onDismiss = { selectedId = null },
        )
    }
    when (sheet) {
        Sheet.DEPOSIT -> AmountSheet("Deposit", "Virtual money only. Nothing is charged.", onDismiss = { sheet = null }) { amount ->
            engine.deposit(amount)
            sheet = null
            onResult(OrderResult.Success("Deposited ${usd(amount)} (virtual)"))
        }
        Sheet.WITHDRAW -> AmountSheet("Withdraw", "Available: ${usd(metrics.freeMargin.coerceAtLeast(0.0))}", onDismiss = { sheet = null }) { amount ->
            val result = engine.withdraw(amount)
            if (result is OrderResult.Success) sheet = null
            onResult(result)
        }
        Sheet.PAYMENT -> PaymentMethodSheet(
            onCryptoWallet = {
                sheet = null
                onOpenDeposit(DepositStart.WALLET, PaymentMode.DEPOSIT)
            },
            onAllMethods = {
                sheet = null
                onOpenDeposit(DepositStart.METHODS, PaymentMode.DEPOSIT)
            },
            onDismiss = { sheet = null },
        )
        Sheet.PAYMENT_WITHDRAW -> PaymentMethodSheet(
            onCryptoWallet = {
                sheet = null
                onOpenDeposit(DepositStart.WALLET, PaymentMode.WITHDRAW)
            },
            onAllMethods = {
                sheet = null
                onOpenDeposit(DepositStart.METHODS, PaymentMode.WITHDRAW)
            },
            onDismiss = { sheet = null },
        )
        Sheet.SWITCH -> AccountSwitcherSheet(
            accounts = accounts,
            activeNumber = account.number,
            quotes = quotes,
            engine = engine,
            onSelect = {
                engine.switchTo(it)
                sheet = null
            },
            onCreate = {
                sheet = null
                onOpenAccount()
            },
            onDismiss = { sheet = null },
        )
        Sheet.CREATE -> CreateAccountSheet(
            onCreate = { type, leverage ->
                engine.createAccount(type, leverage)
                sheet = null
                onResult(OrderResult.Success("New ${type.label.lowercase()} account opened"))
            },
            onDismiss = { sheet = null },
        )
        Sheet.NOTIFICATIONS -> NotificationsSheet(notifications) { sheet = null }
        null -> Unit
    }
}

@Composable
private fun AccountCard(
    account: Account,
    metrics: AccountMetrics,
    onTrade: () -> Unit,
    onSwitch: () -> Unit,
    onDetails: () -> Unit,
    onAction: (Sheet) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onSwitch)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(account.title)
                    withStyle(SpanStyle(color = AppColors.TextSecondary)) { append(" # ${account.number}") }
                },
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(Icons.Filled.ChevronRight, "Switch account", size = 38, onClick = onSwitch)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AccountChip(Brand.name, account.type)
            AccountChip(account.kind, account.type)
            AccountChip(account.type.label, account.type)
        }
        Spacer(Modifier.height(6.dp))
        Text(com.novatrade.demo.ui.common.balanceText(metrics.equity), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            ActionButton("Trade", Icons.Outlined.CandlestickChart, primary = true, onClick = onTrade)
            ActionButton("Deposit", com.novatrade.demo.ui.common.DepositCircle) { onAction(Sheet.DEPOSIT) }
            ActionButton("Withdraw", com.novatrade.demo.ui.common.WithdrawCircle) { onAction(Sheet.WITHDRAW) }
            ActionButton("Details", Icons.Filled.MoreVert, onClick = onDetails)
        }
    }
}

@Composable
fun AccountChip(text: String, type: AccountType) {
    val demo = type == AccountType.DEMO
    Text(
        text,
        color = if (demo) AppColors.DemoChipText else AppColors.RealChipText,
        fontSize = 12.sp,
        // Tight line height: the default 24sp made the chips twice as tall as the broker's.
        lineHeight = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (demo) AppColors.DemoChipBg else AppColors.RealChipBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun CircleIconButton(icon: ImageVector, description: String, size: Int, iconSize: Int = 24, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(AppColors.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = AppColors.TextPrimary, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, primary: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (primary) AppColors.Accent else AppColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (primary) AppColors.OnAccent else AppColors.TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp)
    }
}

private fun LazyListScope.openPositions(
    account: Account,
    metrics: AccountMetrics,
    quotes: Map<String, Quote>,
    engine: TradingEngine,
    newestFirst: Boolean,
    onResult: (OrderResult) -> Unit,
    onTrade: () -> Unit,
    onOpenInstrument: (String) -> Unit,
    onSelect: (Long) -> Unit,
) {
    if (account.positions.isEmpty()) {
        item { EmptyState("No open positions", onTrade, onOpenInstrument) }
        return
    }
    item {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Total P/L", color = AppColors.TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(signedMoney(metrics.floatingProfit) + " USD", color = AppColors.pnl(metrics.floatingProfit), fontSize = 15.sp)
        }
    }
    val positions = if (newestFirst) account.positions.asReversed() else account.positions
    items(positions, key = { "p${it.id}" }) { p ->
        val quote = quotes[p.symbol]
        val profit = engine.profitOf(p, quote)
        OpenTradeCard(
            symbol = p.symbol,
            sideLots = "${p.side.label} ${lots(p.lots)} lot",
            at = " at ${Instruments[p.symbol].format(p.openPrice)}",
            side = p.side,
            value = signedMoney(profit) + " USD",
            valueColor = AppColors.pnl(profit),
            current = quote?.let { Instruments[p.symbol].format(if (p.side == Side.BUY) it.bid else it.ask) },
            onClick = { onSelect(p.id) },
        )
    }
}

private fun LazyListScope.pendingOrders(
    orders: List<PendingOrder>,
    quotes: Map<String, Quote>,
    engine: TradingEngine,
    newestFirst: Boolean,
    onResult: (OrderResult) -> Unit,
    onTrade: () -> Unit,
    onOpenInstrument: (String) -> Unit,
) {
    if (orders.isEmpty()) {
        item { EmptyState("No pending orders", onTrade, onOpenInstrument) }
        return
    }
    items(if (newestFirst) orders.asReversed() else orders, key = { "o${it.id}" }) { o ->
        val inst = Instruments[o.symbol]
        TradeRow(
            symbol = o.symbol,
            detail = "${o.label} ${lots(o.lots)} lots at ${inst.format(o.price)}",
            side = o.side,
            value = quotes[o.symbol]?.let { inst.format(if (o.side == Side.BUY) it.ask else it.bid) } ?: "—",
            valueColor = AppColors.TextPrimary,
            sub = "Current price",
            onClick = {},
            onClose = { onResult(engine.cancelPending(o.id)) },
        )
    }
}

/** Closed trades grouped by day, each day a card with its total, like the broker's history. */
private fun LazyListScope.closedTrades(trades: List<ClosedTrade>, onOpen: (String) -> Unit) {
    if (trades.isEmpty()) {
        item { EmptyState("No closed positions", null, null) }
        return
    }
    val zone = ZoneId.systemDefault()
    // groupBy keeps the incoming order, so days follow the chosen sort.
    trades.groupBy { Instant.ofEpochMilli(it.closeTime).atZone(zone).toLocalDate() }.forEach { (day, list) ->
        item(key = "day$day") { ClosedDay(day, list, onOpen) }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("dd MMMM", Locale.ENGLISH)

/** "Today, 08 November", "Yesterday, 07 November", "2 days ago, 06 November", then the plain date. */
private fun dayLabel(day: LocalDate): String {
    val ago = ChronoUnit.DAYS.between(day, LocalDate.now())
    return when (ago) {
        0L -> "Today, " + day.format(dayFormat)
        1L -> "Yesterday, " + day.format(dayFormat)
        in 2..6 -> "$ago days ago, " + day.format(dayFormat)
        else -> day.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH))
    }
}

@Composable
private fun ClosedDay(day: LocalDate, trades: List<ClosedTrade>, onOpen: (String) -> Unit) {
    val total = trades.sumOf { it.profit }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(dayLabel(day), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
            Text("${signedMoney(total)} USD", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.pnl(total))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Surface)) {
            trades.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = RowDivider, thickness = 0.5.dp)
                ClosedRow(t) { onOpen(t.symbol) }
            }
        }
    }
}

private val RowDivider = Color(0xFF3A4348)
private val PillBg = Color(0xFF232E34)
private val LossPillBg = Color(0xFF362327)

/** TP / SL / SO tag; [hit] marks the level that closed the trade. */
@Composable
private fun TradeTag(text: String, hit: Boolean, profit: Boolean = false) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (!hit) PillBg else if (profit) AppColors.DemoChipBg else LossPillBg)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontSize = 12.sp, lineHeight = 12.sp,
            color = if (!hit) AppColors.TextPrimary else if (profit) AppColors.Profit else AppColors.Sell,
        )
    }
}

@Composable
private fun ClosedRow(t: ClosedTrade, onClick: () -> Unit) {
    val inst = Instruments[t.symbol]
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InstrumentIcon(inst, size = 24)
            Spacer(Modifier.width(11.dp))
            Text(inst.display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.width(2.dp))
            if (t.takeProfit != null) TradeTag("TP", hit = t.reason == CloseReason.TAKE_PROFIT, profit = true)
            if (t.stopLoss != null) TradeTag("SL", hit = t.reason == CloseReason.STOP_LOSS)
            Spacer(Modifier.weight(1f))
            Text("${signedMoney(t.profit)} USD", color = AppColors.pnl(t.profit), fontSize = 15.sp)
        }
        Row(Modifier.padding(start = 35.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (t.side == Side.BUY) AppColors.Buy else AppColors.Sell)) { append("${t.side.label} ${lots(t.lots)} lot") }
                    append(" at ${inst.format(t.openPrice)}")
                },
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(inst.format(t.closePrice), color = AppColors.TextSecondary, fontSize = 13.sp)
            if (t.reason == CloseReason.STOP_OUT) TradeTag("SO", hit = true)
        }
    }
}

/** Open position as a rounded card, like the account card; tapping it opens the position sheet. */
@Composable
private fun OpenTradeCard(
    symbol: String,
    sideLots: String,
    at: String,
    side: Side,
    value: String,
    valueColor: Color,
    current: String?,
    onClick: () -> Unit,
) {
    val inst = Instruments[symbol]
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Icon sits on the name line, and the second line starts under the name.
        Row(verticalAlignment = Alignment.CenterVertically) {
            InstrumentIcon(inst, size = 24)
            Spacer(Modifier.width(11.dp))
            Text(inst.display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(value, color = valueColor, fontSize = 15.sp)
        }
        Row(Modifier.padding(start = 35.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (side == Side.BUY) AppColors.Buy else AppColors.Sell)) { append(sideLots) }
                    append(at)
                },
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            current?.let { Text(it, color = AppColors.TextSecondary, fontSize = 13.sp) }
        }
    }
}

/** Open / Pending / Closed tabs: white underline across the whole tab, count badge after the title. */
@Composable
private fun TradeTabs(tabs: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier) {
        tabs.forEachIndexed { i, (title, count) ->
            val active = i == selected
            Column(Modifier.width(IntrinsicSize.Max).clickable { onSelect(i) }) {
                Row(Modifier.height(42.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 14.sp, color = if (active) AppColors.TextPrimary else AppColors.TextSecondary)
                    if (count > 0) {
                        Spacer(Modifier.width(5.dp))
                        Box(Modifier.size(16.dp).clip(CircleShape).background(AppColors.Toggle), contentAlignment = Alignment.Center) {
                            Text("$count", fontSize = 10.sp, lineHeight = 10.sp, color = AppColors.TextPrimary)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) AppColors.TextPrimary else Color.Transparent))
            }
        }
    }
}

@Composable
private fun TradeRow(
    symbol: String,
    detail: String,
    side: Side,
    value: String,
    valueColor: Color,
    sub: String?,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = if (onClose != null) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentIcon(Instruments[symbol], size = 32)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(symbol, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(detail, color = if (side == Side.BUY) AppColors.Buy else AppColors.Sell, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            sub?.let { Text(it, color = AppColors.TextSecondary, fontSize = 11.sp) }
        }
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
    HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 60.dp))
}

@Composable
private fun EmptyState(title: String, onExplore: (() -> Unit)?, onOpenInstrument: ((String) -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp))
        if (onOpenInstrument != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Surface)
                    .clickable { onOpenInstrument("BTCUSD") }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentIcon(Instruments["BTCUSD"], size = 26)
                Spacer(Modifier.width(10.dp))
                Text("BTC - Trade", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (onExplore != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onExplore)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ManageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Explore more instruments", fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AmountSheet(title: String, subtitle: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amountText by remember { mutableStateOf("1000") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = AppColors.TextSecondary, fontSize = 13.sp)
            Stepper(amountText, { amountText = it }, 100.0, 0, 1000.0, "Amount", min = 0.0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500, 1000, 5000, 10000).forEach { v ->
                    OutlinedButton(
                        onClick = { amountText = v.toString() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (v >= 1000) "${v / 1000}K" else "$v", color = AppColors.TextPrimary, fontSize = 13.sp) }
                }
            }
            Button(
                onClick = { amountText.toPriceOrNull()?.takeIf { it > 0 }?.let(onConfirm) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
            ) { Text("$title ${amountText.toPriceOrNull()?.let(::usd) ?: ""}", fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PositionSheet(
    position: Position,
    quote: Quote?,
    profit: Double,
    onDismiss: () -> Unit,
    onModify: (Double?, Double?) -> Unit,
    onClose: () -> Unit,
    onOpenChart: () -> Unit,
) {
    val inst = Instruments[position.symbol]
    val current = quote?.let { if (position.side == Side.BUY) it.bid else it.ask }
    var slText by remember(position.id) { mutableStateOf(position.stopLoss?.let(inst::format).orEmpty()) }
    var tpText by remember(position.id) { mutableStateOf(position.takeProfit?.let(inst::format).orEmpty()) }
    val step = inst.priceStep(position.openPrice)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentIcon(inst)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(position.symbol, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${position.side.label} ${lots(position.lots)} lots · #${position.id}",
                        color = if (position.side == Side.BUY) AppColors.Buy else AppColors.Sell,
                        fontSize = 12.sp,
                    )
                }
                Text(signedMoney(profit) + " USD", color = AppColors.pnl(profit), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Row {
                StatCell("Open price", inst.format(position.openPrice), Modifier.weight(1f))
                StatCell("Current price", current?.let(inst::format) ?: "—", Modifier.weight(1f))
                StatCell("Margin", money(position.margin), Modifier.weight(1f))
            }
            Text("Opened ${dateTime(position.openTime)}", color = AppColors.TextSecondary, fontSize = 12.sp)
            Text("Take Profit", color = AppColors.TextSecondary, fontSize = 13.sp)
            Stepper(tpText, { tpText = it }, step, inst.digits, current, "Not set")
            Text("Stop Loss", color = AppColors.TextSecondary, fontSize = 13.sp)
            Stepper(slText, { slText = it }, step, inst.digits, current, "Not set")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenChart, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("Chart", color = AppColors.TextPrimary)
                }
                Button(
                    onClick = { onModify(slText.toPriceOrNull(), tpText.toPriceOrNull()) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceHigh, contentColor = AppColors.TextPrimary),
                ) { Text("Modify") }
            }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
            ) { Text("Close position  ${signedMoney(profit)}", fontWeight = FontWeight.Bold) }
        }
    }
}
