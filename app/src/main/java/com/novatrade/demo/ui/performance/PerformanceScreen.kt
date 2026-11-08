package com.novatrade.demo.ui.performance

import com.novatrade.demo.Brand

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.model.ClosedTrade
import com.novatrade.demo.ui.common.UnderlineTabs
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.lots
import com.novatrade.demo.util.money
import com.novatrade.demo.util.signedMoney
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PERIODS = listOf(7 to "Last 7 days", 30 to "Last 30 days", 90 to "Last 90 days", 365 to "Last 365 days")
private const val DAY = 86_400_000L

/** Account filter: one account, or every real/demo account together. */
private sealed interface Scope {
    val label: String

    data class Single(val account: Account) : Scope {
        override val label get() = "${account.kind} #${account.number}"
    }

    data class AllOf(val type: AccountType) : Scope {
        override val label get() = "All ${type.label.lowercase()} accounts"
    }
}

@Composable
fun PerformanceScreen(accounts: List<Account>, account: Account, modifier: Modifier = Modifier) {
    var scopeIndex by rememberSaveable { mutableIntStateOf(0) }
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scopes: List<Scope> = AccountType.entries.map { Scope.AllOf(it) } + accounts.map { Scope.Single(it) }
    val scope = scopes.getOrNull(scopeIndex) ?: Scope.Single(account)
    val chosen = when (scope) {
        is Scope.Single -> listOf(scope.account)
        is Scope.AllOf -> accounts.filter { it.type == scope.type }
    }
    val days = PERIODS[periodIndex].first
    val from = startOfDay(System.currentTimeMillis()) - (days - 1) * DAY
    val trades = chosen.flatMap { it.history }.filter { it.closeTime >= from }.sortedBy { it.closeTime }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            "Performance",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 52.dp, bottom = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("All times on this page use UTC (GMT+0) and may differ from your local time", fontSize = 12.sp)
        }
        androidx.compose.material3.HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown(scope.label, scopes.map { it.label }) { scopeIndex = it }
            Dropdown("📅 " + PERIODS[periodIndex].second, PERIODS.map { it.second }) { periodIndex = it }
        }
        Spacer(Modifier.height(12.dp))
        UnderlineTabs(listOf("Summary", "Benefits"), tab, { tab = it }, Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(8.dp))

        if (tab == 1) {
            Benefits(trades)
            return@Column
        }

        val net = trades.sumOf { it.profit }
        val wins = trades.count { it.profit > 0 }
        val losses = trades.count { it.profit < 0 }
        val volume = trades.sumOf { it.lots }
        val daily = dailySums(trades, from, days) { it.profit }
        val dailyVolume = dailySums(trades, from, days) { it.lots }

        Card("Net profit", "${signedMoney(net)} USD", AppColors.pnl(net)) {
            Bars(daily, signed = true)
            DateAxis(from, days)
        }
        Card("Closed orders", trades.size.toString()) {
            if (trades.isEmpty()) {
                Text("No closed orders in this period", fontSize = 13.sp, color = AppColors.TextSecondary)
            } else {
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                    if (wins > 0) Box(Modifier.weight(wins.toFloat()).fillMaxSize().background(AppColors.Profit))
                    if (losses > 0) Box(Modifier.weight(losses.toFloat()).fillMaxSize().background(AppColors.Loss))
                    val even = trades.size - wins - losses
                    if (even > 0) Box(Modifier.weight(even.toFloat()).fillMaxSize().background(AppColors.TextSecondary))
                }
                Spacer(Modifier.height(10.dp))
                Legend(AppColors.Profit, "Profitable", wins.toString())
                Legend(AppColors.Loss, "Unprofitable", losses.toString())
            }
        }
        Card("Trading volume", "${lots(volume)} lots") {
            Bars(dailyVolume, signed = false)
            DateAxis(from, days)
        }
        val equity = chosen.sumOf { it.totalDeposits } + chosen.flatMap { it.history }.filter { it.closeTime < from }.sumOf { it.profit }
        val curve = trades.runningFold(equity) { acc, t -> acc + t.profit }
        Card("Equity", "${money(chosen.sumOf { it.balance })} USD") {
            Line(curve)
        }
    }
}

