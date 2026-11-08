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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Side
import com.novatrade.demo.trading.OrderResult
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.common.InstrumentIcon
import com.novatrade.demo.ui.common.Stepper
import com.novatrade.demo.ui.common.toPriceOrNull
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.format
import com.novatrade.demo.util.lots
import com.novatrade.demo.util.signedMoney
import com.novatrade.demo.util.usd
import java.util.Locale
import kotlin.math.pow

private enum class OrderKind(val label: String) { MARKET("Market"), LIMIT("Limit"), STOP("Stop") }

/** How a Take Profit / Stop Loss value is entered. */
private enum class LevelUnit(val label: String) { PRICE("Price"), PIPS("Pips"), USD("USD"), PERCENT("% of balance") }

/** One pip: the 4th decimal on 5-digit quotes (2nd on 3-digit), otherwise the smallest price step. */
private fun Instrument.pip(): Double = if (digits == 5 || digits == 3) 10.0.pow(-(digits - 1)) else 10.0.pow(-digits)

/**
 * Full order form: order type, side, volume, and TP/SL entered as a price, pips, USD or % of balance.
 * Opened from the order settings button on the chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderSheet(
    symbol: String,
    initialSide: Side,
    quote: Quote?,
    leverage: Int,
    engine: TradingEngine,
    onDismiss: () -> Unit,
    onResult: (OrderResult) -> Unit,
) {
    val inst = Instruments[symbol]
    var side by remember { mutableStateOf(initialSide) }
    var kind by remember { mutableStateOf(OrderKind.MARKET) }
    var volumeText by remember { mutableStateOf("0.01") }
    var priceText by remember { mutableStateOf("") }
    var tpText by remember { mutableStateOf("") }
    var slText by remember { mutableStateOf("") }
    var tpUnit by remember { mutableStateOf(LevelUnit.PRICE) }
    var slUnit by remember { mutableStateOf(LevelUnit.PRICE) }
    var showInfo by remember { mutableStateOf(false) }

    val balance = engine.account.value.balance
    val marketPrice = quote?.let { if (side == Side.BUY) it.ask else it.bid }
    val entry = if (kind == OrderKind.MARKET) marketPrice else priceText.toPriceOrNull()
    val volume = volumeText.toPriceOrNull()
    val dir = if (side == Side.BUY) 1 else -1

    /** Converts a TP (profit = true) or SL value in [unit] to a price. */
    fun toPrice(text: String, unit: LevelUnit, profit: Boolean): Double? {
        val v = text.toPriceOrNull() ?: return null
        val e = entry ?: return null
        val sign = dir * if (profit) 1 else -1
        return when (unit) {
            LevelUnit.PRICE -> v
            LevelUnit.PIPS -> e + sign * v * inst.pip()
            LevelUnit.USD, LevelUnit.PERCENT -> {
                val usdAmount = if (unit == LevelUnit.USD) v else balance * v / 100
                val perPoint = volume?.let { engine.usdPerPoint(inst, it) }?.takeIf { it > 0 } ?: return null
                e + sign * usdAmount / perPoint
            }
        }
    }

    val tp = toPrice(tpText, tpUnit, true)
    val sl = toPrice(slText, slUnit, false)
    val margin = if (entry != null && volume != null) engine.requiredMargin(symbol, volume, entry) else null
    val fee = if (quote != null && volume != null) feeFor(engine, inst, quote, volume) else null
    val sideColor = if (side == Side.BUY) AppColors.Buy else AppColors.Sell

    fun pnlAt(level: Double?): Double? {
        val e = entry ?: return null
        val v = volume ?: return null
        level ?: return null
        return (level - e) * dir * engine.usdPerPoint(inst, v)
    }

    fun submit() {
        val v = volume ?: return onResult(OrderResult.Error("Enter a valid volume"))
        if (tpText.isNotBlank() && tp == null) return onResult(OrderResult.Error("Invalid Take Profit"))
        if (slText.isNotBlank() && sl == null) return onResult(OrderResult.Error("Invalid Stop Loss"))
        if (kind == OrderKind.MARKET) return onResult(engine.open(symbol, side, v, sl, tp))

        val p = priceText.toPriceOrNull() ?: return onResult(OrderResult.Error("Enter an open price"))
        val m = marketPrice ?: return
        // Limit orders wait for a better price, stop orders for a breakout.
        val wantsBelow = (side == Side.BUY) == (kind == OrderKind.LIMIT)
        if (wantsBelow && p >= m) return onResult(OrderResult.Error("${side.label} ${kind.label} price must be below ${inst.format(m)}"))
        if (!wantsBelow && p <= m) return onResult(OrderResult.Error("${side.label} ${kind.label} price must be above ${inst.format(m)}"))
        onResult(engine.placePending(symbol, side, v, p, sl, tp))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.SheetBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentIcon(inst, size = 24)
                Spacer(Modifier.width(8.dp))
                Text(inst.displayName, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text("Regular", fontSize = 13.sp, color = AppColors.TextSecondary)
            }

            Segmented(OrderKind.entries.map { it.label }, kind.ordinal, fill = true) { i ->
                kind = OrderKind.entries[i]
                if (kind != OrderKind.MARKET && priceText.isEmpty()) marketPrice?.let { priceText = inst.format(it) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SideTab("Sell", quote?.let { inst.format(it.bid) }, AppColors.Sell, side == Side.SELL, Modifier.weight(1f)) { side = Side.SELL }
                SideTab("Buy", quote?.let { inst.format(it.ask) }, AppColors.Buy, side == Side.BUY, Modifier.weight(1f)) { side = Side.BUY }
            }

            if (kind != OrderKind.MARKET) {
                FieldBlock("Open price", trailing = marketPrice?.let { m ->
                    priceText.toPriceOrNull()?.let { p -> String.format(Locale.US, "%+.1f pips", (p - m) / inst.pip()) }
                }) {
                    Stepper(priceText, { priceText = it }, inst.pip(), inst.digits, marketPrice, "Price", modifier = Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp)))
                }
            }

            FieldBlock("Volume", trailing = "Lots") {
                Stepper(
                    volumeText, { volumeText = it }, 0.01, 2, 0.01, "0.01",
                    modifier = Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp)),
                    min = TradingEngine.MIN_LOT,
                )
            }

            LevelField(
                label = "Take Profit",
                text = tpText,
                onText = { tpText = it },
                unit = tpUnit,
                onUnit = {
                    tpUnit = it
                    tpText = ""
                },
                inst = inst,
                seed = entry,
                hint = tp?.let { p -> "${inst.format(p)}  ·  ${pnlAt(p)?.let(::signedMoney) ?: "—"} USD" },
                hintColor = AppColors.Profit,
            )
            LevelField(
                label = "Stop Loss",
                text = slText,
                onText = { slText = it },
                unit = slUnit,
                onUnit = {
                    slUnit = it
                    slText = ""
                },
                inst = inst,
                seed = entry,
                hint = sl?.let { p -> "${inst.format(p)}  ·  ${pnlAt(p)?.let(::signedMoney) ?: "—"} USD" },
                hintColor = AppColors.Loss,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.SheetCard)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", fontSize = 14.sp) }
                Column(
                    Modifier
                        .weight(2f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(sideColor)
                        .clickable(enabled = quote != null, onClick = ::submit),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val title = if (kind == OrderKind.MARKET) "Confirm ${side.label}" else "Place ${side.label} ${kind.label}"
                    Text("$title ${volume?.let(::lots) ?: "—"} lots", color = Color.White, fontSize = 13.sp, lineHeight = 15.sp)
                    Text(entry?.let(inst::format) ?: "—", color = Color.White, fontSize = 12.sp, lineHeight = 14.sp)
                }
            }

            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Fees: ~ ${fee?.let { String.format(Locale.US, "%.2f", it) } ?: "—"} USD  |  ",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    repeat(3) { Box(Modifier.size(width = 3.dp, height = 9.dp).background(AppColors.Sell)) }
                }
                Text(
                    " Margin: ${margin?.let(::usd) ?: "—"} (1:${minOf(leverage, inst.maxLeverage)})",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Order details",
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(18.dp).clickable { showInfo = true },
                )
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = AppColors.SheetCard,
            title = { Text("Order details", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailLine("Spread", quote?.let { inst.format(it.spread) } ?: "—")
                    DetailLine("Pip size", inst.format(inst.pip()))
                    DetailLine("Pip value", volume?.let { v -> entry?.let { usd(engine.usdPerPoint(inst, v) * inst.pip()) } } ?: "—")
                    DetailLine("Contract size", "${inst.contractSize.toLong()} units")
                    DetailLine("Leverage", "1:${minOf(leverage, inst.maxLeverage)}")
                    DetailLine("Margin", margin?.let(::usd) ?: "—")
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK", color = AppColors.Accent) } },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp)
    }
}

