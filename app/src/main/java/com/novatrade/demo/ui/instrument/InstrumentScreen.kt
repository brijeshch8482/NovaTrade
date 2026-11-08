package com.novatrade.demo.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddAlarm
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novatrade.demo.data.DrawingStore
import com.novatrade.demo.data.MarketRepository
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountMetrics
import com.novatrade.demo.model.Category
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Side
import com.novatrade.demo.model.Timeframe
import com.novatrade.demo.trading.OrderResult
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.accounts.AccountChip
import com.novatrade.demo.ui.accounts.AccountSwitcherSheet
import com.novatrade.demo.ui.common.InstrumentIcon
import com.novatrade.demo.ui.common.toPriceOrNull
import org.json.JSONObject
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.format
import com.novatrade.demo.util.lots
import com.novatrade.demo.util.signedMoney
import com.novatrade.demo.util.usd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Short name as shown on the chart: "BTC" for crypto, "EUR/USD" for pairs. */
val Instrument.displayName: String get() = display

private enum class Sheet { ORDER, PICKER, POSITIONS, INFO, SETTINGS, INDICATORS, ACCOUNTS, MENU, FUNDS, TRANSFER, LEVERAGE, ALERT, DETAILS, CLOSE_ALL }

private val RANGES = listOf("1 hour" to 3_600L, "4 hours" to 14_400L, "1 day" to 86_400L, "5 days" to 432_000L, "1 month" to 2_592_000L, "All" to 0L)

