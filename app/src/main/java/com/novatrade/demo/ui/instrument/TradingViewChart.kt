package com.novatrade.demo.ui.instrument

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.novatrade.demo.model.Candle
import com.novatrade.demo.ui.theme.AppColors
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone

/** A horizontal marker such as an open position, stop loss or take profit. */
data class ChartLine(val price: Double, val color: Color, val label: String, val positionId: Long? = null)

/** Chart styles in menu order; [group] splits the menu with dividers. Names are stored in layouts, so never rename them. */
enum class ChartType(val js: String, val label: String, val group: Int) {
    BARS("bars", "Bars", 0),
    CANDLES("candles", "Candles", 0),
    HOLLOW("hollow", "Hollow candles", 0),
    VOLUME_CANDLES("volcandles", "Volume candles", 0),
    HLC_BARS("hlcbars", "HLC bars", 0),
    LINE("line", "Line", 1),
    LINE_MARKERS("linemarkers", "Line with markers", 1),
    STEP("step", "Step line", 1),
    AREA("area", "Area", 2),
    HLC_AREA("hlcarea", "HLC area", 2),
    BASELINE("baseline", "Baseline", 2),
    COLUMNS("columns", "Columns", 3),
    HIGH_LOW("highlow", "High-low", 3),
    HEIKIN_ASHI("heikinashi", "Heikin Ashi", 4),
    RENKO("renko", "Renko", 4),
    LINE_BREAK("linebreak", "Line break", 4),
    KAGI("kagi", "Kagi", 4),
    POINT_FIGURE("pnf", "Point & figure", 4),
}

enum class Indicator(val js: String, val label: String) { MA20("ma20", "MA 20"), MA50("ma50", "MA 50"), EMA20("ema20", "EMA 20") }

/** Price scale modes in menu order. Names are stored in layouts, so never rename them. */
enum class ScaleMode(val js: String, val label: String) {
    NORMAL("normal", "Regular"), PERCENT("percent", "Percent"), INDEXED("indexed", "Indexed to 100"), LOG("log", "Logarithmic"),
}

/** Labels on the price scale. Names are stored in layouts. */
enum class ScaleLabel(val js: String, val label: String) {
    SYMBOL_NAME("name", "Symbol name label"),
    LAST_PRICE("last", "Symbol last price label"),
    PREV_CLOSE("prev", "Symbol previous day close price label"),
    HIGH_LOW("hl", "High and low price labels"),
    BID_ASK("bidask", "Bid and ask labels"),
    INDICATOR_NAMES("indnames", "Indicators name labels"),
    INDICATOR_VALUES("indvalues", "Indicators value labels"),
    COUNTDOWN("countdown", "Countdown to bar close"),
    NO_OVERLAP("nooverlap", "No overlapping labels");

    companion object {
        val defaults = setOf(LAST_PRICE, INDICATOR_VALUES, COUNTDOWN, NO_OVERLAP)
    }
}

/** Horizontal lines across the chart. Names are stored in layouts. */
enum class ScaleLine(val js: String, val label: String) {
    LAST_PRICE("last", "Symbol last price line"),
    PREV_CLOSE("prev", "Symbol previous day close price line"),
    HIGH_LOW("hl", "High and low price lines"),
    BID_ASK("bidask", "Bid and ask lines");

    companion object {
        val defaults = setOf(LAST_PRICE)
    }
}

/** Live values the price scale labels and lines point at. */
data class PriceMarks(val prevClose: Double?, val bid: Double?, val ask: Double?)

data class ChartSettings(
    val type: ChartType = ChartType.CANDLES,
    val indicators: Set<Indicator> = emptySet(),
    val scale: ScaleMode = ScaleMode.NORMAL,
    val autoScale: Boolean = true,
    val grid: Boolean = true,
    val invertScale: Boolean = false,
    val scaleLeft: Boolean = false,
    /** Keeps the current price range while scrolling instead of refitting. */
    val lockRatio: Boolean = false,
    /** Indicators don't widen the price range. */
    val priceOnly: Boolean = false,
    val labels: Set<ScaleLabel> = ScaleLabel.defaults,
    val lines: Set<ScaleLine> = ScaleLine.defaults,
    val plusButton: Boolean = false,
)

/** Imperative access to the chart page for one-off commands (tools, zoom, clearing drawings). */
class ChartController {
    internal var bridge: ChartBridge? = null

    fun run(js: String) {
        bridge?.runJs(js)
    }
}

/** Links the chart page's drawing tools to storage and to the screen. */
class DrawingCallbacks(
    /** Saved drawings JSON for a symbol. */
    val load: (symbol: String) -> String,
    val onChanged: (symbol: String, json: String) -> Unit,
    /** The page switched back to the cursor after finishing a drawing. */
    val onToolReset: () -> Unit,
    /** A text-type drawing was placed and needs its label. */
    val onTextRequest: (type: String, id: Int) -> Unit,
    /** JSON of the tapped drawing, or "" when nothing is selected. */
    val onSelect: (json: String) -> Unit,
    /** TP/SL tags were dragged on the order preview; null means not set. */
    val onOrderLevels: (tp: Double?, sl: Double?) -> Unit,
    val onOrderCancel: () -> Unit,
)

