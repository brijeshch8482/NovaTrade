package com.novatrade.demo.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.ui.theme.AppColors
import org.json.JSONArray
import org.json.JSONObject

/** State of the panel's on/off buttons. [magnet]: 0 off, 1 weak, 2 strong. */
data class DrawingToggles(
    val magnet: Int = 0,
    val stayInDrawing: Boolean = false,
    val locked: Boolean = false,
    val hideDrawings: Boolean = false,
    val hideIndicators: Boolean = false,
    val hidePositions: Boolean = false,
)

data class DrawingItem(val id: Int, val type: String, val text: String?)

/** The drawing the user tapped on the chart, as reported by the page. */
data class SelectedDrawing(
    val id: Int,
    val type: String,
    val color: String,
    val width: Int,
    val style: Int,
    val locked: Boolean,
    val hasText: Boolean,
    val text: String,
)

fun parseDrawings(json: String): List<DrawingItem> = runCatching {
    val arr = JSONArray(json)
    List(arr.length()) {
        val o = arr.getJSONObject(it)
        DrawingItem(o.getInt("id"), o.getString("type"), o.optString("text").takeIf { t -> t.isNotEmpty() })
    }
}.getOrDefault(emptyList())

fun parseSelection(json: String): SelectedDrawing? = runCatching {
    if (json.isBlank()) return null
    val o = JSONObject(json)
    SelectedDrawing(
        o.getInt("id"), o.getString("type"), o.getString("color"), o.getInt("width"), o.getInt("style"),
        o.getBoolean("locked"), o.getBoolean("hasText"), o.optString("text"),
    )
}.getOrNull()

private val ActiveToolBg = Color(0xFF152A42)

enum class HideAction(val label: String) { DRAWINGS("Hide drawings"), INDICATORS("Hide indicators"), POSITIONS("Hide positions and orders"), ALL("Hide all") }

enum class RemoveAction { DRAWINGS, INDICATORS, ALL }

/** Vertical, scrollable drawing toolbar at the chart's left edge. */
@Composable
fun DrawingPanel(
    activeTool: String,
    groupCurrent: Map<String, String>,
    favorites: List<String>,
    toggles: DrawingToggles,
    drawingCount: Int,
    indicatorCount: Int,
    onCursor: () -> Unit,
    onOpenGroup: (ToolGroup) -> Unit,
    onTool: (String) -> Unit,
    onEmoji: () -> Unit,
    onZoomIn: () -> Unit,
    onMagnet: (Int) -> Unit,
    onToggleStay: () -> Unit,
    onToggleLock: () -> Unit,
    onHide: (HideAction) -> Unit,
    onRemove: (RemoveAction) -> Unit,
    onObjectTree: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(44.dp)
            .fillMaxHeight()
            .background(AppColors.SheetBg)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PanelButton(activeTool == "cursor", onClick = onCursor) { tint -> ToolGlyph(Glyph.CROSS, tint) }
        favorites.mapNotNull(ToolCatalog::find).forEach { f ->
            PanelButton(activeTool == f.id, onClick = { onTool(f.id) }) { tint ->
                Icon(f.icon, f.name, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        if (favorites.isNotEmpty()) HorizontalDivider(color = AppColors.Divider, modifier = Modifier.width(24.dp).padding(vertical = 2.dp))
        ToolCatalog.groups.forEach { g ->
            PanelButton(g.tools.any { it.id == activeTool }, onClick = { onOpenGroup(g) }) { tint -> ToolGlyph(groupGlyph(g.key), tint) }
        }
        PanelButton(activeTool == "emoji", onClick = onEmoji) { tint -> ToolGlyph(Glyph.EMOJI, tint) }
        Spacer(Modifier.height(8.dp))
        PanelButton(activeTool == "date_price_range", onClick = { onTool("date_price_range") }) { tint -> ToolGlyph(Glyph.RULER, tint) }
        PanelButton(false, onClick = onZoomIn) { tint -> ToolGlyph(Glyph.ZOOM, tint) }

        Spacer(Modifier.height(8.dp))
        MenuButton(toggled = toggles.magnet > 0, icon = { tint -> ToolGlyph(Glyph.MAGNET, tint) }) { close ->
            listOf(1 to "Weak magnet", 2 to "Strong magnet", 0 to "Magnet off").forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (toggles.magnet == mode) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        close()
                        onMagnet(mode)
                    },
                )
            }
        }
        PanelButton(false, toggled = toggles.stayInDrawing, onClick = onToggleStay) { tint -> ToolGlyph(Glyph.STAY, tint) }
        PanelButton(false, toggled = toggles.locked, onClick = onToggleLock) { tint ->
            ToolGlyph(if (toggles.locked) Glyph.LOCK else Glyph.UNLOCK, tint)
        }
        val anyHidden = toggles.hideDrawings || toggles.hideIndicators || toggles.hidePositions
        MenuButton(toggled = anyHidden, icon = { tint -> ToolGlyph(if (anyHidden) Glyph.EYE_OFF else Glyph.EYE, tint) }) { close ->
            HideAction.entries.forEach { a ->
                val on = when (a) {
                    HideAction.DRAWINGS -> toggles.hideDrawings
                    HideAction.INDICATORS -> toggles.hideIndicators
                    HideAction.POSITIONS -> toggles.hidePositions
                    HideAction.ALL -> toggles.hideDrawings && toggles.hideIndicators && toggles.hidePositions
                }
                DropdownMenuItem(
                    text = { Text((if (on) "✓ " else "") + a.label) },
                    onClick = {
                        close()
                        onHide(a)
                    },
                )
            }
        }
        MenuButton(toggled = false, icon = { tint -> ToolGlyph(Glyph.TRASH, tint) }) { close ->
            DropdownMenuItem(text = { Text("Remove $drawingCount drawings") }, onClick = { close(); onRemove(RemoveAction.DRAWINGS) })
            DropdownMenuItem(text = { Text("Remove $indicatorCount indicators") }, onClick = { close(); onRemove(RemoveAction.INDICATORS) })
            DropdownMenuItem(text = { Text("Remove drawings & indicators") }, onClick = { close(); onRemove(RemoveAction.ALL) })
        }
        PanelButton(false, onClick = onObjectTree) { tint -> ToolGlyph(Glyph.LAYERS, tint) }
    }
}