@Composable
private fun FieldBlock(label: String, trailing: String? = null, trailingContent: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = AppColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, color = AppColors.TextSecondary, fontSize = 12.sp) }
            trailingContent?.invoke()
        }
        content()
    }
}

@Composable
private fun LevelField(
    label: String,
    text: String,
    onText: (String) -> Unit,
    unit: LevelUnit,
    onUnit: (LevelUnit) -> Unit,
    inst: Instrument,
    seed: Double?,
    hint: String?,
    hintColor: Color,
) {
    var unitMenu by remember { mutableStateOf(false) }
    FieldBlock(label, trailingContent = {
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { unitMenu = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(unit.label, fontSize = 12.sp)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Unit", modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                LevelUnit.entries.forEach { u ->
                    DropdownMenuItem(
                        text = { Text(u.label, fontWeight = if (u == unit) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            unitMenu = false
                            onUnit(u)
                        },
                    )
                }
            }
        }
    }) {
        val (step, decimals, start) = when (unit) {
            LevelUnit.PRICE -> Triple(inst.pip(), inst.digits, seed)
            LevelUnit.PIPS -> Triple(1.0, 0, 0.0)
            LevelUnit.USD -> Triple(1.0, 2, 0.0)
            LevelUnit.PERCENT -> Triple(0.5, 1, 0.0)
        }
        Stepper(
            text, onText, step, decimals, start, "Not set",
            modifier = Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp)),
        )
        if (hint != null && text.isNotBlank()) {
            Text(hint, fontSize = 11.sp, color = hintColor, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun SideTab(label: String, price: String?, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) color else AppColors.SheetCard)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = if (selected) Color.White else AppColors.TextSecondary, fontSize = 12.sp, lineHeight = 14.sp)
        Text(price ?: "—", color = if (selected) Color.White else color, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 17.sp)
    }
}

/** Two-or-more option switch; [fill] stretches it to the full width with equal parts. */
@Composable
fun Segmented(options: List<String>, selected: Int, fill: Boolean = false, onSelect: (Int) -> Unit) {
    Row(
        (if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SheetCard)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (i == selected) AppColors.SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (i == selected) AppColors.TextPrimary else AppColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