/** Asks the chart to show the last [seconds] (0 = all); [id] makes repeated requests distinct. */
data class RangeRequest(val seconds: Long, val id: Int)

/**
 * Candlestick chart rendered by TradingView Lightweight Charts (bundled in assets/chart) inside a WebView.
 * [seriesKey] identifies the symbol + timeframe; changing it resets the chart.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewChart(
    symbol: String,
    seriesKey: Any,
    candles: List<Candle>,
    digits: Int,
    lines: List<ChartLine>,
    settings: ChartSettings,
    range: RangeRequest?,
    controller: ChartController,
    drawings: DrawingCallbacks,
    /** JSON for drawings.js setOrderPreview, or "" for none. */
    orderPreview: String,
    /** Called once the chart page has loaded and can draw. */
    onReady: () -> Unit = {},
    /** A position's line was tapped. */
    onPositionTap: (Long) -> Unit = {},
    /** JSON for drawings.js setPositions: open positions with their TP/SL. */
    positionsJson: String = "[]",
    /** TP/SL were dragged on the chart; null means removed. Not saved until confirmed. */
    onPositionEdit: (id: Long, tp: Double?, sl: Double?) -> Unit = { _, _, _ -> },
    onPositionClose: (Long) -> Unit = {},
    /** Symbol name for the scale label, candle length for the countdown, and the live levels. */
    scaleInfo: Triple<String, Long, PriceMarks> = Triple("", 0L, PriceMarks(null, null, null)),
    /** The price scale was tapped. */
    onScaleTap: () -> Unit = {},
    /** The plus button on the price scale was tapped at [price]. */
    onPlusTap: (Double) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scaleTapCallback by rememberUpdatedState(onScaleTap)
    val plusCallback by rememberUpdatedState(onPlusTap)
    val readyCallback by rememberUpdatedState(onReady)
    val positionCallback by rememberUpdatedState(onPositionTap)
    val editCallback by rememberUpdatedState(onPositionEdit)
    val closeCallback by rememberUpdatedState(onPositionClose)
    val bridge = remember { ChartBridge() }
    val callbacks by rememberUpdatedState(drawings)
    val currentSymbol by rememberUpdatedState(symbol)
    controller.bridge = bridge
    DisposableEffect(Unit) { onDispose { bridge.webView?.destroy() } }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AppColors.SheetBg.toArgb())
                this.settings.javaScriptEnabled = true
                this.settings.allowFileAccess = false
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d("TradingViewChart", "${message.message()} @${message.lineNumber()}")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        bridge.onReady()
                        readyCallback()
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onDrawings(json: String) {
                        post { callbacks.onChanged(currentSymbol, json) }
                    }

                    @JavascriptInterface
                    fun onToolReset() {
                        post { callbacks.onToolReset() }
                    }

                    @JavascriptInterface
                    fun requestText(type: String, id: Int) {
                        post { callbacks.onTextRequest(type, id) }
                    }

                    @JavascriptInterface
                    fun onSelect(json: String) {
                        post { callbacks.onSelect(json) }
                    }

                    @JavascriptInterface
                    fun onOrderLevels(tp: Double, sl: Double) {
                        post { callbacks.onOrderLevels(tp.takeUnless { it.isNaN() }, sl.takeUnless { it.isNaN() }) }
                    }

                    @JavascriptInterface
                    fun onPositionTap(id: String) {
                        id.toLongOrNull()?.let { post { positionCallback(it) } }
                    }

                    @JavascriptInterface
                    fun onPositionEdit(id: String, tp: Double, sl: Double) {
                        id.toLongOrNull()?.let { post { editCallback(it, tp.takeUnless(Double::isNaN), sl.takeUnless(Double::isNaN)) } }
                    }

                    @JavascriptInterface
                    fun onPositionClose(id: String) {
                        id.toLongOrNull()?.let { post { closeCallback(it) } }
                    }

                    @JavascriptInterface
                    fun onOrderCancel() {
                        post { callbacks.onOrderCancel() }
                    }

                    @JavascriptInterface
                    fun onScaleTap() {
                        post { scaleTapCallback() }
                    }

                    @JavascriptInterface
                    fun onPlusTap(price: Double) {
                        post { plusCallback(price) }
                    }
                }, "Android")
                bridge.webView = this
                loadUrl("file:///android_asset/chart/index.html")
            }
        },
        update = {
            bridge.render(symbol, seriesKey, candles, digits, lines, settings, range, orderPreview, callbacks.load)
            bridge.positions(positionsJson)
            bridge.scaleInfo(scaleInfo)
        },
    )
}