@Composable
private fun MenuButton(
    toggled: Boolean,
    icon: @Composable (Color) -> Unit,
    items: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        PanelButton(false, toggled = toggled, onClick = { open = true }, content = icon)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { items { open = false } }
    }
}

@Composable
private fun PanelButton(selected: Boolean, toggled: Boolean = false, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val tint = when {
        selected -> AppColors.Accent
        toggled -> AppColors.Buy
        else -> Color(0xFFD4DAE0)
    }
    Box(
        Modifier
            .size(width = 38.dp, height = 38.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) ActiveToolBg else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}

/** Small tab that opens/closes the panel: "›" when closed, "‹" when open. */
@Composable
fun PanelHandle(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 18.dp, height = 34.dp)
            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            .then(if (open) Modifier else Modifier.border(1.dp, Color(0xFF2E4A66), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)))
            .background(if (open) Color.Transparent else AppColors.SheetBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (open) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
            contentDescription = if (open) "Hide drawing tools" else "Show drawing tools",
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Dims the chart and lists a group's tools in sections, with a favourite star on each. */
@Composable
fun ToolListOverlay(
    group: ToolGroup,
    favorites: List<String>,
    onPick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        LazyColumn(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .background(AppColors.SheetBg),
        ) {
            group.sections.forEach { section ->
                item(key = "h" + section.title) {
                    Text(
                        section.title,
                        color = AppColors.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(section.tools, key = { it.id }) { tool ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(tool.id) }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tool.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(tool.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        val fav = tool.id in favorites
                        IconButton(onClick = { onToggleFavorite(tool.id) }) {
                            Icon(
                                if (fav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "Favorite",
                                tint = if (fav) AppColors.Accent else AppColors.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                item(key = "d" + section.title) { HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

private val PALETTE = listOf(
    "#2962FF", "#F23645", "#089981", "#FF9800", "#9C27B0", "#00BCD4",
    "#FFEB3B", "#FFFFFF", "#787B86", "#E91E63", "#4CAF50", "#3F51B5",
)

/** Floating toolbar for the selected drawing: style, text, delete and more. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionToolbar(
    sel: SelectedDrawing,
    onStyle: (color: String?, width: Int?, style: Int?) -> Unit,
    onTemplate: () -> Unit,
    onEditText: () -> Unit,
    onDelete: () -> Unit,
    onClone: () -> Unit,
    onToggleLock: () -> Unit,
    onHide: () -> Unit,
    onMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var colorMenu by remember { mutableStateOf(false) }
    var widthMenu by remember { mutableStateOf(false) }
    var styleMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val color = runCatching { Color(android.graphics.Color.parseColor(sel.color)) }.getOrDefault(AppColors.Buy)

    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.Toast)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(onClick = onMove) { Icon(Icons.Filled.DragIndicator, "Move toolbar", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp)) }
        BarButton(onClick = onTemplate) { Icon(Icons.Outlined.LibraryAdd, "Save style as default", modifier = Modifier.size(18.dp)) }
        Box {
            BarButton(onClick = { colorMenu = true }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Edit, "Color", modifier = Modifier.size(17.dp))
                    Box(Modifier.size(width = 16.dp, height = 3.dp).background(color))
                }
            }
            DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                FlowRow(Modifier.width(200.dp).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.forEach { hex ->
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(2.dp, if (hex.equals(sel.color, true)) AppColors.TextPrimary else Color.Transparent, CircleShape)
                                .clickable {
                                    colorMenu = false
                                    onStyle(hex, null, null)
                                },
                        )
                    }
                }
            }
        }
        if (sel.hasText) {
            BarButton(onClick = onEditText) { Text("T", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
        Box {
            BarButton(onClick = { widthMenu = true }) { Text("${sel.width}px", fontSize = 12.sp) }
            DropdownMenu(expanded = widthMenu, onDismissRequest = { widthMenu = false }) {
                (1..4).forEach { w ->
                    DropdownMenuItem(text = { Text("${w}px") }, leadingIcon = { Box(Modifier.size(width = 28.dp, height = w.dp).background(AppColors.TextPrimary)) }, onClick = {
                        widthMenu = false
                        onStyle(null, w, null)
                    })
                }
            }
        }
        Box {
            BarButton(onClick = { styleMenu = true }) { LineStyleIcon(sel.style) }
            DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                listOf(0 to "Solid", 1 to "Dashed", 2 to "Dotted").forEach { (st, label) ->
                    DropdownMenuItem(text = { Text(label) }, leadingIcon = { LineStyleIcon(st) }, onClick = {
                        styleMenu = false
                        onStyle(null, null, st)
                    })
                }
            }
        }
        BarButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", modifier = Modifier.size(18.dp)) }
        Box {
            BarButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreHoriz, "More", modifier = Modifier.size(18.dp)) }
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(text = { Text("Clone") }, onClick = { moreMenu = false; onClone() })
                DropdownMenuItem(text = { Text(if (sel.locked) "Unlock" else "Lock") }, onClick = { moreMenu = false; onToggleLock() })
                DropdownMenuItem(text = { Text("Hide") }, onClick = { moreMenu = false; onHide() })
            }
        }
    }
}

@Composable
private fun BarButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(width = 34.dp, height = 34.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun LineStyleIcon(style: Int) {
    Canvas(Modifier.size(width = 20.dp, height = 12.dp)) {
        val y = size.height / 2
        val effect = when (style) {
            1 -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
            2 -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 4f))
            else -> null
        }
        drawLine(Color(0xFFF1F2F4), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), pathEffect = effect)
    }
}

private val EMOJIS = listOf(
    "🚀", "🔥", "👍", "👎", "⚠️", "💰", "🎯", "😀", "😢", "📈", "📉", "⭐",
    "✅", "❌", "💎", "🐂", "🐻", "⏰", "📌", "💡", "❤️", "👀", "🤑", "🏁",
)

/** Grid of emojis for the Icons tool. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmojiSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("EMOJIS", color = AppColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EMOJIS.forEach { e ->
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.SheetCard)
                            .clickable { onPick(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 22.sp) }
                }
            }
        }
    }
}

/** Asks for the label of a text drawing; tables use "|" between cells and one line per row. */
@Composable
fun AnnotationDialog(type: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val isTable = type == "table"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SheetCard,
        title = { Text(ToolCatalog.find(type)?.name ?: "Text", fontSize = 17.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(if (isTable) 300 else 80) },
                    singleLine = !isTable,
                    minLines = if (isTable) 3 else 1,
                    placeholder = { Text(if (isTable) "Price|Level\n78000|Support" else "Text") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.Accent, cursorColor = AppColors.Accent),
                )
                if (isTable) {
                    Text("Use | between cells and a new line for each row.", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text("OK", color = AppColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.TextSecondary) } },
    )
}

private fun DrawingItem.title(): String {
    val name = ToolCatalog.find(type)?.name ?: if (type == "emoji") "Emoji" else type
    return if (text != null) "$name: $text" else name
}

/** Lists the drawings on the chart so single ones can be removed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectTreeSheet(items: List<DrawingItem>, locked: Boolean, onRemove: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text("Object tree", fontSize = 17.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (items.isEmpty()) {
                Text("No drawings on this chart", color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(items, key = { it.id }) { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(d.title(), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(d.id) }, enabled = !locked) {
                            Icon(Icons.Filled.Close, "Remove", tint = if (locked) AppColors.Divider else AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    }
}
