package com.novatrade.demo.ui.instrument

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.model.PendingOrder
import com.novatrade.demo.model.Position
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Side
import com.novatrade.demo.trading.OrderResult
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.common.InfoRow
import com.novatrade.demo.ui.common.InstrumentIcon
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.format
import com.novatrade.demo.util.lots
import com.novatrade.demo.util.signedMoney
import com.novatrade.demo.util.signedPercent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            content()
        }
    }
}

@Composable
fun InstrumentPickerSheet(quotes: Map<String, Quote>, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    SimpleSheet("Instruments", onDismiss) {
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(Instruments.all, key = { it.symbol }) { inst ->
                val q = quotes[inst.symbol]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(inst.symbol) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InstrumentIcon(inst, size = 28)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            inst.displayName,
                            fontSize = 15.sp,
                            fontWeight = if (inst.symbol == current) FontWeight.Bold else FontWeight.Medium,
                            color = if (inst.symbol == current) AppColors.Accent else AppColors.TextPrimary,
                        )
                        Text(inst.name, fontSize = 12.sp, color = AppColors.TextSecondary)
                    }
                    if (q != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(inst.format(q.bid), fontSize = 14.sp)
                            Text(signedPercent(q.changePercent), fontSize = 11.sp, color = AppColors.pnl(q.changePercent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SymbolTradesSheet(
    inst: Instrument,
    positions: List<Position>,
    pending: List<PendingOrder>,
    quote: Quote?,
    engine: TradingEngine,
    onResult: (OrderResult) -> Unit,
    onDismiss: () -> Unit,
) {
    SimpleSheet("${inst.displayName} · Open & Pending", onDismiss) {
        if (positions.isEmpty() && pending.isEmpty()) {
            Text(
                "No open or pending orders on ${inst.displayName}",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(positions, key = { "p${it.id}" }) { p ->
                val profit = engine.profitOf(p, quote)
                TradeLine(
                    title = "${p.side.label} ${lots(p.lots)} lots at ${inst.format(p.openPrice)}",
                    side = p.side,
                    value = signedMoney(profit),
                    valueColor = AppColors.pnl(profit),
                ) { onResult(engine.close(p.id)) }
            }
            items(pending, key = { "o${it.id}" }) { o ->
                TradeLine(
                    title = "${o.label} ${lots(o.lots)} lots at ${inst.format(o.price)}",
                    side = o.side,
                    value = "Pending",
                    valueColor = AppColors.TextSecondary,
                ) { onResult(engine.cancelPending(o.id)) }
            }
        }
    }
}

@Composable
private fun TradeLine(title: String, side: Side, value: String, valueColor: Color, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = if (side == Side.BUY) AppColors.Buy else AppColors.Sell, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
}

@Composable
fun InstrumentInfoSheet(inst: Instrument, quote: Quote?, leverage: Int, onDismiss: () -> Unit) {
    SimpleSheet(inst.name, onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InfoRow("Symbol", inst.symbol)
            InfoRow("Category", inst.category.label)
            InfoRow("Contract size", "${inst.contractSize.toLong()} units per lot")
            InfoRow("Digits", inst.digits.toString())
            InfoRow("Spread", quote?.let { inst.format(it.spread) } ?: "—")
            InfoRow("Max leverage", "1:${minOf(leverage, inst.maxLeverage)}")
            InfoRow("Price feed", if (quote?.live == true) "Live market price" else "Simulated")
        }
    }
}

@Composable
fun ChartSettingsSheet(settings: ChartSettings, onChange: (ChartSettings) -> Unit, onDismiss: () -> Unit) {
    SimpleSheet("Chart settings", onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            ToggleRow("Grid lines", settings.grid) { onChange(settings.copy(grid = it)) }
            ToggleRow("Auto scale", settings.autoScale) { onChange(settings.copy(autoScale = it)) }
            ToggleRow("Logarithmic scale", settings.scale == ScaleMode.LOG) {
                onChange(settings.copy(scale = if (it) ScaleMode.LOG else ScaleMode.NORMAL))
            }
            ToggleRow("Percentage scale", settings.scale == ScaleMode.PERCENT) {
                onChange(settings.copy(scale = if (it) ScaleMode.PERCENT else ScaleMode.NORMAL))
            }
        }
    }
}

@Composable
fun IndicatorsSheet(settings: ChartSettings, onChange: (ChartSettings) -> Unit, onDismiss: () -> Unit) {
    SimpleSheet("Indicators", onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Indicator.entries.forEach { ind ->
                ToggleRow(ind.label, ind in settings.indicators) { on ->
                    onChange(settings.copy(indicators = if (on) settings.indicators + ind else settings.indicators - ind))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Buy, checkedThumbColor = Color.White),
        )
    }
}

@Composable
fun InstrumentLeverageSheet(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    SimpleSheet("Account settings · Leverage", onDismiss) {
        Column(Modifier.padding(vertical = 4.dp)) {
            TradingEngine.LEVERAGES.forEach { lev ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(lev) }.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("1:$lev", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (lev == current) Text("✓", color = AppColors.Accent, fontSize = 16.sp)
                }
            }
        }
    }
}
