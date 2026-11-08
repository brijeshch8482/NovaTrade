package com.novatrade.demo.ui.instrument

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Thin-line icon of a drawing tool in the broker chart's style: the shape the tool draws, with
 * small hollow rings on its anchor points, on a 24-unit grid.
 */
@Composable
fun ToolIcon(id: String, tint: Color, size: Int = 20) {
    Canvas(Modifier.size(size.dp)) { ToolDrawer(this, tint).draw(id) }
}

private class ToolDrawer(private val scope: DrawScope, private val c: Color) {
    private val u = scope.size.width / 24f
    private val w = 1.2f * u
    private val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val dotted = Stroke(w, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f * u, 2.2f * u)))

    private fun o(x: Float, y: Float) = Offset(x * u, y * u)

    fun l(x1: Float, y1: Float, x2: Float, y2: Float) = scope.drawLine(c, o(x1, y1), o(x2, y2), w, StrokeCap.Round)

    fun dl(x1: Float, y1: Float, x2: Float, y2: Float) =
        scope.drawPath(Path().apply { moveTo(x1 * u, y1 * u); lineTo(x2 * u, y2 * u) }, c, style = dotted)

    /** Polyline through (x, y, x, y, ...). */
    fun p(vararg v: Float, close: Boolean = false) = scope.drawPath(path(v, close), c, style = stroke)

    private fun path(v: FloatArray, close: Boolean) = Path().apply {
        moveTo(v[0] * u, v[1] * u)
        for (i in 2 until v.size step 2) lineTo(v[i] * u, v[i + 1] * u)
        if (close) close()
    }

    /** Anchor ring. */
    fun a(x: Float, y: Float) = scope.drawCircle(c, 1.5f * u, o(x, y), style = Stroke(w))

    fun anchors(vararg v: Float) {
        for (i in v.indices step 2) a(v[i], v[i + 1])
    }

    fun r(x: Float, y: Float, wd: Float, h: Float) = scope.drawRect(c, o(x, y), Size(wd * u, h * u), style = stroke)

    fun rr(x: Float, y: Float, wd: Float, h: Float, rad: Float) = scope.drawRoundRect(c, o(x, y), Size(wd * u, h * u), CornerRadius(rad * u), style = stroke)

    fun circle(x: Float, y: Float, rad: Float) = scope.drawCircle(c, rad * u, o(x, y), style = stroke)

    fun oval(x: Float, y: Float, rx: Float, ry: Float) = scope.drawOval(c, o(x - rx, y - ry), Size(2 * rx * u, 2 * ry * u), style = stroke)

    /** Arc of the circle at (x, y); angles in degrees, clockwise from 3 o'clock. */
    fun arc(x: Float, y: Float, rad: Float, start: Float, sweep: Float) =
        scope.drawArc(c, start, sweep, false, o(x - rad, y - rad), Size(2 * rad * u, 2 * rad * u), style = stroke)

    fun quad(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float) =
        scope.drawPath(Path().apply { moveTo(x1 * u, y1 * u); quadraticTo(cx * u, cy * u, x2 * u, y2 * u) }, c, style = stroke)

    fun cubic(x1: Float, y1: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float, x2: Float, y2: Float) =
        scope.drawPath(Path().apply { moveTo(x1 * u, y1 * u); cubicTo(c1x * u, c1y * u, c2x * u, c2y * u, x2 * u, y2 * u) }, c, style = stroke)

    fun fill(vararg v: Float) = scope.drawPath(path(v, true), c)

    /** Small text centred on (x, y), e.g. the wave labels "1 5" or "A C". */
    fun txt(s: String, x: Float, y: Float, size: Float = 5.5f, bold: Boolean = false) = scope.drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.toArgb()
            textSize = size * u
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        canvas.nativeCanvas.drawText(s, x * u, y * u + size * u * 0.36f, paint)
    }

    /** Hollow candle with wicks. */
    fun candle(x: Float, top: Float, bottom: Float, wickTop: Float, wickBottom: Float, half: Float = 1.3f) {
        l(x, wickTop, x, top)
        l(x, bottom, x, wickBottom)
        r(x - half, top, 2 * half, bottom - top)
    }

    /** Pitchfork: handle from (hx, hy) to the median, crossbar between the outer prongs, prongs up-right. */
    fun fork(hx: Float, hy: Float, spread: Float) {
        val ax = 6f
        val ay = 10f
        val bx = 14f
        val by = 18f
        val mx = 10f
        val my = 14f
        l(hx, hy, mx, my)
        l(ax, ay, bx, by)
        l(ax, ay, ax + 7f, ay - 7f - spread)
        l(mx, my, mx + 8f, my - 8f)
        l(bx, by, bx + 7f + spread, by - 7f)
        anchors(hx, hy, ax, ay, bx, by)
    }

    fun zigzag(vararg v: Float) {
        p(*v)
        for (i in v.indices step 2) a(v[i], v[i + 1])
    }

    /** Elliott wave: a zigzag with anchors and two labels above. */
    fun elliott(first: String, last: String) {
        zigzag(3f, 19f, 7f, 14f, 10f, 18f, 14f, 12f, 17f, 16f, 21f, 10f)
        txt(first, 7f, 6f)
        txt(last, 21f, 4f)
    }

    fun draw(id: String) {
        when (id) {
            // ---- Lines
            "trend_line" -> { l(4f, 20f, 20f, 4f); anchors(4f, 20f, 20f, 4f) }
            "ray" -> { l(4f, 20f, 21f, 3f); anchors(4f, 20f, 12f, 12f) }
            "info_line" -> { l(4f, 20f, 16f, 5f); anchors(4f, 20f, 16f, 5f); rr(14f, 14.5f, 7f, 5.5f, 1.5f) }
            "extended_line" -> { l(2f, 22f, 22f, 2f); anchors(9f, 15f, 15f, 9f) }
            "trend_angle" -> { l(4f, 20f, 18f, 6f); l(4f, 20f, 20f, 20f); arc(4f, 20f, 6f, -45f, 45f); anchors(4f, 20f, 18f, 6f) }
            "horizontal_line" -> { l(2f, 12f, 22f, 12f); a(12f, 12f) }
            "horizontal_ray" -> { l(5.5f, 12f, 22f, 12f); a(4f, 12f) }
            "vertical_line" -> { l(12f, 2f, 12f, 22f); a(12f, 12f) }
            "cross_line" -> { l(2f, 12f, 22f, 12f); l(12f, 2f, 12f, 22f); a(12f, 12f) }
            // ---- Channels
            "parallel_channel" -> { l(3f, 14f, 14f, 3f); l(9f, 21f, 21f, 9f); anchors(5f, 12f, 13f, 4f, 15f, 15f) }
            "regression_trend" -> { p(4f, 10f, 20f, 4f, 20f, 14f, 4f, 20f, close = true); l(4f, 15f, 20f, 9f); anchors(4f, 15f, 20f, 9f) }
            "flat_top_bottom" -> { l(4f, 13f, 19f, 5f); l(4f, 19f, 19f, 19f); anchors(4f, 13f, 19f, 5f, 4f, 19f, 19f, 19f) }
            "disjoint_channel" -> { l(4f, 10f, 19f, 4f); l(4f, 14f, 19f, 20f); anchors(4f, 10f, 19f, 4f, 4f, 14f, 19f, 20f) }
            // ---- Pitchforks
            "pitchfork" -> fork(3f, 21f, 0f)
            "schiff_pitchfork" -> fork(3.5f, 18.5f, 1f)
            "modified_schiff_pitchfork" -> fork(4f, 20f, 2f)
            "inside_pitchfork" -> { fork(3f, 21f, -2f) }
            // ---- Fibonacci
            "fib_retracement" -> { listOf(5f, 9.5f, 14f, 18.5f).forEach { l(4f, it, 20f, it) }; anchors(20f, 5f, 4f, 20f) }
            "trend_fib_extension" -> { l(4f, 11f, 15f, 5f); anchors(4f, 11f, 15f, 5f); listOf(14f, 17.5f, 21f).forEach { l(4f, it, 20f, it) } }
            "fib_channel" -> { l(3f, 12f, 17f, 4f); l(3f, 17f, 19f, 8f); l(5f, 21f, 21f, 12f); anchors(17f, 4f, 3f, 12f) }
            "fib_time_zone" -> { listOf(10f, 13.5f, 17f, 20.5f).forEach { l(it, 3f, it, 21f) }; l(4f, 6f, 7f, 19f); anchors(4f, 6f, 7f, 19f) }
            "fib_speed_fan" -> { l(4f, 4f, 4f, 20f); l(4f, 20f, 20f, 20f); listOf(4f, 9f, 14f).forEach { l(4f, 20f, 20f, it) }; listOf(9f, 14f).forEach { l(4f, 20f, it, 4f) }; anchors(4f, 20f, 20f, 4f) }
            "trend_fib_time" -> { listOf(12f, 16f, 20f).forEach { l(it, 3f, it, 21f) }; p(4f, 7f, 7f, 19f, 9.5f, 10f); anchors(4f, 7f, 7f, 19f, 9.5f, 10f) }
            "fib_circles" -> { circle(11f, 13f, 2.5f); circle(11f, 13f, 5.5f); circle(11f, 13f, 8.5f); a(18.5f, 5.5f) }
            "fib_spiral" -> { arc(12f, 13f, 2f, 0f, 180f); arc(11f, 13f, 3f, 180f, 180f); arc(12.5f, 13f, 4.5f, 0f, 180f); arc(10.5f, 13f, 6.5f, 180f, 130f); a(17f, 7f) }
            "fib_speed_arcs" -> { arc(12f, 12f, 3f, 0f, 180f); arc(12f, 12f, 6f, 0f, 180f); arc(12f, 12f, 9f, 0f, 180f); a(12f, 8f) }
            "fib_wedge" -> { l(5f, 5.5f, 5f, 20f); l(5f, 20f, 19.5f, 20f); arc(5f, 20f, 5f, -90f, 90f); arc(5f, 20f, 9.5f, -90f, 90f); anchors(5f, 4f, 21f, 20f) }
            "pitchfan" -> { listOf(12f to 3f, 16f to 4f, 19f to 7f, 21f to 11f).forEach { (x, y) -> l(4f, 20f, x, y) }; l(10f, 5f, 20f, 15f); anchors(4f, 20f, 10f, 5f, 20f, 15f) }
            // ---- Gann
            "gann_box" -> { r(4f, 5f, 15f, 15f); l(9f, 5f, 9f, 20f); l(14f, 5f, 14f, 20f); l(4f, 10f, 19f, 10f); l(4f, 15f, 19f, 15f); anchors(19f, 5f, 4f, 20f) }
            "gann_square_fixed" -> { r(4f, 5f, 15f, 15f); l(4f, 10f, 19f, 10f); l(4f, 15f, 19f, 15f); l(9f, 5f, 9f, 20f); l(14f, 5f, 14f, 20f); l(4f, 20f, 19f, 5f); l(4f, 5f, 19f, 20f); anchors(19f, 5f, 4f, 20f) }
            "gann_square" -> { r(4f, 5f, 15f, 15f); l(4f, 12.5f, 19f, 12.5f); l(11.5f, 5f, 11.5f, 20f); l(4f, 20f, 19f, 5f); l(4f, 5f, 19f, 20f); l(4f, 20f, 11.5f, 5f); l(4f, 20f, 19f, 12.5f); l(11.5f, 20f, 19f, 5f); anchors(19f, 5f, 4f, 20f) }
            "gann_fan" -> { listOf(10f to 3f, 14f to 4f, 18f to 6f, 20f to 10f, 21f to 15f, 21f to 19f).forEach { (x, y) -> l(4f, 20f, x, y) }; anchors(4f, 20f, 16f, 8f) }
            // ---- Patterns
            "xabcd_pattern" -> { zigzag(4f, 20f, 6f, 5f, 12f, 13f, 17f, 4f, 20f, 18f); l(4f, 20f, 12f, 13f); l(12f, 13f, 20f, 18f); l(6f, 5f, 17f, 4f) }
            "cypher_pattern" -> { zigzag(4f, 20f, 6f, 5f, 12f, 13f, 17f, 4f, 20f, 18f); l(6f, 5f, 12f, 13f); l(4f, 20f, 12f, 13f); txt("C", 10.5f, 18.5f, 6f) }
            "head_shoulders" -> zigzag(3f, 20f, 6f, 12f, 9f, 17f, 12f, 5f, 15f, 17f, 18f, 12f, 21f, 20f)
            "abcd_pattern" -> { p(5f, 20f, 7f, 7f, 19f, 4f, 17f, 17f, close = true); l(7f, 7f, 17f, 17f); anchors(5f, 20f, 7f, 7f, 19f, 4f, 17f, 17f) }
            "triangle_pattern" -> { p(4f, 4f, 21f, 12f, 4f, 20f); zigzag(4f, 4f, 7f, 18f, 11f, 7f, 14f, 15f, 21f, 12f) }
            "three_drives" -> { zigzag(3f, 9f, 6f, 20f, 9f, 13f, 12f, 19f, 15f, 9f, 18f, 13f, 21f, 3f) }
            // ---- Elliott waves
            "elliott_impulse" -> elliott("1", "5")
            "elliott_correction" -> elliott("A", "C")
            "elliott_triangle" -> elliott("A", "E")
            "elliott_double" -> elliott("W", "Y")
            "elliott_triple" -> elliott("W", "Z")
            // ---- Cycles
            "cyclic_lines" -> { listOf(4f, 9f, 14f, 19f).forEach { l(it, 3f, it, 21f) }; anchors(4f, 12f, 9f, 9f) }
            "time_cycles" -> { arc(8f, 12f, 4f, 0f, 180f); arc(16f, 12f, 4f, 0f, 180f); anchors(4f, 12f, 12f, 12f, 20f, 12f) }
            "sine_line" -> { cubic(3f, 13f, 6f, 1f, 8f, 3f, 12f, 13f); cubic(12f, 13f, 15f, 22f, 18f, 23f, 21f, 9f); anchors(6f, 5f, 18f, 20f) }
            // ---- Projection
            "long_position" -> { listOf(5f, 12f, 19f).forEach { l(6f, it, 21f, it) }; anchors(4f, 5f, 4f, 12f, 4f, 19f); dl(9f, 10f, 17f, 7f) }
            "short_position" -> { listOf(5f, 12f, 19f).forEach { l(6f, it, 21f, it) }; anchors(4f, 5f, 4f, 12f, 4f, 19f); dl(9f, 14f, 17f, 17f) }
            "forecast" -> { candle(5f, 8f, 16f, 5f, 20f); candle(11f, 6f, 13f, 3f, 17f); dl(18f, 4f, 18f, 13f); a(18f, 16f); p(16f, 7f, 18f, 4f, 20f, 7f) }
            "bars_pattern" -> { candle(4f, 9f, 17f, 6f, 20f, 1.1f); candle(9f, 7f, 14f, 4f, 18f, 1.1f); candle(14f, 10f, 16f, 7f, 19f, 1.1f); candle(19f, 5f, 12f, 3f, 16f, 1.1f) }
            "ghost_feed" -> { candle(8f, 13f, 18f, 11f, 20f, 1.1f); candle(12f, 9f, 15f, 7f, 18f, 1.1f); candle(16f, 7f, 13f, 4f, 15f, 1.1f); l(5f, 19f, 6.5f, 19f); anchors(3.5f, 19f, 20.5f, 7f) }
            "projection" -> { l(4f, 6f, 4f, 20f); l(4f, 20f, 18f, 20f); arc(4f, 20f, 14f, -90f, 90f); anchors(4f, 4.5f, 4f, 20f, 19.5f, 20f) }
            // ---- Volume-based
            "anchored_vwap" -> { candle(9f, 9f, 16f, 5f, 20f, 1.4f); candle(15f, 7f, 13f, 4f, 17f, 1.4f); cubic(4f, 15f, 8f, 13f, 14f, 13f, 21f, 7f); a(3f, 15.5f) }
            "fixed_range_volume_profile" -> {
                l(5f, 4f, 5f, 18f); l(5f, 5f, 12f, 5f); l(5f, 8.5f, 10f, 8.5f); l(10f, 8.5f, 10f, 5f); l(5f, 12f, 8f, 12f); l(8f, 12f, 8f, 8.5f)
                a(5f, 19.5f); l(19f, 7f, 19f, 21f); a(19f, 5f)
            }
            // ---- Measurer
            "price_range" -> { l(5f, 4f, 18f, 4f); l(6f, 20f, 19f, 20f); l(12f, 6f, 12f, 20f); p(9.5f, 8.5f, 12f, 6f, 14.5f, 8.5f); anchors(19.5f, 4f, 4.5f, 20f) }
            "date_range" -> { l(4f, 6f, 4f, 19f); l(20f, 5f, 20f, 18f); l(4f, 12f, 18f, 12f); p(15.5f, 9.5f, 18f, 12f, 15.5f, 14.5f); anchors(4f, 20.5f, 20f, 3.5f) }
            "date_price_range" -> { r(4f, 5f, 15f, 15f); l(7f, 12.5f, 16f, 12.5f); p(13.5f, 10f, 16f, 12.5f, 13.5f, 15f); l(11.5f, 17f, 11.5f, 8f); p(9f, 10.5f, 11.5f, 8f, 14f, 10.5f); anchors(19f, 5f, 4f, 20f) }
            // ---- Brushes
            "brush" -> {
                scope.drawPath(Path().apply {
                    moveTo(3f * u, 20f * u); cubicTo(4f * u, 15f * u, 7f * u, 12.5f * u, 10.5f * u, 13f * u)
                    cubicTo(12.5f * u, 14.5f * u, 12f * u, 18f * u, 9f * u, 19.5f * u); cubicTo(7f * u, 20.3f * u, 5f * u, 20.2f * u, 3f * u, 20f * u); close()
                }, c, style = stroke)
                l(11.5f, 13f, 18f, 7f); p(16f, 5.5f, 18f, 7f, 21.5f, 3.5f)
            }
            "highlighter" -> { p(9f, 9f, 16f, 16f, 12f, 20f, 5f, 13f, close = true); l(9f, 9f, 15f, 3f); l(16f, 16f, 21f, 11f); p(5f, 13f, 3f, 20f, 12f, 20f) }
            // ---- Arrows
            "arrow_marker" -> p(4f, 21f, 3f, 20f, 13f, 9f, 11f, 7f, 20f, 4f, 17f, 13f, 15f, 11f, 5f, 21f, close = true)
            "arrow" -> { l(4f, 20f, 15f, 9f); fill(18f, 6f, 11.5f, 8.5f, 15.5f, 12.5f); anchors(4f, 20f, 19.5f, 4.5f) }
            "arrow_mark_up" -> p(12f, 4f, 20f, 12f, 15.5f, 12f, 15.5f, 20f, 8.5f, 20f, 8.5f, 12f, 4f, 12f, close = true)
            "arrow_mark_down" -> p(12f, 20f, 20f, 12f, 15.5f, 12f, 15.5f, 4f, 8.5f, 4f, 8.5f, 12f, 4f, 12f, close = true)
            "arrow_mark_left" -> p(4f, 12f, 12f, 4f, 12f, 8.5f, 20f, 8.5f, 20f, 15.5f, 12f, 15.5f, 12f, 20f, close = true)
            "arrow_mark_right" -> p(20f, 12f, 12f, 4f, 12f, 8.5f, 4f, 8.5f, 4f, 15.5f, 12f, 15.5f, 12f, 20f, close = true)
            // ---- Shapes
            "rectangle" -> { r(5f, 5f, 14f, 14f); anchors(5f, 5f, 19f, 5f, 5f, 19f, 19f, 19f) }
            "rotated_rectangle" -> { p(3f, 12f, 12f, 4f, 21f, 9f, 12f, 20f, close = true); anchors(3f, 12f, 12f, 4f, 21f, 9f, 12f, 20f, 7.5f, 8f, 16.5f, 14.5f) }
            "path" -> { p(3f, 17f, 7f, 10f, 11f, 15f, 20f, 6f); p(16f, 6f, 20f, 6f, 20f, 10f); anchors(7f, 10f, 11f, 15f) }
            "circle" -> { circle(12f, 12f, 8.5f); a(12f, 12f); a(20.5f, 12f) }
            "ellipse" -> { oval(12f, 12f, 9f, 6f); anchors(3f, 12f, 21f, 12f, 12f, 6f, 12f, 18f) }
            "polyline" -> { p(5f, 15f, 4f, 7f, 9f, 4f, 14f, 9f, 19f, 5f, 20f, 13f, 12f, 14f, 9f, 20f, close = true); anchors(5f, 15f, 4f, 7f, 9f, 4f, 14f, 9f, 19f, 5f, 20f, 13f, 9f, 20f) }
            "triangle" -> { p(5f, 4f, 5f, 20f, 19f, 20f, close = true); anchors(5f, 4f, 5f, 20f, 19f, 20f) }
            "arc" -> { quad(5f, 18f, 9f, 9f, 18f, 5f); quad(5f, 18f, 18f, 22f, 18f, 5f); anchors(5f, 18f, 18f, 5f, 15.5f, 18f) }
            "curve" -> { quad(4f, 20f, 7f, 7f, 19f, 5f); anchors(4f, 20f, 7f, 10f, 19f, 5f) }
            "double_curve" -> { cubic(5f, 19f, 13f, 22f, 7f, 7f, 13f, 5f); quad(13f, 5f, 16f, 3f, 19f, 5f); anchors(5f, 19f, 13f, 5f, 19f, 5f) }
            // ---- Text and notes
            "text" -> { l(6f, 5f, 18f, 5f); l(12f, 5f, 12f, 20f); l(10f, 20f, 14f, 20f); l(6f, 5f, 6f, 7f); l(18f, 5f, 18f, 7f) }
            "anchored_text" -> {
                l(3f, 4f, 13f, 4f); l(8f, 4f, 8f, 18f); l(6f, 18f, 10f, 18f); l(3f, 4f, 3f, 6f); l(13f, 4f, 13f, 6f)
                circle(17.5f, 14f, 1.2f); l(17.5f, 15.2f, 17.5f, 21f); l(15.5f, 17f, 19.5f, 17f); arc(17.5f, 18.5f, 2.6f, 20f, 140f)
            }
            "note" -> { r(4f, 3f, 16f, 11f); txt("T", 12f, 8.5f, 8f); l(12f, 14f, 12f, 18f); a(12f, 19.5f) }
            "price_note" -> { r(4f, 3f, 16f, 11f); txt("$", 12f, 8.5f, 8f); l(12f, 14f, 12f, 18f); a(12f, 19.5f) }
            "pin" -> {
                scope.drawPath(Path().apply {
                    moveTo(12f * u, 21f * u); cubicTo(5f * u, 13f * u, 5f * u, 3f * u, 12f * u, 3f * u)
                    cubicTo(19f * u, 3f * u, 19f * u, 13f * u, 12f * u, 21f * u); close()
                }, c, style = stroke)
                circle(12f, 9.5f, 2.5f)
            }
            "table" -> { r(3f, 5f, 18f, 14f); l(3f, 9.5f, 21f, 9.5f); l(3f, 14f, 21f, 14f); l(8.5f, 5f, 8.5f, 19f) }
            "callout" -> p(3f, 5f, 21f, 5f, 21f, 15f, 8f, 15f, 4f, 19f, 4f, 15f, 3f, 15f, close = true)
            "comment" -> rr(3f, 7f, 18f, 10f, 5f)
            "price_label" -> { p(5f, 5f, 21f, 5f, 21f, 15f, 9f, 15f, 5f, 18f, close = true); a(4f, 20f) }
            "signpost" -> {
                circle(12f, 9f, 6.5f)
                p(12f, 5.2f, 13.1f, 7.6f, 15.7f, 7.8f, 13.7f, 9.5f, 14.3f, 12f, 12f, 10.7f, 9.7f, 12f, 10.3f, 9.5f, 8.3f, 7.8f, 10.9f, 7.6f, close = true)
                l(12f, 15.5f, 12f, 21f)
            }
            "flag_mark" -> { l(5f, 4f, 5f, 21f); p(5f, 5f, 19f, 5f, 15f, 10f, 19f, 15f, 5f, 15f) }
            else -> { l(4f, 20f, 20f, 4f); anchors(4f, 20f, 20f, 4f) }
        }
    }
}
