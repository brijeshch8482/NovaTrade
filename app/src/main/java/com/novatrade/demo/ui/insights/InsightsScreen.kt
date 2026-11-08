package com.novatrade.demo.ui.insights

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novatrade.demo.data.CalendarEvent
import com.novatrade.demo.data.InsightsRepository
import com.novatrade.demo.data.MarketRepository
import com.novatrade.demo.data.NewsItem
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountMetrics
import com.novatrade.demo.model.Candle
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Timeframe
import com.novatrade.demo.ui.common.Badge
import com.novatrade.demo.ui.common.InstrumentIcon
import com.novatrade.demo.ui.instrument.AccountCapsule
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.format
import com.novatrade.demo.util.percent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Intraday read-out from the app's own 1h candles: price vs. 20/50-period averages and ATR. */
private data class Signal(val bullish: Boolean, val target: Double, val time: Long)

private fun signalFor(candles: List<Candle>?): Signal? {
    if (candles == null || candles.size < 50) return null
    val closes = candles.map { it.close }
    val price = closes.last()
    val ma20 = closes.takeLast(20).average()
    // Direction from price vs. the 20-period average; the 50-period average confirms strength only.
    val atr = candles.takeLast(15).zipWithNext { prev, c -> maxOf(c.high - c.low, abs(c.high - prev.close), abs(c.low - prev.close)) }.average()
    val bullish = price >= ma20
    return Signal(bullish, if (bullish) price + atr * 2 else price - atr * 2, candles.last().time)
}

private val SIGNAL_SYMBOLS = listOf("XAUUSD", "BTCUSD", "EURUSD", "USTEC", "GBPUSD", "USDJPY", "ETHUSD", "US30", "USOIL", "AAPL", "NVDA", "TSLA")

private val CCY_FLAG = mapOf("USD" to "USD", "EUR" to "EUR", "GBP" to "GBP", "JPY" to "JPY", "CHF" to "CHF", "AUD" to "AUD", "CAD" to "CAD", "NZD" to "NZD", "CNY" to "#cn", "All" to "#eu")
private val COUNTRY_CODE = mapOf("USD" to "US", "EUR" to "EMU", "GBP" to "UK", "JPY" to "JP", "CHF" to "CH", "AUD" to "AU", "CAD" to "CA", "NZD" to "NZ", "CNY" to "CN")

private enum class More { MOVERS, SIGNALS, EVENTS, NEWS }

