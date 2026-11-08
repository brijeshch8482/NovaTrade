package com.novatrade.demo.ui.instrument

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChangeHistory
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CropRotate
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Lens
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Looks
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector

/** One drawing tool; [id] is the tool name the chart page (drawings.js) understands. */
data class ToolDef(val id: String, val name: String, val icon: ImageVector)

data class ToolSection(val title: String, val tools: List<ToolDef>)

/** A left-panel button that opens a list of related tools, like the broker app's toolbar groups. */
data class ToolGroup(val key: String, val sections: List<ToolSection>) {
    val tools: List<ToolDef> get() = sections.flatMap { it.tools }
}

private fun t(id: String, name: String, icon: ImageVector) = ToolDef(id, name, icon)

object ToolCatalog {
    val lines = ToolGroup(
        "lines",
        listOf(
            ToolSection(
                "LINES",
                listOf(
                    t("trend_line", "Trend Line", Icons.Outlined.ShowChart),
                    t("ray", "Ray", Icons.Outlined.CallMade),
                    t("info_line", "Info Line", Icons.Outlined.Info),
                    t("extended_line", "Extended Line", Icons.Outlined.OpenInFull),
                    t("trend_angle", "Trend Angle", Icons.Outlined.Architecture),
                    t("horizontal_line", "Horizontal Line", Icons.Outlined.HorizontalRule),
                    t("horizontal_ray", "Horizontal Ray", Icons.AutoMirrored.Outlined.ArrowForward),
                    t("vertical_line", "Vertical Line", Icons.Outlined.Height),
                    t("cross_line", "Cross Line", Icons.Outlined.Add),
                ),
            ),
            ToolSection(
                "CHANNELS",
                listOf(
                    t("parallel_channel", "Parallel Channel", Icons.Outlined.ViewStream),
                    t("regression_trend", "Regression Trend", Icons.AutoMirrored.Outlined.TrendingUp),
                    t("flat_top_bottom", "Flat Top/Bottom", Icons.Outlined.VerticalAlignTop),
                    t("disjoint_channel", "Disjoint Channel", Icons.Outlined.UnfoldMore),
                ),
            ),
            ToolSection(
                "PITCHFORKS",
                listOf(
                    t("pitchfork", "Pitchfork", Icons.AutoMirrored.Outlined.CallSplit),
                    t("schiff_pitchfork", "Schiff Pitchfork", Icons.AutoMirrored.Outlined.CallSplit),
                    t("modified_schiff_pitchfork", "Modified Schiff Pitchfork", Icons.AutoMirrored.Outlined.CallSplit),
                    t("inside_pitchfork", "Inside Pitchfork", Icons.AutoMirrored.Outlined.CallSplit),
                ),
            ),
        ),
    )

    val fibonacci = ToolGroup(
        "fib",
        listOf(
            ToolSection(
                "FIBONACCI",
                listOf(
                    t("fib_retracement", "Fib Retracement", Icons.Outlined.FormatLineSpacing),
                    t("trend_fib_extension", "Trend-Based Fib Extension", Icons.Outlined.Reorder),
                    t("fib_channel", "Fib Channel", Icons.Outlined.ViewStream),
                    t("fib_time_zone", "Fib Time Zone", Icons.Outlined.ViewWeek),
                    t("fib_speed_fan", "Fib Speed Resistance Fan", Icons.Outlined.FilterList),
                    t("trend_fib_time", "Trend-Based Fib Time", Icons.Outlined.Schedule),
                    t("fib_circles", "Fib Circles", Icons.Outlined.DonutLarge),
                    t("fib_spiral", "Fib Spiral", Icons.Outlined.AllInclusive),
                    t("fib_speed_arcs", "Fib Speed Resistance Arcs", Icons.Outlined.WifiTethering),
                    t("fib_wedge", "Fib Wedge", Icons.Outlined.PieChart),
                ),
            ),
        ),
    )

