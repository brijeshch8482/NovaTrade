package com.novatrade.demo.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Thin line drawing of what a drawing tool puts on the chart (a trend line with its two anchors,
 * Fibonacci levels, a pitchfork's prongs...), on a 24-unit grid. Anchors are small hollow rings.
 */
@Composable
fun ToolIcon(id: String, tint: Color, size: Int = 20) {
    Canvas(Modifier.size(size.dp)) { ToolDrawer(this, tint).draw(id) }
}

private class ToolDrawer(private val scope: DrawScope, private val c: Color) {
    private val u = scope.size.width / 24f
    private val stroke = Stroke(1.3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val dashed = Stroke(1.1f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6f * u, 1.6f * u)))

    private fun o(x: Float, y: Float) = Offset(x * u, y * u)

    /** Straight line. */
    fun l(x1: Float, y1: Float, x2: Float, y2: Float) = scope.drawLine(c, o(x1, y1), o(x2, y2), 1.3f * u, StrokeCap.Round)

    fun dl(x1: Float, y1: Float, x2: Float, y2: Float) = scope.drawPath(Path().apply { moveTo(x1 * u, y1 * u); lineTo(x2 * u, y2 * u) }, c, style = dashed)

    /** Open polyline through the points (x, y, x, y, ...). */
    fun p(vararg v: Float, close: Boolean = false) = scope.drawPath(path(v, close), c, style = stroke)

    private fun path(v: FloatArray, close: Boolean) = Path().apply {
        moveTo(v[0] * u, v[1] * u)
        var i = 2
        while (i < v.size) {
            lineTo(v[i] * u, v[i + 1] * u)
            i += 2
        }
        if (close) close()
    }

    /** Anchor: a small hollow ring. */
    fun a(x: Float, y: Float) = scope.drawCircle(c, 1.7f * u, o(x, y), style = Stroke(1.1f * u))

    fun dot(x: Float, y: Float) = scope.drawCircle(c, 1.1f * u, o(x, y))

    fun r(x: Float, y: Float, w: Float, h: Float) = scope.drawRect(c, o(x, y), Size(w * u, h * u), style = stroke)

    fun circle(x: Float, y: Float, rad: Float) = scope.drawCircle(c, rad * u, o(x, y), style = stroke)

    fun oval(x: Float, y: Float, rx: Float, ry: Float) = scope.drawOval(c, o(x - rx, y - ry), Size(2 * rx * u, 2 * ry * u), style = stroke)

    /** Arc of the circle at (x, y), angles in degrees clockwise from 3 o'clock. */
    fun arc(x: Float, y: Float, rad: Float, start: Float, sweep: Float) =
        scope.drawArc(c, start, sweep, false, o(x - rad, y - rad), Size(2 * rad * u, 2 * rad * u), style = stroke)

    fun curve(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float) =
        scope.drawPath(Path().apply { moveTo(x1 * u, y1 * u); quadraticTo(cx * u, cy * u, x2 * u, y2 * u) }, c, style = stroke)

    fun cubic(x1: Float, y1: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float, x2: Float, y2: Float) =
        scope.drawPath(Path().apply { moveTo(x1 * u, y1 * u); cubicTo(c1x * u, c1y * u, c2x * u, c2y * u, x2 * u, y2 * u) }, c, style = stroke)

    /** Arrow head at (x, y) pointing along (dx, dy). */
    fun head(x: Float, y: Float, dx: Float, dy: Float, len: Float = 4f) {
        val n = kotlin.math.sqrt(dx * dx + dy * dy)
        val ux = dx / n
        val uy = dy / n
        p(x - len * (ux + uy * 0.6f), y - len * (uy - ux * 0.6f), x, y, x - len * (ux - uy * 0.6f), y - len * (uy + ux * 0.6f))
    }

    /** Block arrow outline pointing [dir]: 0 up, 1 right, 2 down, 3 left. */
    fun block(dir: Int) {
        val up = floatArrayOf(12f, 3f, 20f, 11f, 15.5f, 11f, 15.5f, 21f, 8.5f, 21f, 8.5f, 11f, 4f, 11f)
        val pts = FloatArray(up.size)
        for (i in up.indices step 2) {
            val x = up[i] - 12f
            val y = up[i + 1] - 12f
            val (rx, ry) = when (dir) {
                1 -> -y to x
                2 -> -x to -y
                3 -> y to -x
                else -> x to y
            }
            pts[i] = rx + 12f
            pts[i + 1] = ry + 12f
        }
        p(*pts, close = true)
    }

    fun draw(id: String) {
        when (id) {
            // Lines
            "trend_line" -> { l(5f, 19f, 19f, 5f); a(5f, 19f); a(19f, 5f) }
            "ray" -> { l(4f, 20f, 22f, 2f); a(4f, 20f); a(11f, 13f) }
            "info_line" -> { l(4f, 19f, 16f, 7f); a(4f, 19f); a(16f, 7f); r(13f, 14f, 8f, 6f); l(15f, 17f, 19f, 17f) }
            "extended_line" -> { l(2f, 22f, 22f, 2f); a(8f, 16f); a(16f, 8f) }
            "trend_angle" -> { l(4f, 18f, 19f, 6f); l(4f, 18f, 20f, 18f); arc(4f, 18f, 7f, -38f, 38f); a(4f, 18f); a(19f, 6f) }
            "horizontal_line" -> { l(2f, 12f, 22f, 12f); a(12f, 12f) }
            "horizontal_ray" -> { l(9f, 12f, 22f, 12f); a(7f, 12f) }
            "vertical_line" -> { l(12f, 2f, 12f, 22f); a(12f, 12f) }
            "cross_line" -> { l(2f, 12f, 22f, 12f); l(12f, 2f, 12f, 22f); a(12f, 12f) }
            // Channels
            "parallel_channel" -> { l(3f, 14f, 15f, 4f); l(9f, 21f, 21f, 11f); dl(6f, 17.5f, 18f, 7.5f); a(3f, 14f); a(15f, 4f); a(21f, 11f) }
            "regression_trend" -> { l(3f, 12f, 21f, 4f); l(3f, 20f, 21f, 12f); dl(3f, 16f, 21f, 8f); a(3f, 16f); a(21f, 8f) }
            "flat_top_bottom" -> { l(3f, 6f, 21f, 6f); l(3f, 20f, 21f, 11f); a(3f, 20f); a(21f, 11f); a(21f, 6f) }
            "disjoint_channel" -> { l(3f, 5f, 21f, 10f); l(3f, 20f, 21f, 14f); a(3f, 5f); a(21f, 10f); a(3f, 20f) }
            // Pitchforks: a handle to the median and three prongs.
            "pitchfork" -> { l(5f, 12f, 13f, 12f); l(13f, 6f, 13f, 18f); l(13f, 6f, 22f, 6f); l(13f, 12f, 22f, 12f); l(13f, 18f, 22f, 18f); a(3.5f, 12f) }
            "schiff_pitchfork" -> { l(5f, 17f, 13f, 12f); l(13f, 6f, 13f, 18f); l(13f, 6f, 22f, 4f); l(13f, 12f, 22f, 10f); l(13f, 18f, 22f, 16f); a(3.5f, 18f) }
            "modified_schiff_pitchfork" -> { l(5f, 15f, 13f, 12f); l(13f, 6f, 13f, 18f); l(13f, 6f, 22f, 5f); l(13f, 12f, 22f, 11f); l(13f, 18f, 22f, 17f); a(3.5f, 15.5f) }
            "inside_pitchfork" -> { l(5f, 12f, 13f, 12f); l(13f, 6f, 13f, 18f); l(13f, 6f, 22f, 9f); l(13f, 12f, 22f, 12f); l(13f, 18f, 22f, 15f); a(3.5f, 12f) }
            // Fibonacci
            "fib_retracement" -> { listOf(4f, 8.5f, 12f, 15.5f, 20f).forEach { l(6f, it, 22f, it) }; dl(4f, 20f, 20f, 4f); a(4f, 20f); a(20f, 4f) }
            "trend_fib_extension" -> { p(2f, 20f, 7f, 9f, 11f, 15f); listOf(4f, 9f, 14f, 19f).forEach { l(13f, it, 22f, it) }; a(2f, 20f); a(7f, 9f); a(11f, 15f) }
            "fib_channel" -> { l(2f, 9f, 14f, 3f); l(4f, 15f, 20f, 7f); l(6f, 21f, 22f, 13f); a(2f, 9f); a(14f, 3f); a(6f, 21f) }
            "fib_time_zone" -> { listOf(4f, 6f, 9f, 13.5f, 20.5f).forEach { l(it, 4f, it, 20f) }; a(4f, 20f) }
            "fib_speed_fan" -> { l(3f, 21f, 21f, 3f); l(3f, 21f, 21f, 10f); l(3f, 21f, 10f, 3f); dl(3f, 3f, 21f, 3f); dl(21f, 3f, 21f, 21f); a(3f, 21f); a(21f, 3f) }
            "trend_fib_time" -> { p(2f, 18f, 7f, 8f, 11f, 13f); listOf(13f, 16f, 21f).forEach { l(it, 3f, it, 21f) }; a(2f, 18f); a(7f, 8f); a(11f, 13f) }
            "fib_circles" -> { circle(12f, 12f, 3.5f); circle(12f, 12f, 6.5f); circle(12f, 12f, 9.5f); dot(12f, 12f) }
            "fib_spiral" -> { arc(13f, 12f, 2f, 0f, 180f); arc(12f, 12f, 3f, 180f, 180f); arc(13.5f, 12f, 4.5f, 0f, 180f); arc(11f, 12f, 7f, 180f, 180f); arc(14.5f, 12f, 10.5f, 0f, 120f) }
            "fib_speed_arcs" -> { arc(12f, 19f, 4f, 180f, 180f); arc(12f, 19f, 7f, 180f, 180f); arc(12f, 19f, 10f, 180f, 180f); l(12f, 19f, 19f, 9f); a(12f, 19f) }
            "fib_wedge" -> { l(4f, 19f, 21f, 5f); l(4f, 19f, 22f, 15f); arc(4f, 19f, 8f, -38f, 30f); arc(4f, 19f, 13f, -38f, 30f); a(4f, 19f) }
            // Patterns
            "xabcd_pattern" -> { p(2f, 17f, 7f, 6f, 11f, 14f, 16f, 7f, 22f, 18f); dl(2f, 17f, 11f, 14f); dl(11f, 14f, 22f, 18f) }
            "cypher_pattern" -> { p(2f, 19f, 7f, 8f, 11f, 14f, 17f, 4f, 22f, 16f); dl(2f, 19f, 11f, 14f); dl(7f, 8f, 17f, 4f) }
            "head_shoulders" -> { p(2f, 18f, 6f, 11f, 9f, 15f, 12f, 4f, 15f, 15f, 18f, 11f, 22f, 18f); dl(2f, 15f, 22f, 15f) }
            "abcd_pattern" -> { p(3f, 19f, 9f, 6f, 14f, 13f, 21f, 4f); dl(3f, 19f, 14f, 13f); a(3f, 19f); a(21f, 4f) }
            "triangle_pattern" -> { l(2f, 4f, 22f, 11f); l(2f, 20f, 22f, 13f); p(3f, 5f, 7f, 18f, 11f, 8f, 15f, 15f, 19f, 11f) }
            "three_drives" -> { p(2f, 21f, 6f, 14f, 9f, 17f, 13f, 9f, 16f, 12f, 21f, 3f) }
            // Elliott waves: the wave shape with its turning points.
            "elliott_impulse" -> wave(2f, 20f, 6f, 12f, 9f, 16f, 15f, 5f, 18f, 10f, 22f, 3f)
            "elliott_correction" -> wave(3f, 4f, 10f, 16f, 14f, 10f, 21f, 20f)
            "elliott_triangle" -> wave(2f, 4f, 6f, 19f, 10f, 8f, 14f, 15f, 18f, 10f, 22f, 13f)
            "elliott_double" -> wave(2f, 5f, 8f, 15f, 12f, 10f, 17f, 19f, 22f, 15f)
            "elliott_triple" -> wave(2f, 4f, 6f, 12f, 9f, 9f, 13f, 16f, 16f, 12f, 21f, 20f)
            // Cycles
            "cyclic_lines" -> { listOf(4f, 9f, 14f, 19f).forEach { l(it, 3f, it, 21f) }; a(4f, 12f); a(9f, 12f) }
            "time_cycles" -> { l(2f, 18f, 22f, 18f); arc(7f, 18f, 5f, 180f, 180f); arc(17f, 18f, 5f, 180f, 180f); a(2f, 18f) }
            "sine_line" -> { cubic(2f, 12f, 5f, 2f, 8f, 2f, 12f, 12f); cubic(12f, 12f, 16f, 22f, 19f, 22f, 22f, 12f); a(2f, 12f); a(7f, 4.5f) }
            // Prediction and measurement
            "long_position" -> { r(4f, 3f, 16f, 9f); r(4f, 12f, 16f, 9f); l(12f, 10f, 12f, 5f); head(12f, 5f, 0f, -1f, 3f) }
            "short_position" -> { r(4f, 3f, 16f, 9f); r(4f, 12f, 16f, 9f); l(12f, 14f, 12f, 19f); head(12f, 19f, 0f, 1f, 3f) }
            "forecast" -> { l(3f, 19f, 11f, 12f); dl(11f, 12f, 21f, 5f); a(3f, 19f); a(11f, 12f); a(21f, 5f) }
            "price_range" -> { l(6f, 3f, 18f, 3f); l(6f, 21f, 18f, 21f); l(12f, 5f, 12f, 19f); head(12f, 5f, 0f, -1f, 3f); head(12f, 19f, 0f, 1f, 3f) }
            "date_range" -> { l(3f, 6f, 3f, 18f); l(21f, 6f, 21f, 18f); l(5f, 12f, 19f, 12f); head(5f, 12f, -1f, 0f, 3f); head(19f, 12f, 1f, 0f, 3f) }
            "date_price_range" -> { r(3f, 3f, 18f, 18f); l(6f, 12f, 18f, 12f); head(18f, 12f, 1f, 0f, 3f); l(12f, 18f, 12f, 6f); head(12f, 6f, 0f, -1f, 3f) }
            // Brushes
            "brush" -> { cubic(3f, 18f, 7f, 8f, 12f, 21f, 20f, 6f); dot(20f, 6f) }
            "highlighter" -> { p(14f, 3f, 21f, 10f, 12f, 19f, 5f, 12f, close = true); l(5f, 12f, 3f, 21f); l(3f, 21f, 12f, 19f) }
            // Arrows
            "arrow_marker" -> { p(4f, 20f, 15f, 9f); p(10f, 5f, 20f, 4f, 19f, 14f, close = true) }
            "arrow" -> { l(4f, 20f, 20f, 4f); head(20f, 4f, 1f, -1f) }
            "arrow_mark_up" -> block(0)
            "arrow_mark_right" -> block(1)
            "arrow_mark_down" -> block(2)
            "arrow_mark_left" -> block(3)
            // Shapes
            "rectangle" -> { r(4f, 6f, 16f, 12f); a(4f, 6f); a(20f, 18f) }
            "rotated_rectangle" -> { p(4f, 13f, 12f, 4f, 20f, 11f, 12f, 20f, close = true); a(4f, 13f); a(12f, 4f) }
            "path" -> { p(3f, 19f, 8f, 8f, 14f, 15f, 20f, 5f); head(20f, 5f, 0.5f, -1f, 3.5f); a(3f, 19f) }
            "circle" -> { circle(12f, 12f, 8.5f); a(12f, 12f); a(20.5f, 12f) }
            "ellipse" -> { oval(12f, 12f, 9.5f, 6f); a(12f, 12f) }
            "polyline" -> { p(4f, 16f, 8f, 5f, 19f, 8f, 20f, 18f, 11f, 20f, close = true); a(8f, 5f); a(20f, 18f) }
            "triangle" -> { p(12f, 4f, 21f, 19f, 3f, 19f, close = true); a(12f, 4f); a(21f, 19f); a(3f, 19f) }
            "arc" -> { arc(12f, 17f, 9f, 180f, 180f); a(3f, 17f); a(21f, 17f) }
            "curve" -> { curve(3f, 18f, 12f, -1f, 21f, 18f); a(3f, 18f); a(21f, 18f) }
            "double_curve" -> { cubic(3f, 17f, 8f, 1f, 16f, 23f, 21f, 7f); a(3f, 17f); a(21f, 7f) }
            // Text and notes
            "text" -> { l(6f, 5f, 18f, 5f); l(12f, 5f, 12f, 19f); l(10f, 19f, 14f, 19f); l(6f, 5f, 6f, 7f); l(18f, 5f, 18f, 7f) }
            "anchored_text" -> { l(4f, 5f, 14f, 5f); l(9f, 5f, 9f, 17f); l(7f, 17f, 11f, 17f); a(18f, 19f) }
            "note" -> { p(4f, 4f, 20f, 4f, 20f, 14f, 14f, 20f, 4f, 20f, close = true); p(14f, 20f, 14f, 14f, 20f, 14f); l(7f, 9f, 16f, 9f) }
            "price_note" -> { r(3f, 4f, 13f, 9f); l(6f, 8.5f, 13f, 8.5f); l(9f, 13f, 18f, 20f); a(19f, 20.5f) }
            "pin" -> { scope.drawPath(Path().apply { moveTo(12f * u, 21f * u); cubicTo(5f * u, 13f * u, 5f * u, 3f * u, 12f * u, 3f * u); cubicTo(19f * u, 3f * u, 19f * u, 13f * u, 12f * u, 21f * u); close() }, c, style = stroke); circle(12f, 9.5f, 2.5f) }
            "table" -> { r(3f, 5f, 18f, 14f); l(3f, 9.5f, 21f, 9.5f); l(3f, 14f, 21f, 14f); l(9f, 5f, 9f, 19f); l(15f, 5f, 15f, 19f) }
            "callout" -> { p(3f, 4f, 21f, 4f, 21f, 15f, 11f, 15f, 6f, 20f, 7f, 15f, 3f, 15f, close = true) }
            "comment" -> { scope.drawRoundRect(c, o(3f, 4f), Size(18f * u, 12f * u), androidx.compose.ui.geometry.CornerRadius(4f * u), style = stroke); p(8f, 16f, 7f, 21f, 12f, 16f); l(7f, 9f, 17f, 9f); l(7f, 12f, 14f, 12f) }
            "price_label" -> { p(3f, 7f, 15f, 7f, 21f, 12f, 15f, 17f, 3f, 17f, close = true); circle(15.5f, 12f, 1.3f) }
            "signpost" -> { l(12f, 3f, 12f, 21f); p(5f, 6f, 17f, 6f, 20f, 9f, 17f, 12f, 5f, 12f, close = true); l(8f, 21f, 16f, 21f) }
            "flag_mark" -> { l(6f, 3f, 6f, 21f); p(6f, 4f, 19f, 4f, 15f, 8.5f, 19f, 13f, 6f, 13f) }
            else -> { l(5f, 19f, 19f, 5f); a(5f, 19f); a(19f, 5f) }
        }
    }

    /** Wave through the points with a dot on every turn. */
    private fun wave(vararg v: Float) {
        p(*v)
        for (i in 2 until v.size - 2 step 2) dot(v[i], v[i + 1])
    }
}
