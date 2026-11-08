package com.novatrade.demo.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import kotlin.math.roundToInt
import com.novatrade.demo.data.ChartMenuPrefs
import com.novatrade.demo.model.TfUnit
import com.novatrade.demo.model.Timeframe
import com.novatrade.demo.ui.theme.AppColors

/** Which chart toolbar list is open over the chart. */
enum class ChartMenu { TIMEFRAME, TYPE, CUSTOM_INTERVAL, SCALE }

private val Selected = Color(0xFF148AF9)
private val MenuDivider = Color(0xFF3B434A)
private val StarTint = Color(0xFF5D6470)
private val HeaderText = Color(0xFF9FA4AA)
private val DialogBg = Color(0xFF1D1D1D)
private val DialogLine = Color(0xFF474747)
private val FieldBorder = Color(0xFF4F4F4F)
private val FocusBorder = Color(0xFF2D60F1)

/** Space left above the list, where the dimmed chart shows through. */
private val ListTop = 101.dp

/** Dim layer over the toolbar and chart; tapping it closes the menu. */
@Composable
private fun Scrim(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
}

@Composable
private fun StarButton(starred: Boolean, onClick: () -> Unit) {
    Icon(
        if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
        contentDescription = if (starred) "Remove from favourites" else "Add to favourites",
        tint = if (starred) AppColors.Accent else StarTint,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp).size(22.dp),
    )
}

