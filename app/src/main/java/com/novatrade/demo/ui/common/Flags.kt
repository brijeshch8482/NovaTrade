package com.novatrade.demo.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.ui.theme.AppColors
import kotlin.math.cos
import kotlin.math.sin

private val CCY_COUNTRY = mapOf(
    "USD" to "US", "EUR" to "EU", "GBP" to "GB", "JPY" to "JP", "CHF" to "CH", "AUD" to "AU", "CAD" to "CA",
    "NZD" to "NZ", "HKD" to "HK", "SGD" to "SG", "TRY" to "TR", "ZAR" to "ZA", "MXN" to "MX", "NOK" to "NO",
    "SEK" to "SE", "PLN" to "PL", "THB" to "TH", "INR" to "IN",
)

private data class Coin(val text: String, val bg: Color, val fg: Color = Color.White)

private val COINS = mapOf(
    "XAU" to Coin("Au", Color(0xFFD4A62A), Color(0xFF3A2A00)),
    "XAG" to Coin("Ag", Color(0xFFB8BEC6), Color(0xFF30353B)),
    "XPT" to Coin("Pt", Color(0xFF8E99A6)),
    "XPD" to Coin("Pd", Color(0xFF7D8793)),
    "XCU" to Coin("Cu", Color(0xFFB8733D)),
    "BTC" to Coin("₿", Color(0xFFF7931A)),
    "ETH" to Coin("Ξ", Color(0xFF627EEA)),
    "BNB" to Coin("B", Color(0xFFF3BA2F), Color(0xFF2B2100)),
    "SOL" to Coin("S", Color(0xFF9945FF)),
    "XRP" to Coin("X", Color(0xFF23292F)),
    "ADA" to Coin("₳", Color(0xFF0033AD)),
    "DOGE" to Coin("Ð", Color(0xFFC2A633)),
    "TRX" to Coin("T", Color(0xFFEB0029)),
    "TON" to Coin("◆", Color(0xFF0098EA)),
    "AVAX" to Coin("A", Color(0xFFE84142)),
    "DOT" to Coin("●", Color(0xFFE6007A)),
    "LINK" to Coin("⬡", Color(0xFF2A5ADA)),
    "LTC" to Coin("Ł", Color(0xFF345D9D)),
    "BCH" to Coin("₿", Color(0xFF0AC18E)),
    "XLM" to Coin("✦", Color(0xFF14181D)),
    "UNI" to Coin("U", Color(0xFFFF007A)),
    "ATOM" to Coin("⚛", Color(0xFF2E3148)),
    "ETC" to Coin("Ξ", Color(0xFF328332)),
    "NEAR" to Coin("N", Color(0xFF111111)),
    "APT" to Coin("≡", Color(0xFF111111)),
    "ARB" to Coin("A", Color(0xFF28A0F0)),
    "OP" to Coin("OP", Color(0xFFFF0420)),
    "SUI" to Coin("S", Color(0xFF4DA2FF)),
    "FIL" to Coin("⨍", Color(0xFF0090FF)),
    "ICP" to Coin("∞", Color(0xFF29ABE2)),
    "HBAR" to Coin("ħ", Color(0xFF222222)),
    "AAVE" to Coin("A", Color(0xFFB6509E)),
    "INJ" to Coin("I", Color(0xFF0082FA)),
    "SHIB" to Coin("S", Color(0xFFFFA409), Color(0xFF3A1E00)),
    "PEPE" to Coin("P", Color(0xFF4C9540)),
    "USDT" to Coin("₮", Color(0xFF26A17B)),
    "USDC" to Coin("$", Color(0xFF2775CA)),
)

private val STOCK_COLORS = mapOf(
    "AAPL" to Color(0xFF1C1C1E), "MSFT" to Color(0xFF00A4EF), "NVDA" to Color(0xFF76B900), "TSLA" to Color(0xFFCC0000),
    "AMZN" to Color(0xFFFF9900), "GOOGL" to Color(0xFF4285F4), "META" to Color(0xFF0866FF), "NFLX" to Color(0xFFE50914),
    "AMD" to Color(0xFF111111), "INTC" to Color(0xFF0071C5), "JPM" to Color(0xFF5A4636), "BA" to Color(0xFF0033A1),
    "DIS" to Color(0xFF113CCF), "KO" to Color(0xFFF40009), "PG" to Color(0xFF003DA5),
)

/** Bundled logos (assets/icons), decoded once and kept for the whole session. */
private object IconCache {
    private val cache = HashMap<String, ImageBitmap?>()

