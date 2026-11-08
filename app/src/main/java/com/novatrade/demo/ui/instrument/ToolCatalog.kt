package com.novatrade.demo.ui.instrument

/** One drawing tool; [id] is the tool name the chart page (drawings.js) understands. */
data class ToolDef(val id: String, val name: String)

data class ToolSection(val title: String, val tools: List<ToolDef>)

/** A left-panel button that opens a list of related tools, like the broker app's toolbar groups. */
data class ToolGroup(val key: String, val sections: List<ToolSection>) {
    val tools: List<ToolDef> get() = sections.flatMap { it.tools }
}

private fun t(id: String, name: String) = ToolDef(id, name)

object ToolCatalog {
    val lines = ToolGroup(
        "lines",
        listOf(
            ToolSection(
                "LINES",
                listOf(
                    t("trend_line", "Trend Line"),
                    t("ray", "Ray"),
                    t("info_line", "Info Line"),
                    t("extended_line", "Extended Line"),
                    t("trend_angle", "Trend Angle"),
                    t("horizontal_line", "Horizontal Line"),
                    t("horizontal_ray", "Horizontal Ray"),
                    t("vertical_line", "Vertical Line"),
                    t("cross_line", "Cross Line"),
                ),
            ),
            ToolSection(
                "CHANNELS",
                listOf(
                    t("parallel_channel", "Parallel Channel"),
                    t("regression_trend", "Regression Trend"),
                    t("flat_top_bottom", "Flat Top/Bottom"),
                    t("disjoint_channel", "Disjoint Channel"),
                ),
            ),
            ToolSection(
                "PITCHFORKS",
                listOf(
                    t("pitchfork", "Pitchfork"),
                    t("schiff_pitchfork", "Schiff Pitchfork"),
                    t("modified_schiff_pitchfork", "Modified Schiff Pitchfork"),
                    t("inside_pitchfork", "Inside Pitchfork"),
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
                    t("fib_retracement", "Fib Retracement"),
                    t("trend_fib_extension", "Trend-Based Fib Extension"),
                    t("fib_channel", "Fib Channel"),
                    t("fib_time_zone", "Fib Time Zone"),
                    t("fib_speed_fan", "Fib Speed Resistance Fan"),
                    t("trend_fib_time", "Trend-Based Fib Time"),
                    t("fib_circles", "Fib Circles"),
                    t("fib_spiral", "Fib Spiral"),
                    t("fib_speed_arcs", "Fib Speed Resistance Arcs"),
                    t("fib_wedge", "Fib Wedge"),
                    t("pitchfan", "Pitchfan"),
                ),
            ),
            ToolSection(
                "GANN",
                listOf(
                    t("gann_box", "Gann Box"),
                    t("gann_square_fixed", "Gann Square Fixed"),
                    t("gann_square", "Gann Square"),
                    t("gann_fan", "Gann Fan"),
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
                    t("xabcd_pattern", "XABCD Pattern"),
                    t("cypher_pattern", "Cypher Pattern"),
                    t("head_shoulders", "Head and Shoulders"),
                    t("abcd_pattern", "ABCD Pattern"),
                    t("triangle_pattern", "Triangle Pattern"),
                    t("three_drives", "Three Drives Pattern"),
                ),
            ),
            ToolSection(
                "ELLIOTT WAVES",
                listOf(
                    t("elliott_impulse", "Elliott Impulse Wave (12345)"),
                    t("elliott_correction", "Elliott Correction Wave (ABC)"),
                    t("elliott_triangle", "Elliott Triangle Wave (ABCDE)"),
                    t("elliott_double", "Elliott Double Combo Wave (WXY)"),
                    t("elliott_triple", "Elliott Triple Combo Wave (WXYXZ)"),
                ),
            ),
            ToolSection(
                "CYCLES",
                listOf(
                    t("cyclic_lines", "Cyclic Lines"),
                    t("time_cycles", "Time Cycles"),
                    t("sine_line", "Sine Line"),
                ),
            ),
        ),
    )

    val prediction = ToolGroup(
        "prediction",
        listOf(
            ToolSection(
                "PROJECTION",
                listOf(
                    t("long_position", "Long Position"),
                    t("short_position", "Short Position"),
                    t("forecast", "Forecast"),
                    t("bars_pattern", "Bars Pattern"),
                    t("ghost_feed", "Ghost Feed"),
                    t("projection", "Projection"),
                ),
            ),
            ToolSection(
                "VOLUME-BASED",
                listOf(
                    t("anchored_vwap", "Anchored VWAP"),
                    t("fixed_range_volume_profile", "Fixed Range Volume Profile"),
                ),
            ),
            ToolSection(
                "MEASURER",
                listOf(
                    t("price_range", "Price Range"),
                    t("date_range", "Date Range"),
                    t("date_price_range", "Date and Price Range"),
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
                    t("brush", "Brush"),
                    t("highlighter", "Highlighter"),
                ),
            ),
            ToolSection(
                "ARROWS",
                listOf(
                    t("arrow_marker", "Arrow Marker"),
                    t("arrow", "Arrow"),
                    t("arrow_mark_up", "Arrow Mark Up"),
                    t("arrow_mark_down", "Arrow Mark Down"),
                    t("arrow_mark_left", "Arrow Mark Left"),
                    t("arrow_mark_right", "Arrow Mark Right"),
                ),
            ),
            ToolSection(
                "SHAPES",
                listOf(
                    t("rectangle", "Rectangle"),
                    t("rotated_rectangle", "Rotated Rectangle"),
                    t("path", "Path"),
                    t("circle", "Circle"),
                    t("ellipse", "Ellipse"),
                    t("polyline", "Polyline"),
                    t("triangle", "Triangle"),
                    t("arc", "Arc"),
                    t("curve", "Curve"),
                    t("double_curve", "Double Curve"),
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
                    t("text", "Text"),
                    t("anchored_text", "Anchored Text"),
                    t("note", "Note"),
                    t("price_note", "Price Note"),
                    t("pin", "Pin"),
                    t("table", "Table"),
                    t("callout", "Callout"),
                    t("comment", "Comment"),
                    t("price_label", "Price Label"),
                    t("signpost", "Signpost"),
                    t("flag_mark", "Flag Mark"),
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