@Composable
fun InstrumentScreen(
    symbol: String,
    timeframe: Timeframe,
    quotes: Map<String, Quote>,
    account: Account,
    accounts: List<Account>,
    metrics: AccountMetrics,
    market: MarketRepository,
    engine: TradingEngine,
    settings: ChartSettings,
    onSettingsChange: (ChartSettings) -> Unit,
    layout: LayoutControls,
    drawingStore: DrawingStore,
    onSymbolChange: (String) -> Unit,
    onTimeframeChange: (Timeframe) -> Unit,
    onRequestDeposit: () -> Unit,
    onRequestWithdraw: () -> Unit,
    alerts: com.novatrade.demo.data.PriceAlerts,
    onBack: () -> Unit,
    onResult: (OrderResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inst = Instruments[symbol]
    val quote = quotes[symbol]
    val candles by remember(symbol, timeframe) { market.candles(symbol, timeframe) }.collectAsStateWithLifecycle()
    val toolPrefs = rememberToolPrefs()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var orderSide by remember { mutableStateOf(Side.BUY) }
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()
    var placing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Pair<String, String>?>(null) }
    var panelIndex by remember(symbol) { mutableStateOf<Int?>(null) }
    /** TP/SL moved on the chart but not confirmed yet. */
    var draft by remember(symbol) { mutableStateOf<PositionDraft?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(4_000)
            toast = null
        }
    }
    var fullscreen by remember { mutableStateOf(false) }
    var showTrades by remember { mutableStateOf(true) }
    var showAsk by remember { mutableStateOf(false) }
    var layoutMenu by remember { mutableStateOf(false) }
    var chartMenu by remember { mutableStateOf<ChartMenu?>(null) }
    var alertPrice by remember { mutableStateOf<Double?>(null) }
    val menuContext = androidx.compose.ui.platform.LocalContext.current
    val menuPrefs = remember { com.novatrade.demo.data.ChartMenuPrefs(menuContext.applicationContext) }
    val controller = remember { ChartController() }
    fun dropDraft() {
        draft?.let { controller.run("clearPositionEdit('${it.id}')") }
        draft = null
    }

    // Drawing tools
    var panelOpen by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf("cursor") }
    var groupCurrent by remember { mutableStateOf(mapOf<String, String>()) }
    var openGroup by remember { mutableStateOf<ToolGroup?>(null) }
    var favorites by remember { mutableStateOf(toolPrefs.favorites()) }
    var toggles by remember { mutableStateOf(DrawingToggles()) }
    var drawingsJson by remember(symbol) { mutableStateOf(drawingStore.load(symbol)) }
    var textRequest by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var pendingEmoji by remember { mutableStateOf<String?>(null) }
    var showEmoji by remember { mutableStateOf(false) }
    var showTree by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SelectedDrawing?>(null) }
    var toolbarAtBottom by remember { mutableStateOf(false) }

    // Inline order panel (opened by Sell/Buy)
    var inlineSide by remember { mutableStateOf<Side?>(null) }
    var volumeText by remember { mutableStateOf("0.01") }
    var inlineTp by remember { mutableStateOf<Double?>(null) }
    var inlineSl by remember { mutableStateOf<Double?>(null) }
    var oneClick by remember { mutableStateOf(toolPrefs.oneClick()) }
    var noMoney by remember { mutableStateOf<Double?>(null) }

    fun selectTool(id: String) {
        tool = id
        controller.run("setTool('$id')")
        ToolCatalog.groups.firstOrNull { g -> g.tools.any { it.id == id } }?.let { g -> groupCurrent = groupCurrent + (g.key to id) }
    }

    val drawingCallbacks = DrawingCallbacks(
        load = drawingStore::load,
        onChanged = { sym, json ->
            drawingStore.save(sym, json)
            if (sym == symbol) drawingsJson = json
        },
        onToolReset = { tool = "cursor" },
        onTextRequest = { type, id ->
            val emoji = pendingEmoji
            if (type == "emoji" && emoji != null) {
                controller.run("setDrawingText($id, ${JSONObject.quote(emoji)})")
            } else {
                textRequest = type to id
            }
        },
        onSelect = { json -> selected = parseSelection(json) },
        onOrderLevels = { tp, sl ->
            inlineTp = tp
            inlineSl = sl
        },
        onOrderCancel = { inlineSide = null },
    )
    var pageReady by remember { mutableStateOf(false) }
    var minLoadDone by remember(symbol, timeframe) { mutableStateOf(false) }
    LaunchedEffect(symbol, timeframe) {
        // Keep the loader up briefly so the chart doesn't flash in half-drawn.
        kotlinx.coroutines.delay(450)
        minLoadDone = true
    }
    val loading = !pageReady || candles == null || !minLoadDone
    var range by remember { mutableStateOf<RangeRequest?>(null) }
    var rangeId by remember { mutableIntStateOf(0) }

    val positions = account.positions.filter { it.symbol == symbol }
    val pending = account.pending.filter { it.symbol == symbol }
    val showPositions = showTrades && !toggles.hidePositions
    // Positions are drawn by the chart page itself, with draggable TP/SL tags.
    val lines = (if (showPositions) pending.map { o -> ChartLine(o.price, AppColors.TextSecondary, "${o.label} ${lots(o.lots)}") } else emptyList()) +
        if (showAsk && quote != null) listOf(ChartLine(quote.ask, AppColors.Sell, "Ask")) else emptyList()
    val positionsJson = if (!showPositions) "[]" else org.json.JSONArray(positions.map { p ->
        val profit = engine.profitOf(p, quote)
        JSONObject()
            .put("id", p.id.toString())
            .put("price", p.openPrice)
            .put("side", if (p.side == Side.BUY) "buy" else "sell")
            .put("lotsLabel", lots(p.lots))
            .put("value", engine.usdPerPoint(inst, p.lots))
            .put("pnl", (if (profit >= 0) "+ " else "- ") + String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(profit)) + " USD")
            .put("tp", p.takeProfit ?: JSONObject.NULL)
            .put("sl", p.stopLoss ?: JSONObject.NULL)
    }).toString()
    val chartSettings = if (toggles.hideIndicators) settings.copy(indicators = emptySet()) else settings

    val volume = volumeText.toPriceOrNull()
    val side = inlineSide
    val orderPrice = if (side != null && quote != null) (if (side == Side.BUY) quote.ask else quote.bid) else null
    val orderPreview = if (side != null && orderPrice != null && volume != null) {
        val perPoint = engine.usdPerPoint(inst, volume)
        JSONObject()
            .put("price", orderPrice).put("side", if (side == Side.BUY) "buy" else "sell")
            .put("lots", volume).put("lotsLabel", lots(volume)).put("value", perPoint)
            .put("tp", inlineTp ?: JSONObject.NULL).put("sl", inlineSl ?: JSONObject.NULL)
            .toString()
    } else ""

    fun openInline(s: Side) {
        val v = volume ?: 0.01
        if (oneClick) {
            val r = engine.open(symbol, s, v, null, null)
            if (r is OrderResult.Success) toast = "Order opened" to "${inst.display} ${s.label} ${lots(v)} lot at ${quote?.let { inst.format(if (s == Side.BUY) it.ask else it.bid) }}" else onResult(r)
        } else {
            inlineTp = null
            inlineSl = null
            inlineSide = s
            if (tool != "cursor") selectTool("cursor")
            controller.run("deselectDrawing()")
            selected = null
        }
    }

    fun confirmInline() {
        val s = inlineSide ?: return
        val v = volume ?: return onResult(OrderResult.Error("Enter a valid volume"))
        val price = orderPrice ?: return
        if (quote?.open == false) return onResult(OrderResult.Error(TradingEngine.MARKET_CLOSED))
        val margin = engine.requiredMargin(symbol, v, price)
        if (margin > metrics.freeMargin) {
            noMoney = v
            return
        }
        placing = true
        uiScope.launch {
            kotlinx.coroutines.delay(500)
            placing = false
            val result = engine.open(symbol, s, v, inlineSl, inlineTp)
            if (result is OrderResult.Success) {
                inlineSide = null
                val at = engine.account.value.positions.lastOrNull()?.openPrice ?: price
                toast = "Order opened" to "${inst.display} ${s.label} ${lots(v)} lot at ${inst.format(at)}"
            } else {
                onResult(result)
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = inlineSide != null) { inlineSide = null }
    androidx.activity.compose.BackHandler(enabled = inlineSide == null && panelIndex != null) {
        dropDraft()
        panelIndex = null
    }
    // The broker app shrinks the header while an order form or a position panel is open.
    val compact = side != null || panelIndex != null

    Column(modifier.fillMaxSize()) {
        if (!fullscreen && !compact) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                AccountCapsule(account, metrics) { sheet = Sheet.ACCOUNTS }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(AppColors.SheetBg),
        ) {
            if (compact) {
                Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    InstrumentIcon(inst, size = 26)
                    Spacer(Modifier.width(8.dp))
                    Text(inst.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (side == null) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    Spacer(Modifier.weight(1f))
                    AccountCapsule(account, metrics) { sheet = Sheet.ACCOUNTS }
                }
            } else {
                // Drag handle: tap to go back, like closing the sheet.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF4A535C)))
                }
            }

            if (!fullscreen && !compact) {
                Row(Modifier.padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { sheet = Sheet.PICKER }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InstrumentIcon(inst, size = 26)
                        Spacer(Modifier.width(8.dp))
                        Text(inst.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Choose instrument")
                    }
                    Spacer(Modifier.weight(1f))
                    HeaderIcon(Icons.Outlined.AddAlarm, "Price alert") { sheet = Sheet.ALERT }
                    HeaderIcon(Icons.Outlined.Fullscreen, "Full screen") { fullscreen = true }
                    HeaderIcon(Icons.Outlined.Settings, "Chart settings") { chartMenu = ChartMenu.SCALE }
                    HeaderIcon(Icons.Filled.MoreVert, "Account menu") { sheet = Sheet.MENU }
                }

                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.Background)
                        .clickable { if (positions.isNotEmpty()) panelIndex = 0 else sheet = Sheet.POSITIONS }
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Open", fontSize = 13.sp)
                    CountBadge(positions.size)
                    Spacer(Modifier.width(18.dp))
                    Text("Pending", fontSize = 13.sp)
                    CountBadge(pending.size)
                    Spacer(Modifier.weight(1f))
                    if (positions.isNotEmpty()) {
                        val total = positions.sumOf { engine.profitOf(it, quote) }
                        Text("${signedMoney(total)} USD", fontSize = 14.sp, color = AppColors.pnl(total))
                        Box(
                            Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).clickable { sheet = Sheet.CLOSE_ALL },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, contentDescription = "Close all", tint = AppColors.Sell, modifier = Modifier.size(20.dp)) }
                    } else {
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize()) {
                    if (!compact) {
                        Toolbar(
                            timeframe = timeframe,
                            settings = settings,
                            showTrades = showTrades,
                            fullscreen = fullscreen,
                            onMenu = { chartMenu = it },
                            onSettingsChange = onSettingsChange,
                            onIndicators = { sheet = Sheet.INDICATORS },
                            showAsk = showAsk,
                            onToggleTrades = { showTrades = !showTrades },
                            onToggleAsk = { showAsk = !showAsk },
                            layout = layout,
                            layoutMenuOpen = layoutMenu,
                            onLayoutMenu = { layoutMenu = !layoutMenu },
                            onExitFullscreen = { fullscreen = false },
                        )
                    }

                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (panelOpen && !compact) {
                            DrawingPanel(
                                activeTool = tool,
                                groupCurrent = groupCurrent,
                                favorites = favorites,
                                toggles = toggles,
                                drawingCount = parseDrawings(drawingsJson).size,
                                indicatorCount = settings.indicators.size,
                                onCursor = { selectTool("cursor") },
                                onOpenGroup = { openGroup = it },
                                onTool = ::selectTool,
                                onEmoji = { showEmoji = true },
                                onZoomIn = { controller.run("zoomIn()") },
                                onMagnet = { mode ->
                                    toggles = toggles.copy(magnet = mode)
                                    controller.run("setMagnet($mode)")
                                },
                                onToggleStay = {
                                    toggles = toggles.copy(stayInDrawing = !toggles.stayInDrawing)
                                    controller.run("setStayInDrawing(${toggles.stayInDrawing})")
                                },
                                onToggleLock = {
                                    toggles = toggles.copy(locked = !toggles.locked)
                                    controller.run("setLockedAll(${toggles.locked})")
                                    onResult(OrderResult.Success(if (toggles.locked) "Drawings locked" else "Drawings unlocked"))
                                },
                                onHide = { action ->
                                    toggles = when (action) {
                                        HideAction.DRAWINGS -> toggles.copy(hideDrawings = !toggles.hideDrawings)
                                        HideAction.INDICATORS -> toggles.copy(hideIndicators = !toggles.hideIndicators)
                                        HideAction.POSITIONS -> toggles.copy(hidePositions = !toggles.hidePositions)
                                        HideAction.ALL -> {
                                            val all = !(toggles.hideDrawings && toggles.hideIndicators && toggles.hidePositions)
                                            toggles.copy(hideDrawings = all, hideIndicators = all, hidePositions = all)
                                        }
                                    }
                                    controller.run("setDrawingsVisible(${!toggles.hideDrawings})")
                                },
                                onRemove = { action ->
                                    val removeDrawings = action != RemoveAction.INDICATORS
                                    val removeIndicators = action != RemoveAction.DRAWINGS
                                    if (removeDrawings) {
                                        if (toggles.locked) onResult(OrderResult.Error("Drawings are locked")) else controller.run("clearDrawings()")
                                    }
                                    if (removeIndicators) onSettingsChange(settings.copy(indicators = emptySet()))
                                },
                                onObjectTree = { showTree = true },
                            )
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Keep the WebView mounted while a new timeframe loads; it ignores empty data.
                                TradingViewChart(
                                    symbol = symbol,
                                    seriesKey = symbol to timeframe,
                                    candles = candles.orEmpty(),
                                    digits = inst.digits,
                                    lines = lines,
                                    settings = chartSettings,
                                    range = range,
                                    controller = controller,
                                    drawings = drawingCallbacks,
                                    orderPreview = orderPreview,
                                    onReady = { pageReady = true },
                                    onPositionTap = { id ->
                                        positions.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let {
                                            if (positions.getOrNull(panelIndex ?: -1)?.id != id) dropDraft()
                                            panelIndex = it
                                        }
                                    },
                                    positionsJson = positionsJson,
                                    onPositionEdit = { id, tp, sl ->
                                        val i = positions.indexOfFirst { it.id == id }
                                        if (i >= 0) {
                                            if (draft != null && draft?.id != id) dropDraft()
                                            draft = PositionDraft(id, tp, sl)
                                            panelIndex = i
                                        }
                                    },
                                    onPositionClose = { id ->
                                        val r = engine.close(id)
                                        if (r is OrderResult.Success) {
                                            if (draft?.id == id) draft = null
                                            if (positions.size <= 1) panelIndex = null
                                            toast = "Order closed" to r.message
                                        } else onResult(r)
                                    },
                                    scaleInfo = Triple(inst.display, timeframe.seconds, PriceMarks(quote?.dayOpen, quote?.bid, quote?.ask)),
                                    onScaleTap = { chartMenu = ChartMenu.SCALE },
                                    onPlusTap = { price ->
                                        alertPrice = price
                                        sheet = Sheet.ALERT
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (loading) ChartLoader()
                                toast?.let { (t, b) ->
                                    ChartToast(t, b, onClose = { toast = null }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                                }
                                if (placing) PlacingPill(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                                if (!compact) {
                                    PanelHandle(
                                        open = panelOpen,
                                        onClick = {
                                            if (panelOpen && tool != "cursor") selectTool("cursor")
                                            panelOpen = !panelOpen
                                        },
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(bottom = 28.dp),
                                    )
                                }
                                selected?.takeIf { side == null }?.let { sel ->
                                    SelectionToolbar(
                                        sel = sel,
                                        onStyle = { color, width, style ->
                                            val patch = JSONObject()
                                            color?.let { patch.put("color", it) }
                                            width?.let { patch.put("width", it) }
                                            style?.let { patch.put("style", it) }
                                            controller.run("styleDrawing(${sel.id}, $patch)")
                                        },
                                        onTemplate = {
                                            controller.run("saveDrawingTemplate(${sel.id})")
                                            onResult(OrderResult.Success("Style saved for new ${ToolCatalog.find(sel.type)?.name ?: "drawings"}"))
                                        },
                                        onEditText = { textRequest = sel.type to sel.id },
                                        onDelete = {
                                            if (sel.locked || toggles.locked) onResult(OrderResult.Error("This drawing is locked")) else controller.run("removeDrawing(${sel.id})")
                                        },
                                        onClone = { controller.run("cloneDrawing(${sel.id})") },
                                        onToggleLock = { controller.run("styleDrawing(${sel.id}, {locked: ${!sel.locked}})") },
                                        onHide = {
                                            controller.run("styleDrawing(${sel.id}, {hidden: true})")
                                            controller.run("deselectDrawing()")
                                        },
                                        onMove = { toolbarAtBottom = !toolbarAtBottom },
                                        modifier = Modifier
                                            .align(if (toolbarAtBottom) Alignment.BottomStart else Alignment.TopStart)
                                            .padding(start = 8.dp, top = 6.dp, bottom = 40.dp),
                                    )
                                }
                            }

                            if (side == null) {
                                ScaleBar(
                                    settings = settings,
                                    compact = panelOpen,
                                    onSettingsChange = onSettingsChange,
                                    onRange = { seconds -> range = RangeRequest(seconds, ++rangeId) },
                                )
                            }
                        }
                    }
                }
                if (layoutMenu) {
                    LayoutMenuOverlay(layout, onDismiss = { layoutMenu = false }, onResult = onResult)
                }
                when (chartMenu) {
                    ChartMenu.TIMEFRAME -> TimeframeMenu(
                        current = timeframe,
                        prefs = menuPrefs,
                        onPick = {
                            chartMenu = null
                            onTimeframeChange(it)
                        },
                        onAddCustom = { chartMenu = ChartMenu.CUSTOM_INTERVAL },
                        onDismiss = { chartMenu = null },
                    )
                    ChartMenu.TYPE -> ChartTypeMenu(
                        current = settings.type,
                        prefs = menuPrefs,
                        onPick = {
                            chartMenu = null
                            onSettingsChange(settings.copy(type = it))
                        },
                        onDismiss = { chartMenu = null },
                    )
                    ChartMenu.SCALE -> ScaleSettingsMenu(
                        settings = settings,
                        ratio = candles.orEmpty().takeLast(100).let { c ->
                            if (c.isEmpty()) "" else String.format(java.util.Locale.US, "%.4f", (c.maxOf { it.high } - c.minOf { it.low }) / c.size)
                        },
                        onChange = onSettingsChange,
                        onMoreSettings = { sheet = Sheet.SETTINGS },
                        onDismiss = { chartMenu = null },
                    )
                    ChartMenu.CUSTOM_INTERVAL -> CustomIntervalPanel(
                        prefs = menuPrefs,
                        onDismiss = { chartMenu = ChartMenu.TIMEFRAME },
                        onAdded = { chartMenu = ChartMenu.TIMEFRAME },
                    )
                    null -> Unit
                }
                openGroup?.let { g ->
                    ToolListOverlay(
                        group = g,
                        favorites = favorites,
                        onPick = { id ->
                            openGroup = null
                            selectTool(id)
                        },
                        onToggleFavorite = { id ->
                            favorites = if (id in favorites) favorites - id else favorites + id
                            toolPrefs.saveFavorites(favorites)
                        },
                        onDismiss = { openGroup = null },
                    )
                }
            }

            if (side != null) {
                InlineOrderPanel(
                    side = side,
                    price = orderPrice?.let(inst::format),
                    volumeText = volumeText,
                    onVolumeChange = { volumeText = it },
                    oneClick = oneClick,
                    onOneClickChange = {
                        oneClick = it
                        toolPrefs.saveOneClick(it)
                        if (it) onResult(OrderResult.Success("One-click trading on: Sell/Buy now open orders instantly"))
                    },
                    fee = if (quote != null && volume != null) feeFor(engine, inst, quote, volume) else null,
                    margin = if (orderPrice != null && volume != null) engine.requiredMargin(symbol, volume, orderPrice) else null,
                    leverage = minOf(account.leverage, inst.maxLeverage),
                    onAdvanced = {
                        orderSide = side
                        inlineSide = null
                        sheet = Sheet.ORDER
                    },
                    onCancel = { inlineSide = null },
                    onConfirm = ::confirmInline,
                )
            } else if (panelIndex != null && positions.isNotEmpty()) {
                val idx = panelIndex!!.coerceIn(0, positions.lastIndex)
                val p = positions[idx]
                val d = draft?.takeIf { it.id == p.id }
                PositionPanel(
                    positions = positions,
                    index = idx,
                    inst = inst,
                    quote = quote,
                    engine = engine,
                    editing = d != null,
                    errors = d?.let { draftErrors(p, it, quote, inst) }.orEmpty(),
                    tp = if (d != null) d.tp else p.takeProfit,
                    sl = if (d != null) d.sl else p.stopLoss,
                    onIndex = {
                        dropDraft()
                        panelIndex = it
                    },
                    onDetails = { sheet = Sheet.DETAILS },
                    onClose = {
                        val r = engine.close(p.id)
                        if (r is OrderResult.Success) {
                            toast = "Order closed" to r.message
                            if (positions.size <= 1) panelIndex = null
                        } else onResult(r)
                    },
                    onCancelEdit = ::dropDraft,
                    onConfirmEdit = {
                        val dd = d ?: return@PositionPanel
                        val r = engine.modify(p.id, dd.sl, dd.tp)
                        if (r is OrderResult.Success) {
                            dropDraft()
                            toast = "Position modified" to buildString {
                                append("${inst.display} ${p.side.label} ${lots(p.lots)} lot at ${inst.format(p.openPrice)}")
                                dd.tp?.let { append("\nTake profit ${inst.format(it)}") }
                                dd.sl?.let { append("\nStop loss ${inst.format(it)}") }
                            }
                        } else onResult(r)
                    },
                    onDismiss = {
                        dropDraft()
                        panelIndex = null
                    },
                )
            } else {
                SellBuyBar(
                    quote = quote.takeUnless { loading },
                    inst = inst,
                    onSell = { openInline(Side.SELL) },
                    onBuy = { openInline(Side.BUY) },
                )
            }
        }
    }

    when (sheet) {
        Sheet.ORDER -> OrderSheet(
            symbol = symbol,
            initialSide = orderSide,
            quote = quote,
            leverage = account.leverage,
            engine = engine,
            onDismiss = { sheet = null },
            onResult = { result ->
                if (result is OrderResult.Success) sheet = null
                onResult(result)
            },
        )
        Sheet.PICKER -> InstrumentPickerSheet(quotes, current = symbol, onDismiss = { sheet = null }) {
            sheet = null
            onSymbolChange(it)
        }
        Sheet.POSITIONS -> SymbolTradesSheet(inst, positions, pending, quote, engine, onResult) { sheet = null }
        Sheet.INFO -> InstrumentInfoSheet(inst, quote, account.leverage) { sheet = null }
        Sheet.MENU -> AccountMenuSheet(onDismiss = { sheet = null }) { item ->
            sheet = when (item) {
                "Deposit" -> null.also { onRequestDeposit() }
                "Transfer" -> Sheet.TRANSFER
                "Withdrawal" -> null.also { onRequestWithdraw() }
                "Account funds" -> Sheet.FUNDS
                "Account settings" -> Sheet.LEVERAGE
                else -> Sheet.ACCOUNTS
            }
        }
        Sheet.FUNDS -> AccountFundsSheet(account, metrics) { sheet = null }
        Sheet.TRANSFER -> TransferSheet(accounts, account.number, engine, onResult) { sheet = null }
        Sheet.LEVERAGE -> InstrumentLeverageSheet(account.leverage, onPick = {
            engine.setLeverage(it)
            sheet = null
        }) { sheet = null }
        Sheet.ALERT -> com.novatrade.demo.ui.trade.CreateAlertSheet(symbol, alerts, quotes, initialPrice = alertPrice) { msg ->
            sheet = null
            alertPrice = null
            msg?.let { onResult(OrderResult.Success(it)) }
        }
        Sheet.DETAILS -> positions.getOrNull(panelIndex ?: 0)?.let { p ->
            PositionDetailsSheet(p, positions, inst, quote, engine, onResult = { r ->
                if (r is OrderResult.Success) toast = "Position modified" to r.message else onResult(r)
            }) { sheet = null }
        } ?: run { sheet = null }
        Sheet.CLOSE_ALL -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { sheet = null },
            containerColor = AppColors.SheetCard,
            title = { Text("Close all ${inst.display} positions?") },
            text = { Text("${positions.size} open position${if (positions.size > 1) "s" else ""} will be closed at the market price.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    sheet = null
                    val r = engine.closeSymbol(symbol)
                    if (r is OrderResult.Success) {
                        panelIndex = null
                        toast = "Orders closed" to r.message
                    } else onResult(r)
                }) { Text("Close all", color = AppColors.Sell) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { sheet = null }) { Text("Cancel", color = AppColors.TextPrimary) } },
        )
        Sheet.SETTINGS -> ChartSettingsSheet(settings, onSettingsChange) { sheet = null }
        Sheet.INDICATORS -> IndicatorsSheet(settings, onSettingsChange) { sheet = null }
        Sheet.ACCOUNTS -> AccountSwitcherSheet(
            accounts = accounts,
            activeNumber = account.number,
            quotes = quotes,
            engine = engine,
            onSelect = {
                engine.switchTo(it)
                sheet = null
            },
            onCreate = { sheet = null },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    textRequest?.let { (type, id) ->
        AnnotationDialog(type, initial = selected?.takeIf { it.id == id }?.text.orEmpty(), onDismiss = { textRequest = null }) { text ->
            if (text.isNotBlank()) controller.run("setDrawingText($id, ${JSONObject.quote(text)})")
            textRequest = null
        }
    }
    if (showEmoji) {
        EmojiSheet(onDismiss = { showEmoji = false }) { e ->
            showEmoji = false
            pendingEmoji = e
            selectTool("emoji")
        }
    }
    if (showTree) {
        ObjectTreeSheet(
            items = parseDrawings(drawingsJson),
            locked = toggles.locked,
            onRemove = { id -> controller.run("removeDrawing($id)") },
            onDismiss = { showTree = false },
        )
    }
    noMoney?.let { v ->
        NotEnoughMoneyDialog(
            volume = v,
            demoEmpty = account.type == com.novatrade.demo.model.AccountType.DEMO && account.balance <= 0.0,
            onDismiss = { noMoney = null },
            onDeposit = {
                noMoney = null
                inlineSide = null
                onRequestDeposit()
            },
        )
    }
}

