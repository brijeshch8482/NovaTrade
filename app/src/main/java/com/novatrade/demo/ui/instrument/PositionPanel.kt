package com.novatrade.demo.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountMetrics
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.model.Position
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Side
import com.novatrade.demo.trading.OrderResult
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.common.InstrumentIcon
import com.novatrade.demo.ui.common.Stepper
import com.novatrade.demo.ui.common.toPriceOrNull
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.dateTime
import com.novatrade.demo.util.format
import com.novatrade.demo.util.lots
import com.novatrade.demo.util.percent
import com.novatrade.demo.util.signedMoney
import com.novatrade.demo.util.usd

/** Bottom panel for one open position, shown when its line on the chart is tapped. */
@Composable
fun PositionPanel(
    positions: List<Position>,
    index: Int,
    inst: Instrument,
    quote: Quote?,
    engine: TradingEngine,
    editing: Boolean,
    errors: List<String>,
    /** Levels to show beside the name: the draft while editing, else the saved ones. */
    tp: Double?,
    sl: Double?,
    onIndex: (Int) -> Unit,
    onDetails: () -> Unit,
    onClose: () -> Unit,
    onCancelEdit: () -> Unit,
    onConfirmEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showErrors by remember(editing) { mutableStateOf(false) }
    val p = positions.getOrNull(index) ?: return
    val profit = engine.profitOf(p, quote)
    val current = quote?.let { if (p.side == Side.BUY) it.bid else it.ask }
    Column(Modifier.fillMaxWidth().background(AppColors.SheetBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF4A535C)))
        }
        val many = positions.size > 1
        Row(Modifier.height(if (many) 48.dp else 30.dp), verticalAlignment = Alignment.CenterVertically) {
            // Paging arrows and the "1/2" counter only appear with more than one position.
            if (many) {
                IconButton(onClick = { if (index > 0) onIndex(index - 1) }, enabled = index > 0) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous", tint = if (index > 0) AppColors.TextPrimary else AppColors.TextSecondary)
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("#${p.id}", fontSize = 15.sp)
                if (many) Text("${index + 1}/${positions.size}", fontSize = 11.sp, color = AppColors.TextSecondary)
            }
            if (many) {
                IconButton(onClick = { if (index < positions.lastIndex) onIndex(index + 1) }, enabled = index < positions.lastIndex) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next", tint = if (index < positions.lastIndex) AppColors.TextPrimary else AppColors.TextSecondary)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            InstrumentIcon(inst, size = 24)
            Spacer(Modifier.width(8.dp))
            Text(inst.display, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (tp != null) LevelPill("TP")
            if (sl != null) LevelPill("SL")
            Spacer(Modifier.weight(1f))
            Text("${signedMoney(profit)} USD", fontSize = 15.sp, color = AppColors.pnl(profit))
        }
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text(
                "${p.side.label} ${lots(p.lots)} lot",
                fontSize = 13.sp,
                color = if (p.side == Side.BUY) AppColors.Buy else AppColors.Sell,
            )
            Text(" at ${inst.format(p.openPrice)}", fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
            Text(current?.let(inst::format) ?: "—", fontSize = 13.sp, color = AppColors.TextSecondary)
        }
        if (editing && errors.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    "View errors " + if (showErrors) "⌃" else "⌄",
                    fontSize = 12.sp,
                    color = AppColors.Sell,
                    modifier = Modifier.clickable { showErrors = !showErrors },
                )
            }
            if (showErrors) errors.forEach { Text(it, fontSize = 12.sp, color = AppColors.Sell, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
        Row(Modifier.padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                PanelButton("Cancel", Modifier.weight(1f), onClick = onCancelEdit)
                val ok = errors.isEmpty()
                Row(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (ok) AppColors.Accent else Color.Transparent)
                        .border(1.dp, if (ok) AppColors.Accent else AppColors.Divider, RoundedCornerShape(6.dp))
                        .clickable(enabled = ok, onClick = onConfirmEdit),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = if (ok) AppColors.OnAccent else AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirm", fontSize = 14.sp, color = if (ok) AppColors.OnAccent else AppColors.TextSecondary)
                }
            } else {
                PanelButton("Details", Modifier.weight(1f), onClick = onDetails)
                PanelButton("Close order", Modifier.weight(1f), icon = true, onClick = onClose)
            }
        }
    }
}

