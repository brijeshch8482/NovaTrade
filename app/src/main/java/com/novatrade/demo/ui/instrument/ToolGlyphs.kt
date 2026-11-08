package com.novatrade.demo.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Thin-line icons for the drawing toolbar, in the style of TradingView chart tools. */
enum class Glyph { CANDLES, LINE_CHART, FX, GRID4, ARROW_UP, CROSS, TREND, FIB, PATTERN, PREDICTION, BRUSH, TEXT, EMOJI, RULER, ZOOM, MAGNET, STAY, LOCK, UNLOCK, EYE, EYE_OFF, TRASH, LAYERS }

/** Glyph for each left-panel tool group. */
fun groupGlyph(key: String): Glyph = when (key) {
    "lines" -> Glyph.TREND
    "fib" -> Glyph.FIB
    "patterns" -> Glyph.PATTERN
    "prediction" -> Glyph.PREDICTION
    "shapes" -> Glyph.BRUSH
    else -> Glyph.TEXT
}

@Composable
fun ToolGlyph(glyph: Glyph, tint: Color, size: Int = 24) {
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.width
        val w = 1.25.dp.toPx()
        val line = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(s * x, s * y)
        fun l(x0: Float, y0: Float, x1: Float, y1: Float) = drawLine(tint, p(x0, y0), p(x1, y1), w, StrokeCap.Round)
        fun ring(x: Float, y: Float, r: Float = 0.08f) = drawCircle(tint, s * r, p(x, y), style = line)
        fun path(vararg pts: Pair<Float, Float>, close: Boolean = false) {
            val pa = Path()
            pts.forEachIndexed { i, (x, y) -> if (i == 0) pa.moveTo(s * x, s * y) else pa.lineTo(s * x, s * y) }
            if (close) pa.close()
            drawPath(pa, tint, style = line)
        }

        when (glyph) {
            Glyph.CANDLES -> {
                // Two hollow candles: a tall one on the left, a shorter one on the right.
                l(0.3f, 0.06f, 0.3f, 0.2f)
                drawRect(tint, p(0.19f, 0.2f), Size(s * 0.22f, s * 0.6f), style = line)
                l(0.3f, 0.8f, 0.3f, 0.94f)
                l(0.7f, 0.18f, 0.7f, 0.34f)
                drawRect(tint, p(0.59f, 0.34f), Size(s * 0.22f, s * 0.32f), style = line)
                l(0.7f, 0.66f, 0.7f, 0.8f)
            }
            Glyph.LINE_CHART -> path(0.08f to 0.78f, 0.34f to 0.42f, 0.56f to 0.62f, 0.92f to 0.2f)
            Glyph.FX -> {
                // Script "f" with curls at both ends, crossbar, and a small subscript x.
                val f = Path().apply {
                    moveTo(s * 0.66f, s * 0.12f)
                    cubicTo(s * 0.56f, s * 0.02f, s * 0.42f, s * 0.08f, s * 0.42f, s * 0.26f)
                    lineTo(s * 0.42f, s * 0.76f)
                    cubicTo(s * 0.42f, s * 0.94f, s * 0.28f, s * 0.98f, s * 0.18f, s * 0.88f)
                }
                drawPath(f, tint, style = line)
                l(0.26f, 0.36f, 0.58f, 0.36f)
                l(0.58f, 0.64f, 0.84f, 0.92f)
                l(0.84f, 0.64f, 0.58f, 0.92f)
            }
            Glyph.GRID4 -> {
                listOf(0.08f to 0.08f, 0.56f to 0.08f, 0.08f to 0.56f, 0.56f to 0.56f).forEach { (x, y) ->
                    drawRoundRect(tint, p(x, y), Size(s * 0.36f, s * 0.36f), CornerRadius(s * 0.09f), style = line)
                }
            }
            Glyph.ARROW_UP -> {
                l(0.5f, 0.84f, 0.5f, 0.16f)
                path(0.28f to 0.38f, 0.5f to 0.16f, 0.72f to 0.38f)
            }
            Glyph.CROSS -> {
                // Crosshair with an open centre.
                l(0.5f, 0.12f, 0.5f, 0.38f)
                l(0.5f, 0.62f, 0.5f, 0.88f)
                l(0.12f, 0.5f, 0.38f, 0.5f)
                l(0.62f, 0.5f, 0.88f, 0.5f)
            }
            Glyph.TREND -> {
                l(0.21f, 0.79f, 0.79f, 0.21f)
                ring(0.15f, 0.85f, 0.07f)
                ring(0.85f, 0.15f, 0.07f)
            }
            Glyph.FIB -> {
                l(0.1f, 0.16f, 0.9f, 0.16f)
                l(0.1f, 0.38f, 0.8f, 0.38f)
                ring(0.87f, 0.38f, 0.065f)
                l(0.1f, 0.6f, 0.9f, 0.6f)
                l(0.2f, 0.82f, 0.9f, 0.82f)
                ring(0.13f, 0.82f, 0.065f)
            }
            Glyph.PATTERN -> {
                // XABCD "bow tie" with a ring on every point.
                val x = 0.14f to 0.82f
                val a = 0.32f to 0.14f
                val b = 0.5f to 0.46f
                val c = 0.74f to 0.14f
                val d = 0.86f to 0.76f
                path(x, a, b, c, d)
                path(x, b, d)
                listOf(x, a, b, c, d).forEach { (px, py) -> ring(px, py, 0.065f) }
            }
            Glyph.PREDICTION -> {
                l(0.2f, 0.16f, 0.88f, 0.16f)
                ring(0.13f, 0.16f, 0.065f)
                l(0.2f, 0.5f, 0.8f, 0.5f)
                ring(0.13f, 0.5f, 0.065f)
                ring(0.87f, 0.5f, 0.065f)
                l(0.2f, 0.82f, 0.88f, 0.82f)
                ring(0.13f, 0.82f, 0.065f)
                // dotted forecast between the top two levels
                for (i in 0 until 4) {
                    val t0 = i / 4f + 0.04f
                    val t1 = t0 + 0.12f
                    l(0.32f + 0.5f * t0, 0.42f - 0.18f * t0, 0.32f + 0.5f * t1, 0.42f - 0.18f * t1)
                }
            }
            Glyph.BRUSH -> {
                // Brush head with a flat bottom edge and a point at the bottom-left, plus a hooked handle.
                val head = Path().apply {
                    moveTo(s * 0.08f, s * 0.88f)
                    lineTo(s * 0.4f, s * 0.88f)
                    cubicTo(s * 0.56f, s * 0.88f, s * 0.62f, s * 0.62f, s * 0.5f, s * 0.52f)
                    cubicTo(s * 0.38f, s * 0.42f, s * 0.16f, s * 0.56f, s * 0.08f, s * 0.88f)
                }
                drawPath(head, tint, style = line)
                val handle = Path().apply {
                    moveTo(s * 0.6f, s * 0.22f)
                    quadraticTo(s * 0.58f, s * 0.44f, s * 0.7f, s * 0.42f)
                    lineTo(s * 0.92f, s * 0.16f)
                }
                drawPath(handle, tint, style = line)
            }
            Glyph.TEXT -> {
                l(0.18f, 0.14f, 0.82f, 0.14f)
                l(0.18f, 0.14f, 0.18f, 0.24f)
                l(0.82f, 0.14f, 0.82f, 0.24f)
                l(0.5f, 0.14f, 0.5f, 0.88f)
                l(0.38f, 0.88f, 0.62f, 0.88f)
            }
            Glyph.EMOJI -> {
                drawCircle(tint, s * 0.42f, p(0.5f, 0.5f), style = line)
                drawCircle(tint, s * 0.035f, p(0.39f, 0.42f))
                drawCircle(tint, s * 0.035f, p(0.61f, 0.42f))
                drawArc(tint, 25f, 130f, false, p(0.38f, 0.44f), Size(s * 0.24f, s * 0.2f), style = line)
            }
            Glyph.RULER -> {
                rotateRect(this, s, tint, w)
            }
            Glyph.ZOOM -> {
                drawCircle(tint, s * 0.33f, p(0.43f, 0.43f), style = line)
                l(0.67f, 0.67f, 0.86f, 0.86f)
                l(0.3f, 0.43f, 0.56f, 0.43f)
                l(0.43f, 0.3f, 0.43f, 0.56f)
            }
            Glyph.MAGNET -> {
                // Horseshoe: outer and inner arcs, legs, and a block on each foot.
                drawArc(tint, 180f, 180f, false, p(0.2f, 0.12f), Size(s * 0.6f, s * 0.6f), style = line)
                drawArc(tint, 180f, 180f, false, p(0.38f, 0.3f), Size(s * 0.24f, s * 0.24f), style = line)
                l(0.2f, 0.42f, 0.2f, 0.88f)
                l(0.8f, 0.42f, 0.8f, 0.88f)
                l(0.38f, 0.42f, 0.38f, 0.88f)
                l(0.62f, 0.42f, 0.62f, 0.88f)
                l(0.2f, 0.72f, 0.38f, 0.72f)
                l(0.62f, 0.72f, 0.8f, 0.72f)
                l(0.2f, 0.88f, 0.38f, 0.88f)
                l(0.62f, 0.88f, 0.8f, 0.88f)
            }
            Glyph.STAY -> {
                // Large pencil, with a small open padlock below it on the right.
                path(0.08f to 0.84f, 0.12f to 0.66f, 0.58f to 0.2f, 0.72f to 0.34f, 0.26f to 0.8f, close = true)
                l(0.5f, 0.28f, 0.64f, 0.42f)
                drawRoundRect(tint, p(0.62f, 0.7f), Size(s * 0.3f, s * 0.22f), CornerRadius(s * 0.03f), style = line)
                drawArc(tint, 180f, 180f, false, p(0.72f, 0.56f), Size(s * 0.2f, s * 0.2f), style = line)
                l(0.92f, 0.66f, 0.92f, 0.7f)
                drawCircle(tint, s * 0.025f, p(0.77f, 0.81f))
            }
            Glyph.LOCK, Glyph.UNLOCK -> {
                drawRoundRect(tint, p(0.2f, 0.46f), Size(s * 0.6f, s * 0.42f), CornerRadius(s * 0.06f), style = line)
                if (glyph == Glyph.LOCK) {
                    drawArc(tint, 180f, 180f, false, p(0.32f, 0.16f), Size(s * 0.36f, s * 0.4f), style = line)
                    l(0.32f, 0.36f, 0.32f, 0.46f)
                    l(0.68f, 0.36f, 0.68f, 0.46f)
                } else {
                    drawArc(tint, 180f, 180f, false, p(0.48f, 0.12f), Size(s * 0.36f, s * 0.4f), style = line)
                    l(0.84f, 0.32f, 0.84f, 0.4f)
                    l(0.48f, 0.32f, 0.48f, 0.46f)
                }
                l(0.5f, 0.62f, 0.5f, 0.72f)
            }
            Glyph.EYE, Glyph.EYE_OFF -> {
                val pa = Path().apply {
                    moveTo(s * 0.08f, s * 0.5f)
                    quadraticTo(s * 0.5f, s * 0.08f, s * 0.92f, s * 0.5f)
                    quadraticTo(s * 0.5f, s * 0.92f, s * 0.08f, s * 0.5f)
                }
                drawPath(pa, tint, style = line)
                drawCircle(tint, s * 0.12f, p(0.5f, 0.5f), style = line)
                if (glyph == Glyph.EYE_OFF) l(0.16f, 0.84f, 0.84f, 0.16f)
            }
            Glyph.TRASH -> {
                l(0.16f, 0.24f, 0.84f, 0.24f)
                path(0.38f to 0.24f, 0.4f to 0.12f, 0.6f to 0.12f, 0.62f to 0.24f)
                path(0.24f to 0.24f, 0.3f to 0.88f, 0.7f to 0.88f, 0.76f to 0.24f)
                l(0.43f, 0.38f, 0.44f, 0.74f)
                l(0.57f, 0.38f, 0.56f, 0.74f)
            }
            Glyph.LAYERS -> {
                path(0.5f to 0.14f, 0.88f to 0.34f, 0.5f to 0.54f, 0.12f to 0.34f, close = true)
                path(0.12f to 0.5f, 0.5f to 0.7f, 0.88f to 0.5f)
                path(0.12f to 0.66f, 0.5f to 0.86f, 0.88f to 0.66f)
            }
        }
    }
}

/** Ruler: a long, narrow bar from bottom-left to top-right, with ticks along its top edge. */
private fun rotateRect(scope: DrawScope, s: Float, tint: Color, w: Float) = with(scope) {
    val a = Offset(s * 0.06f, s * 0.76f)
    val b = Offset(s * 0.76f, s * 0.06f)
    val c = Offset(s * 0.94f, s * 0.24f)
    val d = Offset(s * 0.24f, s * 0.94f)
    val pa = Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
    drawPath(pa, tint, style = Stroke(w, join = StrokeJoin.Round))
    for (i in 1..5) {
        val t = i / 6f
        val start = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        val len = if (i % 2 == 0) 0.13f else 0.08f
        drawLine(tint, start, Offset(start.x + s * len, start.y + s * len), w, StrokeCap.Round)
    }
}