    val patterns = ToolGroup(
        "patterns",
        listOf(
            ToolSection(
                "PATTERNS",
                listOf(
                    t("xabcd_pattern", "XABCD Pattern", Icons.Outlined.Timeline),
                    t("cypher_pattern", "Cypher Pattern", Icons.Outlined.Timeline),
                    t("head_shoulders", "Head and Shoulders", Icons.Outlined.Landscape),
                    t("abcd_pattern", "ABCD Pattern", Icons.Outlined.Timeline),
                    t("triangle_pattern", "Triangle Pattern", Icons.Outlined.ChangeHistory),
                    t("three_drives", "Three Drives Pattern", Icons.Outlined.Waves),
                ),
            ),
            ToolSection(
                "ELLIOTT WAVES",
                listOf(
                    t("elliott_impulse", "Elliott Impulse Wave (12345)", Icons.Outlined.Insights),
                    t("elliott_correction", "Elliott Correction Wave (ABC)", Icons.Outlined.Insights),
                    t("elliott_triangle", "Elliott Triangle Wave (ABCDE)", Icons.Outlined.Insights),
                    t("elliott_double", "Elliott Double Combo Wave (WXY)", Icons.Outlined.Insights),
                    t("elliott_triple", "Elliott Triple Combo Wave (WXYXZ)", Icons.Outlined.Insights),
                ),
            ),
            ToolSection(
                "CYCLES",
                listOf(
                    t("cyclic_lines", "Cyclic Lines", Icons.Outlined.ViewWeek),
                    t("time_cycles", "Time Cycles", Icons.Outlined.Loop),
                    t("sine_line", "Sine Line", Icons.Outlined.Waves),
                ),
            ),
        ),
    )

    val prediction = ToolGroup(
        "prediction",
        listOf(
            ToolSection(
                "PREDICTION AND MEASUREMENT",
                listOf(
                    t("long_position", "Long Position", Icons.AutoMirrored.Outlined.TrendingUp),
                    t("short_position", "Short Position", Icons.AutoMirrored.Outlined.TrendingDown),
                    t("forecast", "Forecast", Icons.Outlined.Insights),
                    t("price_range", "Price Range", Icons.Outlined.Height),
                    t("date_range", "Date Range", Icons.Outlined.SwapHoriz),
                    t("date_price_range", "Date and Price Range", Icons.Outlined.OpenWith),
                ),
            ),
        ),
    )

    val shapes = ToolGroup(
        "shapes",
        listOf(
            ToolSection(
                "BRUSHES",
                listOf(
                    t("brush", "Brush", Icons.Outlined.Brush),
                    t("highlighter", "Highlighter", Icons.Outlined.Highlight),
                ),
            ),
            ToolSection(
                "ARROWS",
                listOf(
                    t("arrow_marker", "Arrow Marker", Icons.Outlined.NorthEast),
                    t("arrow", "Arrow", Icons.Outlined.CallMade),
                    t("arrow_mark_up", "Arrow Mark Up", Icons.Outlined.ArrowUpward),
                    t("arrow_mark_down", "Arrow Mark Down", Icons.Outlined.ArrowDownward),
                    t("arrow_mark_left", "Arrow Mark Left", Icons.AutoMirrored.Outlined.ArrowBack),
                    t("arrow_mark_right", "Arrow Mark Right", Icons.AutoMirrored.Outlined.ArrowForward),
                ),
            ),
            ToolSection(
                "SHAPES",
                listOf(
                    t("rectangle", "Rectangle", Icons.Outlined.CropSquare),
                    t("rotated_rectangle", "Rotated Rectangle", Icons.Outlined.CropRotate),
                    t("path", "Path", Icons.Outlined.Gesture),
                    t("circle", "Circle", Icons.Outlined.Circle),
                    t("ellipse", "Ellipse", Icons.Outlined.Lens),
                    t("polyline", "Polyline", Icons.Outlined.Category),
                    t("triangle", "Triangle", Icons.Outlined.ChangeHistory),
                    t("arc", "Arc", Icons.Outlined.Looks),
                    t("curve", "Curve", Icons.Outlined.Gesture),
                    t("double_curve", "Double Curve", Icons.Outlined.Waves),
                ),
            ),
        ),
    )

    val text = ToolGroup(
        "text",
        listOf(
            ToolSection(
                "TEXT & NOTES",
                listOf(
                    t("text", "Text", Icons.Outlined.Title),
                    t("anchored_text", "Anchored Text", Icons.Outlined.TextFields),
                    t("note", "Note", Icons.AutoMirrored.Outlined.Note),
                    t("price_note", "Price Note", Icons.Outlined.AttachMoney),
                    t("pin", "Pin", Icons.Outlined.PushPin),
                    t("table", "Table", Icons.Outlined.TableChart),
                    t("callout", "Callout", Icons.Outlined.ChatBubbleOutline),
                    t("comment", "Comment", Icons.AutoMirrored.Outlined.Comment),
                    t("price_label", "Price Label", Icons.Outlined.LocalOffer),
                    t("signpost", "Signpost", Icons.Outlined.Place),
                    t("flag_mark", "Flag Mark", Icons.Outlined.Flag),
                ),
            ),
        ),
    )

    val groups = listOf(lines, fibonacci, patterns, prediction, shapes, text)

    private val byId = groups.flatMap { it.tools }.associateBy { it.id }

    fun find(id: String): ToolDef? = byId[id]

    /** Tools whose label is typed after placing them. */
    val textTools = setOf("text", "anchored_text", "note", "price_note", "pin", "table", "callout", "comment", "signpost")
}