/** Small "TP" / "SL" marker next to the instrument name: white text on a box a shade lighter than the panel. */
@Composable
private fun LevelPill(text: String) {
    // Fixed height: the default text style's 24sp line height made the box taller than the icon.
    Box(
        Modifier
            .padding(start = 4.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF232E34))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, lineHeight = 12.sp, color = AppColors.TextPrimary)
    }
}

@Composable
private fun PanelButton(text: String, modifier: Modifier, icon: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.SurfaceHigh)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 14.sp)
    }
}

/** Details of a position with Modify / Partial close / Close by tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionDetailsSheet(
    position: Position,
    others: List<Position>,
    inst: Instrument,
    quote: Quote?,
    engine: TradingEngine,
    onResult: (OrderResult) -> Unit,
    /** When set (opened from the trade list), Modify shows View chart / Close order until a level changes. */
    onViewChart: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var confirmClose by remember { mutableStateOf(false) }
    val current = quote?.let { if (position.side == Side.BUY) it.bid else it.ask }
    val profit = engine.profitOf(position, quote)
    if (confirmClose) {
        ClosePositionSheet(position, inst, current, profit, onDismiss = { confirmClose = false }) {
            confirmClose = false
            onResult(engine.close(position.id))
            onDismiss()
        }
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("#${position.id}", fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InstrumentIcon(inst, size = 24)
                    Spacer(Modifier.width(8.dp))
                    Text(inst.display, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${signedMoney(profit)} USD", fontSize = 15.sp, color = AppColors.pnl(profit))
                }
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${position.side.label} ${lots(position.lots)} lot", fontSize = 13.sp, color = if (position.side == Side.BUY) AppColors.Buy else AppColors.Sell)
                    Text(" at ${inst.format(position.openPrice)}", fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
                    Text(current?.let(inst::format) ?: "—", fontSize = 13.sp, color = AppColors.TextSecondary)
                }
            }
            Row(Modifier.padding(top = 8.dp)) {
                listOf("Modify", "Partial close", "Close by").forEachIndexed { i, t ->
                    Column(Modifier.weight(1f).clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t, fontSize = 14.sp, color = if (i == tab) AppColors.TextPrimary else AppColors.TextSecondary, modifier = Modifier.padding(vertical = 10.dp))
                        Box(Modifier.fillMaxWidth().height(2.dp).background(if (i == tab) AppColors.TextPrimary else Color.Transparent))
                    }
                }
            }
            HorizontalDivider(color = AppColors.Divider)
            when (tab) {
                0 -> ModifyTab(position, inst, current, engine, onResult, onDismiss, onViewChart, onCloseOrder = { confirmClose = true })
                1 -> PartialTab(position, inst, current, engine, onResult, onDismiss)
                else -> CloseByTab(position, others, inst, engine, onResult, onDismiss)
            }
        }
    }
}