@Composable
private fun Dropdown(label: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(AppColors.Surface)
                .clickable { open = true }
                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.sp, maxLines = 1)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = {
                    open = false
                    onPick(i)
                })
            }
        }
    }
}

@Composable
private fun Card(title: String, value: String, valueColor: Color = AppColors.TextPrimary, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(16.dp),
    ) {
        Text(title, fontSize = 13.sp, color = AppColors.TextSecondary)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One bar per day; with [signed], bars grow up (green) or down (red) from a zero line. */
@Composable
private fun Bars(values: List<Double>, signed: Boolean) {
    val max = values.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val n = values.size
        val slot = size.width / n
        val barW = (slot * 0.6f).coerceAtMost(14.dp.toPx())
        val zero = if (signed) size.height / 2 else size.height
        drawLine(Color(0xFF2B3036), Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
        values.forEachIndexed { i, v ->
            if (v == 0.0) return@forEachIndexed
            val h = (kotlin.math.abs(v) / max * (if (signed) size.height / 2 else size.height)).toFloat()
            val x = slot * i + (slot - barW) / 2
            val color = if (!signed) AppColors.Buy else if (v > 0) AppColors.Profit else AppColors.Loss
            drawRect(color, Offset(x, if (v > 0 || !signed) zero - h else zero), Size(barW, h))
        }
    }
}

@Composable
private fun DateAxis(from: Long, days: Int) {
    val fmt = remember { SimpleDateFormat("dd MMM", Locale.US) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(fmt.format(Date(from)), fontSize = 11.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(fmt.format(Date(from + (days - 1) * DAY)), fontSize = 11.sp, color = AppColors.TextSecondary)
    }
}

@Composable
private fun Line(points: List<Double>) {
    if (points.size < 2) {
        Text("Close some orders to see how your equity changes", fontSize = 13.sp, color = AppColors.TextSecondary)
        return
    }
    val color = if (points.last() >= points.first()) AppColors.Profit else AppColors.Loss
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val lo = points.min()
        val hi = points.max()
        val range = (hi - lo).takeIf { it > 0 } ?: 1.0
        val dx = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, v ->
            val o = Offset(i * dx, ((hi - v) / range * size.height).toFloat())
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.12f))
        drawPath(path, color, style = Stroke(2.dp.toPx()))
    }
}

/** Rewards-style tab: what the trader's activity has earned in this demo. */
@Composable
private fun Benefits(trades: List<ClosedTrade>) {
    val spreadSaved = trades.sumOf { it.lots } * 0.5
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Surface)
                .padding(16.dp),
        ) {
            Text("Swap-free trading", fontSize = 13.sp, color = AppColors.TextSecondary)
            Text("0.00 USD swaps paid", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Positions held overnight are never charged swaps in ${Brand.name}.", fontSize = 12.sp, color = AppColors.TextSecondary)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Surface)
                .padding(16.dp),
        ) {
            Text("Stop out protection", fontSize = 13.sp, color = AppColors.TextSecondary)
            Text("30% stop out level", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Positions are only closed automatically when margin level falls below 30%.", fontSize = 12.sp, color = AppColors.TextSecondary)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Surface)
                .padding(16.dp),
        ) {
            Text("Tight spreads", fontSize = 13.sp, color = AppColors.TextSecondary)
            Text("~${money(spreadSaved)} USD saved", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Estimate versus a typical retail spread, for the volume traded in this period.", fontSize = 12.sp, color = AppColors.TextSecondary)
        }
    }
}

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dailySums(trades: List<ClosedTrade>, from: Long, days: Int, value: (ClosedTrade) -> Double): List<Double> {
    val sums = DoubleArray(days)
    trades.forEach { t ->
        val i = ((startOfDay(t.closeTime) - from) / DAY).toInt()
        if (i in 0 until days) sums[i] += value(t)
    }
    return sums.toList()
}