/** Sends only what changed to the page: full data on reset, single bars on ticks. */
internal class ChartBridge {
    var webView: WebView? = null
    private var ready = false
    private val queued = ArrayList<String>()

    private var key: Any? = null
    private var sentFirstTime = 0L
    private var sentCount = 0
    private var sentLines = ""
    private var sentSettings: ChartSettings? = null
    private var sentRange: RangeRequest? = null
    private var sentSymbol: String? = null
    private var sentOrder = ""

    fun onReady() {
        ready = true
        queued.forEach { webView?.evaluateJavascript(it, null) }
        queued.clear()
    }

    fun render(
        symbol: String, seriesKey: Any, candles: List<Candle>, digits: Int, lines: List<ChartLine>, settings: ChartSettings,
        range: RangeRequest?, orderPreview: String, loadDrawings: (String) -> String,
    ) {
        if (orderPreview != sentOrder) {
            sentOrder = orderPreview
            run("setOrderPreview(${JSONObject.quote(orderPreview)})")
        }
        if (symbol != sentSymbol) {
            sentSymbol = symbol
            run("setDrawings(${JSONObject.quote(loadDrawings(symbol))})")
        }
        val old = sentSettings
        if (old?.type != settings.type) run("setChartType('${settings.type.js}')")
        if (old?.indicators != settings.indicators) {
            run("setIndicators([${settings.indicators.joinToString(",") { "'${it.js}'" }}])")
        }
        if (old?.scale != settings.scale || old.autoScale != settings.autoScale) {
            run("setScale('${settings.scale.js}', ${settings.autoScale})")
        }
        if (old?.grid != settings.grid) run("setGrid(${settings.grid})")
        if (old == null || old.invertScale != settings.invertScale || old.scaleLeft != settings.scaleLeft || old.priceOnly != settings.priceOnly ||
            old.labels != settings.labels || old.lines != settings.lines || old.plusButton != settings.plusButton
        ) {
            val options = JSONObject()
                .put("invert", settings.invertScale)
                .put("left", settings.scaleLeft)
                .put("priceOnly", settings.priceOnly)
                .put("labels", org.json.JSONArray(settings.labels.map { it.js }))
                .put("lines", org.json.JSONArray(settings.lines.map { it.js }))
                .put("plus", settings.plusButton)
            run("setScaleOptions($options)")
        }
        sentSettings = settings

        if (candles.isEmpty()) return
        val sameSeries = seriesKey == key && candles.first().time == sentFirstTime
        when {
            seriesKey != key -> {
                run("setPrecision($digits)")
                run("setData(${barsJson(candles)}, true)")
            }
            sameSeries && (candles.size == sentCount || candles.size == sentCount + 1) ->
                run("updateBar(${barJson(candles.last())})")
            else -> run("setData(${barsJson(candles)}, false)")
        }
        key = seriesKey
        sentFirstTime = candles.first().time
        sentCount = candles.size

        val linesJson = lines.joinToString(",", "[", "]") { l ->
            JSONObject()
                .put("price", l.price)
                .put("color", String.format(Locale.US, "#%06X", l.color.toArgb() and 0xFFFFFF))
                .put("title", l.label)
                .put("id", l.positionId?.toString() ?: "")
                .toString()
        }
        if (linesJson != sentLines) {
            sentLines = linesJson
            run("setLines($linesJson)")
        }
        if (range != null && range != sentRange) {
            sentRange = range
            run("setRange(${range.seconds})")
        }
    }

    fun runJs(js: String) = run(js)

    private var sentInfo: Triple<String, Long, PriceMarks>? = null

    fun scaleInfo(info: Triple<String, Long, PriceMarks>) {
        if (info == sentInfo) return
        val old = sentInfo
        sentInfo = info
        if (old?.first != info.first || old.second != info.second) {
            val offset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
            run("setBarInfo(${JSONObject.quote(info.first)}, ${info.second}, $offset)")
        }
        val (prev, bid, ask) = info.third
        fun js(v: Double?) = v?.toString() ?: "null"
        run("setMarks(${js(prev)}, ${js(bid)}, ${js(ask)})")
    }

    private var sentPositions = ""

    fun positions(json: String) {
        if (json == sentPositions) return
        sentPositions = json
        run("setPositions(${JSONObject.quote(json)})")
    }

    private fun run(js: String) {
        if (ready) webView?.evaluateJavascript(js, null) else queued += js
    }

    private fun barsJson(candles: List<Candle>) = candles.joinToString(",", "[", "]", transform = ::barJson)

    /** Lightweight Charts shows UTC, so shift times by the local offset to display local time. */
    private fun barJson(c: Candle): String {
        val time = (c.time + TimeZone.getDefault().getOffset(c.time)) / 1000
        return "{\"time\":$time,\"open\":${c.open},\"high\":${c.high},\"low\":${c.low},\"close\":${c.close},\"volume\":${c.volume}}"
    }
}