/** "Close position #id ?" confirmation with lots, closing price and the result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClosePositionSheet(position: Position, inst: Instrument, current: Double?, profit: Double, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Close position #${position.id} ?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            CloseRow("Lots", lots(position.lots))
            CloseRow("Closing price", current?.let(inst::format) ?: "—")
            CloseRow(if (profit < 0) "Loss" else "Profit", "${signedMoney(profit)} USD", AppColors.pnl(profit))
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(6.dp)).background(AppColors.Accent).clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) { Text("Confirm", fontSize = 15.sp, color = AppColors.OnAccent) }
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(6.dp)).background(AppColors.SurfaceHigh).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", fontSize = 15.sp) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CloseRow(label: String, value: String, color: Color = AppColors.TextPrimary) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = color)
    }
}

@Composable
private fun ModifyTab(
    position: Position,
    inst: Instrument,
    current: Double?,
    engine: TradingEngine,
    onResult: (OrderResult) -> Unit,
    onDismiss: () -> Unit,
    onViewChart: (() -> Unit)?,
    onCloseOrder: () -> Unit,
) {
    var tp by remember { mutableStateOf(position.takeProfit?.let(inst::format).orEmpty()) }
    var sl by remember { mutableStateOf(position.stopLoss?.let(inst::format).orEmpty()) }
    val step = inst.priceStep(position.openPrice)
    val buy = position.side == Side.BUY
    val changed = tp != position.takeProfit?.let(inst::format).orEmpty() || sl != position.stopLoss?.let(inst::format).orEmpty()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Time:", fontSize = 13.sp, color = AppColors.TextSecondary)
                Text(dateTime(position.openTime), fontSize = 14.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("Swap:", fontSize = 13.sp, color = AppColors.TextSecondary)
                Text("0.00 USD", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        LevelRow("Take Profit", tp, { tp = it }, step, inst, current, hint = current?.let { (if (buy) "Min " else "Max ") + inst.format(it) })
        LevelRow("Stop Loss", sl, { sl = it }, step, inst, current, hint = current?.let { (if (buy) "Max " else "Min ") + inst.format(it) })
        Spacer(Modifier.height(if (onViewChart != null) 56.dp else 10.dp))
        if (onViewChart != null && !changed) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PanelButton("View chart", Modifier.weight(1f), onClick = onViewChart)
                PanelButton("Close order", Modifier.weight(1f), icon = true, onClick = onCloseOrder)
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(6.dp)).background(AppColors.SurfaceHigh).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Text("Cancel", fontSize = 14.sp)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (changed) AppColors.Accent else Color.Transparent)
                    .border(1.dp, if (changed) AppColors.Accent else AppColors.Divider, RoundedCornerShape(6.dp))
                    .clickable(enabled = changed) {
                        val r = engine.modify(position.id, sl.toPriceOrNull(), tp.toPriceOrNull())
                        onResult(r)
                        if (r is OrderResult.Success) onDismiss()
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = if (changed) AppColors.OnAccent else AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Confirm", fontSize = 14.sp, color = if (changed) AppColors.OnAccent else AppColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun LevelRow(label: String, value: String, onValue: (String) -> Unit, step: Double, inst: Instrument, seed: Double?, hint: String?) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("Clear", fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.clickable(enabled = value.isNotEmpty()) { onValue("") }.padding(4.dp))
    }
    Row(Modifier.fillMaxWidth().border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp)), verticalAlignment = Alignment.CenterVertically) {
        Stepper(value, onValue, step, inst.digits, seed, "Not set", modifier = Modifier.weight(1.6f))
        Box(Modifier.width(1.dp).height(40.dp).background(AppColors.Divider))
        Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Price", fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
    if (value.isNotEmpty() && hint != null) Text(hint, fontSize = 11.sp, color = AppColors.Sell)
}

@Composable
private fun PartialTab(position: Position, inst: Instrument, current: Double?, engine: TradingEngine, onResult: (OrderResult) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(lots(position.lots / 2).let { if ((it.toDoubleOrNull() ?: 0.0) < TradingEngine.MIN_LOT) lots(position.lots) else it }) }
    val v = text.toPriceOrNull()
    val dir = if (position.side == Side.BUY) 1 else -1
    val pnl = if (v != null && current != null) (current - position.openPrice) * dir * engine.usdPerPoint(inst, v) else null
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Volume to close (of ${lots(position.lots)} lots)", fontSize = 13.sp, color = AppColors.TextSecondary)
        Stepper(text, { text = it }, 0.01, 2, position.lots, "0.01", modifier = Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp)), min = TradingEngine.MIN_LOT)
        Text("Estimated result: ${pnl?.let { "${signedMoney(it)} USD" } ?: "—"}", fontSize = 13.sp, color = pnl?.let { AppColors.pnl(it) } ?: AppColors.TextSecondary)
        val ok = v != null && v >= TradingEngine.MIN_LOT && v <= position.lots
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(6.dp)).background(if (ok) AppColors.Accent else AppColors.SurfaceHigh)
                .clickable(enabled = ok) {
                    val r = engine.partialClose(position.id, v!!)
                    onResult(r)
                    if (r is OrderResult.Success) onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) { Text("Close ${v?.let(::lots) ?: "—"} lots", color = if (ok) AppColors.OnAccent else AppColors.TextSecondary, fontSize = 14.sp) }
    }
}

@Composable
private fun CloseByTab(position: Position, others: List<Position>, inst: Instrument, engine: TradingEngine, onResult: (OrderResult) -> Unit, onDismiss: () -> Unit) {
    val opposite = others.filter { it.symbol == position.symbol && it.side != position.side }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (opposite.isEmpty()) {
            Text("No opposite ${inst.display} positions to close this one by.", fontSize = 13.sp, color = AppColors.TextSecondary)
            Text("Close by settles a buy and a sell on the same instrument against each other and saves one spread.", fontSize = 12.sp, color = AppColors.TextSecondary)
        }
        opposite.forEach { o ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(AppColors.SurfaceHigh)
                    .clickable {
                        val r = engine.closeBy(position.id, o.id)
                        onResult(r)
                        if (r is OrderResult.Success) onDismiss()
                    }
                    .padding(12.dp),
            ) {
                Text("#${o.id}  ${o.side.label} ${lots(o.lots)} at ${inst.format(o.openPrice)}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Close by", fontSize = 13.sp, color = AppColors.Accent)
            }
        }
    }
}

/** Account menu from the chart's ⋮ button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountMenuSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            listOf("Deposit", "Transfer", "Withdrawal", "Account funds", "Account settings", "Switch account").forEach { item ->
                Text(item, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(item) }.padding(horizontal = 16.dp, vertical = 14.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFundsSheet(account: Account, metrics: AccountMetrics, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Account funds", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Fund("Account", "${account.title} #${account.number} (${account.type.label})")
            Fund("Balance", usd(metrics.balance))
            Fund("Equity", usd(metrics.equity))
            Fund("Floating P/L", "${signedMoney(metrics.floatingProfit)} USD")
            Fund("Margin", usd(metrics.margin))
            Fund("Free margin", usd(metrics.freeMargin))
            Fund("Margin level", metrics.marginLevel?.let(::percent) ?: "—")
            Fund("Leverage", "1:${account.leverage}")
        }
    }
}

@Composable
private fun Fund(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp)
    }
}

/** Moves virtual money between two of the user's accounts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(accounts: List<Account>, fromNumber: Long, engine: TradingEngine, onResult: (OrderResult) -> Unit, onDismiss: () -> Unit) {
    var from by remember { mutableStateOf(fromNumber) }
    var to by remember { mutableStateOf(accounts.firstOrNull { it.number != fromNumber }?.number) }
    var amount by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Transfer", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("From", fontSize = 12.sp, color = AppColors.TextSecondary)
            AccountChoice(accounts, from) { from = it }
            Text("To", fontSize = 12.sp, color = AppColors.TextSecondary)
            AccountChoice(accounts.filter { it.number != from }, to) { to = it }
            Text("Amount, USD", fontSize = 12.sp, color = AppColors.TextSecondary)
            Stepper(amount, { amount = it }, 100.0, 2, 100.0, "0.00", modifier = Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp)))
            val v = amount.toPriceOrNull()
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(6.dp)).background(if (v != null && to != null) AppColors.Accent else AppColors.SurfaceHigh)
                    .clickable(enabled = v != null && to != null) {
                        val r = engine.transfer(from, to!!, v!!)
                        onResult(r)
                        if (r is OrderResult.Success) onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) { Text("Transfer", color = if (v != null) AppColors.OnAccent else AppColors.TextSecondary, fontSize = 15.sp) }
        }
    }
}

@Composable
private fun AccountChoice(accounts: List<Account>, selected: Long?, onPick: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(AppColors.Surface)) {
        accounts.forEach { a ->
            Row(Modifier.fillMaxWidth().clickable { onPick(a.number) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(CircleShape).border(2.dp, if (a.number == selected) AppColors.Accent else AppColors.TextSecondary, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text("${a.title} #${a.number} (${a.type.label})", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(usd(a.balance), fontSize = 13.sp, color = AppColors.TextSecondary)
            }
        }
    }
}

/** Dark card toast shown over the chart, e.g. "Order opened". */
@Composable
fun ChartToast(title: String, body: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Toast)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = 13.sp)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp)) }
    }
}

/** "Placing order" pill with a spinner. */
@Composable
fun PlacingPill(modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Toast)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = AppColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(12.dp))
        Text("Placing order", fontSize = 13.sp)
    }
}