@Composable
fun TimeframeMenu(
    current: Timeframe,
    prefs: ChartMenuPrefs,
    onPick: (Timeframe) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val custom by prefs.custom.collectAsStateWithLifecycle()
    val starred by prefs.starredIntervals.collectAsStateWithLifecycle()
    val all = (Timeframe.standard + custom).distinct().sortedBy { it.seconds }
    val groups = listOf(
        "MINUTES" to all.filter { it.unit == TfUnit.MINUTE },
        "HOURS" to all.filter { it.unit == TfUnit.HOUR },
        "DAYS" to all.filter { it.unit == TfUnit.DAY || it.unit == TfUnit.WEEK || it.unit == TfUnit.MONTH },
    )
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = ListTop)
                .background(AppColors.SheetBg)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth().height(53.dp).clickable(onClick = onAddCustom).padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(17.dp))
                Text("Add custom interval…", fontSize = 15.sp)
            }
            HorizontalDivider(color = MenuDivider)
            groups.forEach { (title, items) ->
                if (items.isEmpty()) return@forEach
                Text(title, fontSize = 11.sp, letterSpacing = 0.5.sp, color = HeaderText, modifier = Modifier.padding(start = 19.dp, top = 16.dp, bottom = 6.dp))
                items.forEach { tf ->
                    val active = tf == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(if (active) Selected else Color.Transparent)
                            .clickable { onPick(tf) }
                            .padding(start = 19.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tf.title, fontSize = 16.sp, color = if (active) Color.White else AppColors.TextPrimary, modifier = Modifier.weight(1f))
                        if (!active) {
                            if (tf in custom) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Remove interval",
                                    tint = StarTint,
                                    modifier = Modifier.clickable { prefs.removeCustom(tf) }.padding(4.dp).size(20.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                            }
                            StarButton(tf.label in starred) { prefs.toggleInterval(tf) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun ChartTypeMenu(current: ChartType, prefs: ChartMenuPrefs, onPick: (ChartType) -> Unit, onDismiss: () -> Unit) {
    val starred by prefs.starredTypes.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = ListTop)
                .background(AppColors.SheetBg)
                .verticalScroll(rememberScrollState()),
        ) {
            ChartType.entries.groupBy { it.group }.values.forEachIndexed { i, items ->
                if (i > 0) HorizontalDivider(color = MenuDivider, modifier = Modifier.padding(vertical = 6.dp))
                items.forEach { t ->
                    val active = t == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(if (active) Selected else Color.Transparent)
                            .clickable { onPick(t) }
                            .padding(start = 10.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChartTypeIcon(t, if (active) Color.White else AppColors.TextPrimary, Modifier.size(22.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(t.label, fontSize = 16.sp, color = if (active) Color.White else AppColors.TextPrimary, modifier = Modifier.weight(1f))
                        if (!active) StarButton(t.name in starred) { prefs.toggleType(t.name) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private val customUnits = listOf(TfUnit.MINUTE, TfUnit.HOUR, TfUnit.DAY, TfUnit.WEEK, TfUnit.MONTH)

/** Largest count allowed per unit in the custom interval form. */
private fun maxCount(unit: TfUnit) = when (unit) {
    TfUnit.MINUTE -> 1440
    TfUnit.HOUR -> 24
    TfUnit.DAY -> 365
    TfUnit.WEEK -> 52
    TfUnit.MONTH -> 12
}

/** 60 minutes is the same interval as 1 hour, 24 hours the same as 1 day. */
private fun normalized(tf: Timeframe): Timeframe = when {
    tf.unit == TfUnit.MINUTE && tf.count % 60 == 0 -> normalized(Timeframe(tf.count / 60, TfUnit.HOUR))
    tf.unit == TfUnit.HOUR && tf.count % 24 == 0 -> Timeframe(tf.count / 24, TfUnit.DAY)
    else -> tf
}

@Composable
fun CustomIntervalPanel(prefs: ChartMenuPrefs, onDismiss: () -> Unit, onAdded: () -> Unit) {
    val custom by prefs.custom.collectAsStateWithLifecycle()
    var unit by remember { mutableStateOf(TfUnit.MINUTE) }
    var text by remember { mutableStateOf("") }
    var unitMenu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val count = text.toIntOrNull()
    val candidate = count?.takeIf { it > 0 }?.let { Timeframe(it, unit) }
    val existing = (Timeframe.standard + custom).map(::normalized)
    val error = when {
        text.isEmpty() -> null
        candidate == null || candidate.count > maxCount(unit) -> "Interval must be from 1 to ${maxCount(unit)}"
        normalized(candidate) in existing -> "Interval already exists, please use a different value"
        else -> null
    }
    val canAdd = candidate != null && error == null
    val add = {
        if (canAdd) {
            prefs.addCustom(candidate!!)
            onAdded()
        }
    }

    // The panel ends above the order buttons, so lift it only by the part of the keyboard that reaches it.
    val density = LocalDensity.current
    val rootHeight = LocalView.current.rootView.height
    val imeBottom = WindowInsets.ime.getBottom(density)
    var belowPanel by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { belowPanel = rootHeight - it.boundsInWindow().bottom.roundToInt() }
            .padding(bottom = with(density) { (imeBottom - belowPanel).coerceAtLeast(0).toDp() })
            .background(DialogBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 19.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add custom interval", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp).size(26.dp))
        }
        HorizontalDivider(color = DialogLine)
        Column(Modifier.weight(1f).padding(start = 19.dp, end = 52.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            FormRow("Type") {
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(33.dp)
                            .border(1.dp, FieldBorder, RoundedCornerShape(6.dp))
                            .clickable { unitMenu = true }
                            .padding(start = 12.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(unit.plural, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        customUnits.forEach { u ->
                            DropdownMenuItem(text = { Text(u.plural) }, onClick = {
                                unit = u
                                unitMenu = false
                            })
                        }
                    }
                }
            }
            FormRow("Interval") {
                Column {
                    BasicTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(33.dp)
                            .border(
                                if (focused || error != null) 2.dp else 1.dp,
                                when {
                                    error != null -> AppColors.Sell
                                    focused -> FocusBorder
                                    else -> FieldBorder
                                },
                                RoundedCornerShape(6.dp),
                            )
                            .focusRequester(focus)
                            .onFocusChanged { focused = it.isFocused },
                        decorationBox = { inner ->
                            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { inner() }
                        },
                    )
                    error?.let { Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = AppColors.Sell, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
        HorizontalDivider(color = DialogLine)
        Row(
            Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 19.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 72.dp, height = 33.dp)
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", fontSize = 15.sp) }
            Box(
                Modifier
                    .size(width = 52.dp, height = 33.dp)
                    .background(if (canAdd) Color.White else Color(0xFF3C3C3C), RoundedCornerShape(6.dp))
                    .clickable(enabled = canAdd, onClick = add),
                contentAlignment = Alignment.Center,
            ) { Text("Add", fontSize = 15.sp, color = if (canAdd) Color.Black else Color(0xFF6E6E6E)) }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun FormRow(label: String, field: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 15.sp, modifier = Modifier.width(109.dp).padding(top = 6.dp))
        Box(Modifier.weight(1f)) { field() }
    }
}

/** Small line drawing of a chart type for the menu and the toolbar. */
@Composable
fun ChartTypeIcon(type: ChartType, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.width / 22f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val line = Stroke(width = 1.4f * u)
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = 1.4f * u)
        fun poly(vararg pts: Pair<Float, Float>): Path = Path().apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
        }
        fun box(x: Float, y: Float, w: Float, h: Float, filled: Boolean = false) {
            if (filled) drawRect(color, p(x, y), Size(w * u, h * u)) else drawRect(color, p(x, y), Size(w * u, h * u), style = line)
        }
        fun candle(x: Float, top: Float, bottom: Float, wickTop: Float, wickBottom: Float, w: Float = 4f, filled: Boolean = false) {
            seg(x, wickTop, x, top)
            seg(x, bottom, x, wickBottom)
            box(x - w / 2, top, w, bottom - top, filled)
        }
        when (type) {
            ChartType.BARS -> {
                seg(7f, 4f, 7f, 18f); seg(4f, 8f, 7f, 8f); seg(7f, 14f, 10f, 14f)
                seg(15f, 6f, 15f, 16f); seg(12f, 13f, 15f, 13f); seg(15f, 9f, 18f, 9f)
            }
            ChartType.CANDLES -> { candle(7f, 7f, 15f, 3f, 19f); candle(15f, 5f, 12f, 2f, 17f) }
            ChartType.HOLLOW -> { candle(7f, 7f, 15f, 3f, 19f, filled = true); candle(15f, 5f, 12f, 2f, 17f) }
            ChartType.VOLUME_CANDLES -> { candle(6f, 7f, 15f, 3f, 19f, w = 5f); candle(15f, 5f, 13f, 2f, 18f, w = 7f) }
            ChartType.HLC_BARS -> { seg(8f, 4f, 8f, 18f); seg(8f, 12f, 11f, 12f); seg(15f, 6f, 15f, 16f); seg(15f, 9f, 18f, 9f) }
            ChartType.LINE -> drawPath(poly(2f to 16f, 8f to 10f, 12f to 13f, 20f to 5f), color, style = line)
            ChartType.LINE_MARKERS -> {
                drawPath(poly(2f to 16f, 8f to 10f, 13f to 13f, 20f to 6f), color, style = line)
                listOf(8f to 10f, 13f to 13f).forEach { (x, y) -> drawCircle(color, 1.8f * u, p(x, y), style = line) }
            }
            ChartType.STEP -> drawPath(poly(2f to 18f, 7f to 18f, 7f to 11f, 13f to 11f, 13f to 4f, 20f to 4f), color, style = line)
            ChartType.AREA -> {
                val top = poly(2f to 16f, 8f to 9f, 12f to 12f, 20f to 4f)
                drawPath(Path().apply { addPath(top); lineTo(20f * u, 20f * u); lineTo(2f * u, 20f * u); close() }, color.copy(alpha = 0.35f))
                drawPath(top, color, style = line)
            }
            ChartType.HLC_AREA -> {
                drawPath(poly(2f to 12f, 8f to 6f, 13f to 9f, 20f to 3f), color, style = line)
                drawPath(poly(2f to 16f, 8f to 10f, 13f to 13f, 20f to 7f), color, style = line)
                drawPath(poly(2f to 19f, 8f to 14f, 13f to 17f, 20f to 11f), color, style = line)
            }
            ChartType.BASELINE -> {
                drawLine(color, p(2f, 11f), p(20f, 11f), strokeWidth = u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * u, 2f * u)))
                drawPath(poly(2f to 16f, 7f to 7f, 12f to 14f, 20f to 5f), color, style = line)
            }
            ChartType.COLUMNS -> { box(3f, 12f, 4f, 8f); box(9f, 6f, 4f, 14f); box(15f, 9f, 4f, 11f) }
            ChartType.HIGH_LOW -> { box(4f, 6f, 4f, 10f); box(13f, 3f, 4f, 12f); seg(2f, 20f, 20f, 20f) }
            ChartType.HEIKIN_ASHI -> { candle(7f, 8f, 16f, 4f, 19f, filled = true); candle(15f, 4f, 11f, 2f, 15f) }
            ChartType.RENKO -> { box(2f, 14f, 6f, 6f); box(8f, 8f, 6f, 6f); box(14f, 2f, 6f, 6f) }
            ChartType.LINE_BREAK -> { box(3f, 12f, 5f, 7f); box(9f, 6f, 5f, 6f); box(15f, 3f, 4f, 9f, filled = true) }
            ChartType.KAGI -> drawPath(poly(3f to 19f, 3f to 7f, 9f to 7f, 9f to 14f, 15f to 14f, 15f to 3f, 19f to 3f), color, style = Stroke(width = 1.8f * u))
            ChartType.POINT_FIGURE -> {
                seg(3f, 4f, 8f, 9f); seg(8f, 4f, 3f, 9f)
                seg(3f, 11f, 8f, 16f); seg(8f, 11f, 3f, 16f)
                drawCircle(color, 2.6f * u, p(15f, 9f), style = line)
                drawCircle(color, 2.6f * u, p(15f, 15f), style = line)
            }
        }
    }
}

private enum class ScalePage { MAIN, LABELS, LINES }

/** Price scale menu from the settings button or a tap on the scale, with Labels and Lines pages. */
@Composable
fun ScaleSettingsMenu(
    settings: ChartSettings,
    /** Price per bar shown next to "Lock price to bar ratio". */
    ratio: String,
    onChange: (ChartSettings) -> Unit,
    onMoreSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(ScalePage.MAIN) }
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = ListTop)
                .background(AppColors.SheetBg)
                // Each page starts at its top.
                .verticalScroll(key(page) { rememberScrollState() }),
        ) {
            when (page) {
                ScalePage.MAIN -> {
                    MenuRow("Reset price scale", lead = { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(22.dp)) }, tall = true) {
                        onChange(settings.copy(autoScale = true, lockRatio = false))
                        onDismiss()
                    }
                    HorizontalDivider(color = MenuDivider)
                    Spacer(Modifier.height(6.dp))
                    MenuRow("Auto (fits data to screen)", checked = settings.autoScale) {
                        onChange(settings.copy(autoScale = !settings.autoScale, lockRatio = false))
                    }
                    MenuRow("Lock price to bar ratio", checked = settings.lockRatio, trailing = ratio) {
                        val lock = !settings.lockRatio
                        onChange(settings.copy(lockRatio = lock, autoScale = !lock))
                    }
                    MenuRow("Scale price chart only", checked = settings.priceOnly) { onChange(settings.copy(priceOnly = !settings.priceOnly)) }
                    MenuRow("Invert scale", checked = settings.invertScale) { onChange(settings.copy(invertScale = !settings.invertScale)) }
                    GroupDivider()
                    ScaleMode.entries.forEach { m ->
                        MenuRow(m.label, checked = settings.scale == m) { onChange(settings.copy(scale = m)) }
                    }
                    GroupDivider()
                    MenuRow(if (settings.scaleLeft) "Move scale to right" else "Move scale to left") {
                        onChange(settings.copy(scaleLeft = !settings.scaleLeft))
                    }
                    GroupDivider()
                    MenuRow("Labels", arrow = true) { page = ScalePage.LABELS }
                    MenuRow("Lines", arrow = true) { page = ScalePage.LINES }
                    MenuRow("Plus button", checked = settings.plusButton) { onChange(settings.copy(plusButton = !settings.plusButton)) }
                    GroupDivider()
                    MenuRow("More settings…", lead = { SettingsHexagon(AppColors.TextPrimary, Modifier.size(22.dp)) }) {
                        onDismiss()
                        onMoreSettings()
                    }
                }
                ScalePage.LABELS -> {
                    PageHeader("Labels") { page = ScalePage.MAIN }
                    ScaleLabel.entries.forEach { l ->
                        if (l == ScaleLabel.NO_OVERLAP) GroupDivider()
                        MenuRow(l.label, checked = l in settings.labels) {
                            onChange(settings.copy(labels = if (l in settings.labels) settings.labels - l else settings.labels + l))
                        }
                    }
                }
                ScalePage.LINES -> {
                    PageHeader("Lines") { page = ScalePage.MAIN }
                    ScaleLine.entries.forEach { l ->
                        MenuRow(l.label, checked = l in settings.lines) {
                            onChange(settings.copy(lines = if (l in settings.lines) settings.lines - l else settings.lines + l))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** The chart's settings mark: a hexagon with a ring inside. */
@Composable
fun SettingsHexagon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.width / 24f
        val hex = Path().apply {
            moveTo(7f * u, 3.5f * u); lineTo(17f * u, 3.5f * u); lineTo(22f * u, 12f * u)
            lineTo(17f * u, 20.5f * u); lineTo(7f * u, 20.5f * u); lineTo(2f * u, 12f * u); close()
        }
        val stroke = Stroke(width = 1.7f * u, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawPath(hex, color, style = stroke)
        drawCircle(color, 3f * u, Offset(12f * u, 12f * u), style = stroke)
    }
}

@Composable
private fun GroupDivider() = HorizontalDivider(color = MenuDivider, modifier = Modifier.padding(vertical = 6.dp))

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(53.dp).clickable(onClick = onBack).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(7.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = MenuDivider)
    Spacer(Modifier.height(6.dp))
}

/** Menu line with a 22dp slot on the left for a check mark or icon, and an optional value or arrow on the right. */
@Composable
private fun MenuRow(
    text: String,
    checked: Boolean = false,
    lead: (@Composable () -> Unit)? = null,
    trailing: String? = null,
    arrow: Boolean = false,
    tall: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (tall) 53.dp else 40.dp)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                lead != null -> lead()
                checked -> Icon(Icons.Filled.Check, contentDescription = "On", modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, fontSize = 13.sp, color = AppColors.TextSecondary) }
        if (arrow) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}