/** Full-size loading state for the chart: a yellow arc spinning over a grey ring. */
@Composable
private fun ChartLoader() {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.SheetBg)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = AppColors.Accent,
            trackColor = Color(0xFF2A323A),
            strokeWidth = 3.dp,
            modifier = Modifier.size(46.dp),
        )
    }
}

@Composable
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** Small toolbar icon, sized like the chart toolbar on broker apps. */
@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

/** Compact 36dp icon button; the header icons sit closer together than standard 48dp buttons. */
@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(AppColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), fontSize = 11.sp)
    }
}

@Composable
private fun Toolbar(
    timeframe: Timeframe,
    settings: ChartSettings,
    showTrades: Boolean,
    fullscreen: Boolean,
    onMenu: (ChartMenu) -> Unit,
    onSettingsChange: (ChartSettings) -> Unit,
    onIndicators: () -> Unit,
    showAsk: Boolean,
    onToggleTrades: () -> Unit,
    onToggleAsk: () -> Unit,
    layout: LayoutControls,
    layoutMenuOpen: Boolean,
    onLayoutMenu: () -> Unit,
    onExitFullscreen: () -> Unit,
) {
    val iconTint = Color(0xFFD4DAE0)
    // Fixed slot widths put each control where the broker app has it (centres at 19, 66, 112, 150, 201 and 256dp).
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (fullscreen) BarIcon(Icons.Outlined.FullscreenExit, "Exit full screen", onExitFullscreen)
        Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
            Text(
                timeframe.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = iconTint,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onMenu(ChartMenu.TIMEFRAME) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
            if (settings.type == ChartType.CANDLES) {
                GlyphButton(Glyph.CANDLES, iconTint) { onMenu(ChartMenu.TYPE) }
            } else {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).clickable { onMenu(ChartMenu.TYPE) }.padding(6.dp)) {
                    ChartTypeIcon(settings.type, iconTint, Modifier.size(22.dp))
                }
            }
        }
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            GlyphButton(Glyph.FX, if (settings.indicators.isEmpty()) iconTint else AppColors.Buy, onClick = onIndicators)
        }
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            GlyphButton(Glyph.GRID4, if (settings.grid) iconTint else AppColors.TextSecondary) { onSettingsChange(settings.copy(grid = !settings.grid)) }
        }
        Box(Modifier.width(62.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(25.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (showTrades) AppColors.Buy else AppColors.SurfaceHigh)
                    .clickable(onClick = onToggleTrades),
                contentAlignment = Alignment.Center,
            ) {
                ToolGlyph(Glyph.ARROW_UP, Color.White, size = 15)
            }
        }
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            AskBarsIcon(active = showAsk, onClick = onToggleAsk)
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onLayoutMenu)
                .padding(start = 3.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(layout.name, fontSize = 15.sp, lineHeight = 17.sp, maxLines = 1, color = iconTint)
                if (layout.dirty) Text("Save", fontSize = 9.sp, lineHeight = 10.sp, color = AppColors.Accent)
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                if (layoutMenuOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = "Layouts",
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun GlyphButton(glyph: Glyph, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ToolGlyph(glyph, tint, size = when (glyph) { Glyph.CANDLES -> 19; Glyph.FX -> 17; else -> 18 })
    }
}

/** Three small red bars; toggles the ask price line. */
@Composable
private fun AskBarsIcon(active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .background(if (active) AppColors.SurfaceHigh else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(3) { Box(Modifier.size(width = 6.dp, height = 7.5.dp).background(Color(0xFFD93F26))) }
    }
}

@Composable
private fun ScaleBar(settings: ChartSettings, compact: Boolean, onSettingsChange: (ChartSettings) -> Unit, onRange: (Long) -> Unit) {
    var rangeMenu by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { rangeMenu = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Date Range", fontSize = 13.sp, maxLines = 1, softWrap = false)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
                RANGES.forEach { (label, seconds) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        rangeMenu = false
                        onRange(seconds)
                    })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("${clock.format(Date(now))} UTC", fontSize = 13.sp, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(if (compact) 6.dp else 14.dp))
        // With the drawing panel open there is less room, so "%" steps aside like on broker apps.
        if (!compact) {
            ScaleToggle("%", settings.scale == ScaleMode.PERCENT) {
                onSettingsChange(settings.copy(scale = if (settings.scale == ScaleMode.PERCENT) ScaleMode.NORMAL else ScaleMode.PERCENT))
            }
        }
        ScaleToggle("log", settings.scale == ScaleMode.LOG) {
            onSettingsChange(settings.copy(scale = if (settings.scale == ScaleMode.LOG) ScaleMode.NORMAL else ScaleMode.LOG))
        }
        ScaleToggle("auto", settings.autoScale) { onSettingsChange(settings.copy(autoScale = !settings.autoScale)) }
    }
}