@Composable
fun InsightsScreen(
    quotes: Map<String, Quote>,
    market: MarketRepository,
    insights: InsightsRepository,
    account: Account,
    metrics: AccountMetrics,
    onAccountClick: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { insights.start() }
    val events by insights.events.collectAsStateWithLifecycle()
    val news by insights.news.collectAsStateWithLifecycle()
    var more by rememberSaveable { mutableStateOf<More?>(null) }
    val context = LocalContext.current
    val openLink: (String) -> Unit = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }

    val movers = Instruments.all.filter { quotes[it.symbol]?.let { q -> abs(q.changePercent) > 0.001 } == true }
        .sortedByDescending { abs(quotes.getValue(it.symbol).changePercent) }

    more?.let { m ->
        BackHandler { more = null }
        Column(modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { more = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(
                    when (m) {
                        More.MOVERS -> "Top movers"
                        More.SIGNALS -> "Trading signals"
                        More.EVENTS -> "Upcoming events"
                        More.NEWS -> "Top news"
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                when (m) {
                    More.MOVERS -> items(movers, key = { it.symbol }) { inst -> MoverRow(inst, quotes[inst.symbol]) { onOpen(inst.symbol) } }
                    More.SIGNALS -> items(SIGNAL_SYMBOLS) { sym ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { SignalCard(Instruments[sym], market, Modifier.fillMaxWidth()) { onOpen(sym) } }
                    }
                    More.EVENTS -> eventItems(events.orEmpty())
                    More.NEWS -> items(news.orEmpty(), key = { it.link }) { n -> NewsRow(n, insights) { openLink(n.link) } }
                }
            }
        }
        return
    }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { AccountCapsule(account, metrics, onAccountClick) }
            Text("Insights", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
        }
        item {
            SectionHeader("TOP MOVERS") { more = More.MOVERS }
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(movers.take(10), key = { it.symbol }) { inst -> MoverCard(inst, quotes[inst.symbol]) { onOpen(inst.symbol) } }
            }
        }
        item {
            SectionHeader("TRADING SIGNALS") { more = More.SIGNALS }
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SIGNAL_SYMBOLS) { sym -> SignalCard(Instruments[sym], market, Modifier.width(184.dp)) { onOpen(sym) } }
            }
        }
        item {
            SectionHeader("UPCOMING EVENTS") { more = More.EVENTS }
            val list = events
            Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AppColors.Surface)) {
                when {
                    list == null -> Skeleton()
                    list.isEmpty() -> Text(
                        "No more events scheduled this week. New events appear when the next week's calendar is published.",
                        fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(16.dp),
                    )
                    else -> list.take(3).forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
                        EventRow(e)
                    }
                }
            }
        }
        item {
            SectionHeader("TOP NEWS") { more = More.NEWS }
            val list = news
            Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AppColors.Surface)) {
                when {
                    list == null -> Skeleton()
                    list.isEmpty() -> Text("News is unavailable right now.", fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(16.dp))
                    else -> list.take(3).forEach { n -> NewsRow(n, insights) { openLink(n.link) } }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp, color = AppColors.TextSecondary, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
        Text("Show more", fontSize = 13.sp, color = AppColors.Accent, modifier = Modifier.clickable(onClick = onMore).padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
private fun Skeleton() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width = 160.dp, height = 14.dp).background(AppColors.SurfaceHigh))
        Box(Modifier.fillMaxWidth(0.9f).height(8.dp).background(AppColors.SurfaceHigh))
        Box(Modifier.fillMaxWidth(0.7f).height(8.dp).background(AppColors.SurfaceHigh))
    }
}

@Composable
private fun MoverCard(inst: Instrument, quote: Quote?, onClick: () -> Unit) {
    val ch = quote?.changePercent ?: 0.0
    Column(
        Modifier.width(118.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.Surface).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InstrumentIcon(inst, size = 28)
        Text(inst.display, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text((if (ch >= 0) "↑ " else "↓ ") + percent(abs(ch)), fontSize = 13.sp, color = if (ch >= 0) AppColors.Buy else AppColors.Sell)
        Text(quote?.let { inst.format(it.bid) } ?: "—", fontSize = 12.sp, color = AppColors.TextSecondary, maxLines = 1)
    }
}

@Composable
private fun MoverRow(inst: Instrument, quote: Quote?, onClick: () -> Unit) {
    val ch = quote?.changePercent ?: 0.0
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        InstrumentIcon(inst, size = 28)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(inst.display, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(inst.name, fontSize = 12.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(quote?.let { inst.format(it.bid) } ?: "—", fontSize = 14.sp)
            Text((if (ch >= 0) "↑ " else "↓ ") + percent(abs(ch)), fontSize = 12.sp, color = if (ch >= 0) AppColors.Buy else AppColors.Sell)
        }
    }
}

@Composable
private fun SignalCard(inst: Instrument, market: MarketRepository, modifier: Modifier, onClick: () -> Unit) {
    val candles by remember(inst.symbol) { market.candles(inst.symbol, Timeframe.H1) }.collectAsStateWithLifecycle()
    val signal = signalFor(candles)
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.US) }
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.Surface).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(96.dp).background(Color(0xFF0E141A))) {
            MiniChart(candles?.takeLast(60).orEmpty(), signal?.target)
            Text(
                "${inst.name.substringBefore(" vs")}  ·  HOURLY",
                fontSize = 8.sp, color = AppColors.TextPrimary, maxLines = 1,
                modifier = Modifier.padding(6.dp),
            )
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    signal == null -> "${inst.display}: Loading…"
                    else -> "${inst.display}: Target ${inst.format(signal.target)}"
                },
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(40.dp),
            )
            Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (signal != null) {
                    Text(
                        (if (signal.bullish) "↑ " else "↓ ") + "Short term",
                        fontSize = 11.sp, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(if (signal.bullish) AppColors.Buy else AppColors.Sell).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(timeFmt.format(Date(signal.time)), fontSize = 11.sp, color = AppColors.TextSecondary)
                }
            }
        }
    }
}