    fun get(context: android.content.Context, key: String): ImageBitmap? = cache.getOrPut(key) {
        runCatching {
            context.assets.open("icons/$key.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }
}

private val METAL_CODES = setOf("XAU", "XAG", "XPT", "XPD", "XCU")

private fun assetKey(code: String): String = when {
    code in METAL_CODES -> "metal_" + code.lowercase()
    code == "OIL" -> "energy_oil"
    code == "GAS" -> "energy_gas"
    code.startsWith("#") -> "flag_" + code.drop(1).lowercase()
    code.startsWith("@") -> "stock_" + code.drop(1).lowercase()
    CCY_COUNTRY.containsKey(code) -> "flag_" + CCY_COUNTRY.getValue(code).lowercase()
    else -> "coin_" + code.lowercase()
}

/**
 * Round badge for one icon code: a currency (its country's flag), "#XX" a country flag, a crypto
 * coin logo, "@TICKER" a company logo, a metal coin, or OIL / GAS. Real logos are bundled in
 * assets/icons; anything missing falls back to a drawn badge.
 */
@Composable
fun Badge(code: String, size: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(code) { IconCache.get(context.applicationContext, assetKey(code)) }
    val country = when {
        code.startsWith("#") -> code.drop(1)
        else -> CCY_COUNTRY[code]
    }
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .border(1.dp, AppColors.Background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
            country != null -> Canvas(Modifier.size(size.dp)) { drawFlag(country) }
            code.startsWith("@") -> {
                val ticker = code.drop(1)
                Box(Modifier.size(size.dp).background(STOCK_COLORS[ticker] ?: AppColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Text(ticker.take(1), color = Color.White, fontSize = (size * 0.46).sp, fontWeight = FontWeight.Bold)
                }
            }
            code == "OIL" || code == "GAS" -> Canvas(Modifier.size(size.dp)) { drawEnergy(code == "GAS") }
            else -> {
                val c = COINS[code] ?: Coin(code.take(1), AppColors.SurfaceHigh)
                Box(Modifier.size(size.dp).background(c.bg), contentAlignment = Alignment.Center) {
                    Text(c.text, color = c.fg, fontSize = (size * if (c.text.length > 1) 0.36 else 0.46).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** Pairs: base top-left, quote bottom-right drawn on top (like broker watchlists); one badge otherwise. */
@Composable
fun InstrumentIcon(instrument: Instrument, size: Int = 36) {
    val icons = instrument.icons
    if (icons.size >= 2) {
        val small = (size * 0.74).toInt()
        Box(Modifier.size(size.dp)) {
            Badge(icons[0], small)
            Badge(icons[1], small, Modifier.offset((size - small).dp, (size - small).dp))
        }
    } else {
        Badge(icons.first(), size)
    }
}

private fun DrawScope.stripesH(colors: List<Color>) {
    val h = size.height / colors.size
    colors.forEachIndexed { i, c -> drawRect(c, Offset(0f, i * h), Size(size.width, h + 1)) }
}

private fun DrawScope.stripesV(colors: List<Color>) {
    val w = size.width / colors.size
    colors.forEachIndexed { i, c -> drawRect(c, Offset(i * w, 0f), Size(w + 1, size.height)) }
}

private fun DrawScope.star(cx: Float, cy: Float, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = Math.toRadians((i * 36 - 90).toDouble())
        val x = cx + (rr * cos(a)).toFloat()
        val y = cy + (rr * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

private fun DrawScope.unionJack(w: Float, h: Float) {
    val blue = Color(0xFF012169)
    val red = Color(0xFFC8102E)
    drawRect(blue, Offset.Zero, Size(w, h))
    drawLine(Color.White, Offset(0f, 0f), Offset(w, h), h * 0.2f)
    drawLine(Color.White, Offset(w, 0f), Offset(0f, h), h * 0.2f)
    drawLine(red, Offset(0f, 0f), Offset(w, h), h * 0.07f)
    drawLine(red, Offset(w, 0f), Offset(0f, h), h * 0.07f)
    drawRect(Color.White, Offset(w * 0.4f, 0f), Size(w * 0.2f, h))
    drawRect(Color.White, Offset(0f, h * 0.36f), Size(w, h * 0.28f))
    drawRect(red, Offset(w * 0.45f, 0f), Size(w * 0.1f, h))
    drawRect(red, Offset(0f, h * 0.42f), Size(w, h * 0.16f))
}

private fun DrawScope.nordicCross(bg: Color, cross: Color, border: Color? = null) {
    drawRect(bg)
    val w = size.width
    val h = size.height
    if (border != null) {
        drawRect(border, Offset(w * 0.26f, 0f), Size(w * 0.24f, h))
        drawRect(border, Offset(0f, h * 0.38f), Size(w, h * 0.24f))
    }
    drawRect(cross, Offset(w * 0.3f, 0f), Size(w * 0.16f, h))
    drawRect(cross, Offset(0f, h * 0.42f), Size(w, h * 0.16f))
}

private fun DrawScope.drawFlag(country: String) {
    val w = size.width
    val h = size.height
    val red = Color(0xFFD52B1E)
    when (country) {
        "US" -> {
            for (i in 0 until 7) drawRect(if (i % 2 == 0) Color(0xFFB22234) else Color.White, Offset(0f, i * h / 7), Size(w, h / 7 + 1))
            drawRect(Color(0xFF3C3B6E), Offset.Zero, Size(w * 0.5f, h * 0.54f))
            for (r in 0 until 3) for (c in 0 until 3) drawCircle(Color.White, w * 0.03f, Offset(w * (0.09f + c * 0.15f), h * (0.1f + r * 0.16f)))
        }
        "EU" -> {
            drawRect(Color(0xFF003399))
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                star(w / 2 + (w * 0.3f * cos(a)).toFloat(), h / 2 + (h * 0.3f * sin(a)).toFloat(), w * 0.055f, Color(0xFFFFCC00))
            }
        }
        "GB" -> unionJack(w, h)
        "JP" -> {
            drawRect(Color.White)
            drawCircle(Color(0xFFBC002D), w * 0.28f)
        }
        "CH" -> {
            drawRect(red)
            drawRect(Color.White, Offset(w * 0.42f, h * 0.2f), Size(w * 0.16f, h * 0.6f))
            drawRect(Color.White, Offset(w * 0.2f, h * 0.42f), Size(w * 0.6f, h * 0.16f))
        }
        "AU", "NZ" -> {
            drawRect(Color(0xFF012169))
            val save = drawContext.transform
            save.translate(0f, 0f)
            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, w * 0.5f, h * 0.5f)
            unionJack(w * 0.5f, h * 0.5f)
            drawContext.canvas.restore()
            val starColor = if (country == "AU") Color.White else Color(0xFFC8102E)
            star(w * 0.72f, h * 0.28f, w * 0.06f, starColor)
            star(w * 0.82f, h * 0.5f, w * 0.06f, starColor)
            star(w * 0.68f, h * 0.76f, w * 0.07f, starColor)
            star(w * 0.27f, h * 0.75f, w * 0.09f, Color.White)
        }
        "CA" -> {
            stripesV(listOf(red, Color.White, Color.White, red))
            star(w / 2, h / 2, w * 0.18f, red)
        }
        "HK" -> {
            drawRect(Color(0xFFDE2910))
            for (i in 0 until 5) {
                val a = Math.toRadians(i * 72.0 - 90)
                drawCircle(Color.White, w * 0.08f, Offset(w / 2 + (w * 0.17f * cos(a)).toFloat(), h / 2 + (h * 0.17f * sin(a)).toFloat()))
            }
        }
        "SG" -> {
            stripesH(listOf(Color(0xFFEF3340), Color.White))
            drawCircle(Color.White, w * 0.16f, Offset(w * 0.3f, h * 0.26f))
            drawCircle(Color(0xFFEF3340), w * 0.14f, Offset(w * 0.36f, h * 0.26f))
        }
        "TR" -> {
            drawRect(Color(0xFFE30A17))
            drawCircle(Color.White, w * 0.24f, Offset(w * 0.42f, h / 2))
            drawCircle(Color(0xFFE30A17), w * 0.19f, Offset(w * 0.48f, h / 2))
            star(w * 0.68f, h / 2, w * 0.09f, Color.White)
        }
        "ZA" -> {
            stripesH(listOf(Color(0xFFE03C31), Color(0xFF007749), Color(0xFF001489)))
            val p = Path().apply { moveTo(0f, 0f); lineTo(w * 0.45f, h / 2); lineTo(0f, h); close() }
            drawPath(p, Color(0xFF000000))
            drawPath(p, Color(0xFFFFB612), style = Stroke(w * 0.05f))
        }
        "MX" -> {
            stripesV(listOf(Color(0xFF006847), Color.White, Color(0xFFCE1126)))
            drawCircle(Color(0xFF8C6239), w * 0.1f)
        }
        "NO" -> nordicCross(Color(0xFFBA0C2F), Color(0xFF00205B), Color.White)
        "SE" -> nordicCross(Color(0xFF006AA7), Color(0xFFFECC00))
        "PL" -> stripesH(listOf(Color.White, Color(0xFFDC143C)))
        "TH" -> {
            val c = listOf(Color(0xFFA51931), Color.White, Color(0xFF2D2A4A), Color(0xFF2D2A4A), Color.White, Color(0xFFA51931))
            stripesH(c)
        }
        "IN" -> {
            stripesH(listOf(Color(0xFFFF9933), Color.White, Color(0xFF138808)))
            drawCircle(Color(0xFF000080), w * 0.1f, style = Stroke(w * 0.03f))
        }
        "DE" -> stripesH(listOf(Color.Black, Color(0xFFDD0000), Color(0xFFFFCE00)))
        "FR" -> stripesV(listOf(Color(0xFF0055A4), Color.White, Color(0xFFEF4135)))
        else -> drawRect(AppColors.SurfaceHigh)
    }
}

private fun DrawScope.drawEnergy(gas: Boolean) {
    val w = size.width
    val h = size.height
    drawRect(if (gas) Color(0xFF0B3D91) else Color(0xFF1C1C1E))
    val drop = Path().apply {
        moveTo(w * 0.5f, h * 0.18f)
        cubicTo(w * 0.72f, h * 0.45f, w * 0.74f, h * 0.58f, w * 0.7f, h * 0.66f)
        cubicTo(w * 0.62f, h * 0.84f, w * 0.38f, h * 0.84f, w * 0.3f, h * 0.66f)
        cubicTo(w * 0.26f, h * 0.58f, w * 0.28f, h * 0.45f, w * 0.5f, h * 0.18f)
        close()
    }
    drawPath(drop, if (gas) Color(0xFF4DA3FF) else Color.White)
}
