package com.novatrade.demo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.ui.theme.AppColors

@Composable
fun DemoBadge() {
    Text(
        "Demo",
        color = AppColors.Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.Accent.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun Coin(code: String, size: Int, modifier: Modifier = Modifier) = Badge(code, size, modifier)

/**
 * Small price line. With [baseline] (e.g. the previous close) it draws a dashed reference line and
 * colours the line blue above it and red below, like broker watchlists.
 */
@Composable
fun Sparkline(points: List<Double>, modifier: Modifier = Modifier, baseline: Double? = null, upColor: Color = AppColors.Profit) {
    if (points.size < 2) {
        Box(modifier)
        return
    }
    val ref = baseline ?: points.first()
    val color = if (points.last() >= ref) upColor else AppColors.Loss
    Canvas(modifier) {
        val lo = minOf(points.min(), ref)
        val hi = maxOf(points.max(), ref)
        val range = (hi - lo).takeIf { it > 0 } ?: 1.0
        val dx = size.width / (points.size - 1)
        fun y(v: Double) = ((hi - v) / range * size.height).toFloat()
        if (baseline != null) {
            drawLine(
                Color(0xFF6B7480), Offset(0f, y(ref)), Offset(size.width, y(ref)), 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }
        val path = Path()
        points.forEachIndexed { i, p ->
            val o = Offset(i * dx, y(p))
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, color, style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = AppColors.TextPrimary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = AppColors.TextPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Surface)
            .padding(16.dp),
    ) { content() }
}

/** Text tabs with a short accent underline under the selected one. */
@Composable
fun UnderlineTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 8.dp)) {
        titles.forEachIndexed { i, title ->
            val active = i == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    color = if (active) AppColors.TextPrimary else AppColors.TextSecondary,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                )
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(width = 24.dp, height = 2.dp)
                        .background(if (active) AppColors.Accent else Color.Transparent),
                )
            }
        }
    }
}

/**
 * Number field with − / + buttons. When empty, the first press starts from [seed].
 * [placeholder] is shown while empty (e.g. "Not set").
 */
@Composable
fun Stepper(
    value: String,
    onValueChange: (String) -> Unit,
    step: Double,
    decimals: Int,
    seed: Double?,
    placeholder: String,
    modifier: Modifier = Modifier,
    min: Double = 0.0,
) {
    fun bump(dir: Int) {
        val current = value.toPriceOrNull() ?: seed ?: return
        val next = (current + dir * step).coerceAtLeast(min)
        onValueChange(String.format(java.util.Locale.US, "%.${decimals}f", next))
    }
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.SurfaceHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { bump(-1) }) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (value.isEmpty()) Text(placeholder, color = AppColors.TextSecondary, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(AppColors.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconButton(onClick = { bump(1) }) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
    }
}

/** True while balances are hidden with the flip gesture. */
val LocalHideBalances = androidx.compose.runtime.staticCompositionLocalOf { false }

/** USD amount, masked while balances are hidden. */
@Composable
fun balanceText(v: Double): String = if (LocalHideBalances.current) "••••• USD" else com.novatrade.demo.util.usd(v)

fun String.toPriceOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