/** Thumbnail chart: closes as a line, 20-period average, and the target as a dashed line. */
@Composable
private fun MiniChart(candles: List<Candle>, target: Double?) {
    if (candles.size < 2) return
    Canvas(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 6.dp, start = 6.dp, end = 6.dp)) {
        val closes = candles.map { it.close }
        val lo = minOf(candles.minOf { it.low }, target ?: Double.MAX_VALUE)
        val hi = maxOf(candles.maxOf { it.high }, target ?: -Double.MAX_VALUE)
        val range = (hi - lo).takeIf { it > 0 } ?: 1.0
        val dx = size.width / (candles.size - 1)
        fun y(v: Double) = ((hi - v) / range * size.height).toFloat()
        candles.forEachIndexed { i, c ->
            val x = i * dx
            val color = if (c.close >= c.open) AppColors.CandleUp else AppColors.CandleDown
            drawLine(color, Offset(x, y(c.high)), Offset(x, y(c.low)), 1f)
            drawRect(color, Offset(x - dx * 0.3f, y(maxOf(c.open, c.close))), Size(dx * 0.6f, maxOf(1f, y(minOf(c.open, c.close)) - y(maxOf(c.open, c.close)))))
        }
        if (closes.size >= 20) {
            val ma = closes.indices.map { i -> if (i < 19) null else closes.subList(i - 19, i + 1).average() }
            for (i in 1 until ma.size) {
                val a = ma[i - 1] ?: continue
                val b = ma[i] ?: continue
                drawLine(Color(0xFFF5A623), Offset((i - 1) * dx, y(a)), Offset(i * dx, y(b)), 1.2f)
            }
        }
        target?.let {
            drawLine(AppColors.Accent, Offset(0f, y(it)), Offset(size.width, y(it)), 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
        }
    }
}

private fun LazyListScope.eventItems(list: List<CalendarEvent>) {
    if (list.isEmpty()) {
        item { Text("No more events scheduled this week.", fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(16.dp)) }
    }
    items(list) { e ->
        EventRow(e)
        HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
    }
}

@Composable
private fun EventRow(e: CalendarEvent) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Badge(CCY_FLAG[e.country] ?: "#eu", 20)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Text(COUNTRY_CODE[e.country] ?: e.country, fontSize = 11.sp, color = AppColors.TextSecondary)
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    repeat(3) { i -> Box(Modifier.size(width = 3.dp, height = 10.dp).background(if (i < e.impact) Color(0xFFE8B62E) else AppColors.SurfaceHigh)) }
                }
                Spacer(Modifier.width(8.dp))
                Text(relative(e.time), fontSize = 11.sp, color = AppColors.TextSecondary)
                if (e.forecast.isNotBlank()) Text("  ·  Forecast ${e.forecast}", fontSize = 11.sp, color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun NewsRow(n: NewsItem, insights: InsightsRepository, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val bitmap = n.image?.let { url -> insights.image(url).collectAsStateWithLifecycle().value }
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(AppColors.SurfaceHigh)) {
            if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(ago(n.time), fontSize = 11.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

private fun relative(time: Long): String {
    val diff = time - System.currentTimeMillis()
    val minutes = diff / 60_000
    return when {
        minutes < 0 -> "Now"
        minutes < 60 -> "In $minutes min"
        minutes < 24 * 60 -> "In ${minutes / 60} hours"
        else -> "In ${minutes / (24 * 60)} days"
    }
}

private fun ago(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 60 -> "$minutes minutes ago"
        minutes < 24 * 60 -> "${minutes / 60} hours ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}