@Composable
private fun ScaleToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        color = if (active) AppColors.Buy else AppColors.TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
    )
}

@Composable
private fun SellBuyBar(quote: Quote?, inst: Instrument, onSell: () -> Unit, onBuy: () -> Unit) {
    // Simulated crowd sentiment that leans with today's move.
    val buyers = ((50 + (quote?.changePercent ?: 0.0) * 10).roundToInt()).coerceIn(15, 85)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriceButton("Sell", quote?.let { inst.format(it.bid) }, AppColors.Sell, Modifier.weight(1f), onSell)
                PriceButton("Buy", quote?.let { inst.format(it.ask) }, AppColors.Buy, Modifier.weight(1f), onBuy)
            }
            if (quote != null) {
                Text(
                    inst.format(quote.spread),
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .offset(y = 6.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(AppColors.SheetCard)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SentimentBar(100 - buyers, AppColors.Sell, Modifier.weight(1f))
            SentimentBar(buyers, AppColors.Buy, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PriceButton(label: String, price: String?, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(enabled = price != null, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp)
        // While the chart loads only the label shows, like the broker app.
        if (price != null) Text(price, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
    }
}

@Composable
private fun SentimentBar(percent: Int, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFEDEDF0)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(4.dp)
                    .background(color),
            )
        }
        Text("$percent%", color = color, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

data class PositionDraft(val id: Long, val tp: Double?, val sl: Double?)

/** Problems with a dragged TP/SL, worded like the broker app ("Min …" / "Max …"). */
private fun draftErrors(p: com.novatrade.demo.model.Position, d: PositionDraft, quote: Quote?, inst: Instrument): List<String> {
    quote ?: return emptyList()
    val price = if (p.side == Side.BUY) quote.bid else quote.ask
    val buy = p.side == Side.BUY
    return buildList {
        d.tp?.let { if (if (buy) it <= price else it >= price) add("Take Profit: ${if (buy) "Min" else "Max"} ${inst.format(price)}") }
        d.sl?.let { if (if (buy) it >= price else it <= price) add("Stop Loss: ${if (buy) "Max" else "Min"} ${inst.format(price)}") }
    }
}
