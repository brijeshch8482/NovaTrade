package com.novatrade.demo.ui.accounts

import com.novatrade.demo.Brand

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.ui.theme.AppColors
import kotlinx.coroutines.delay

/** Account products offered when opening an account. */
private data class Product(val name: String, val tagline: String, val minDeposit: String, val spread: String, val leverage: String, val commission: String, val tint: Color)

/** Minimum spread of an account kind, as shown on its details page, e.g. "0.2". */
internal fun minSpreadOf(kind: String): String =
    PRODUCTS.firstOrNull { it.name.equals(kind, ignoreCase = true) }?.spread?.removeSuffix(" pips") ?: "0.2"

/** Largest commission per side of an account kind, e.g. "0.00 USD" or "3.50 USD". */
internal fun commissionOf(kind: String): String {
    val text = PRODUCTS.firstOrNull { it.name.equals(kind, ignoreCase = true) }?.commission ?: return "0.00 USD"
    return Regex("""(\d+\.\d+) USD""").find(text)?.let { it.groupValues[1] + " USD" } ?: "0.00 USD"
}

private val PRODUCTS = listOf(
    Product("Standard", "Smaller lots, lower risk. Great for practicing.", "10 USD", "0.2 pips", "1:Unlimited", "No commission", Color(0xFF7B5CC4)),
    Product("Standard Cent", "Trade in cents to test strategies with tiny volumes.", "10 USD", "0.3 pips", "1:Unlimited", "No commission", Color(0xFF3F8CC6)),
    Product("Pro", "Instant execution and low spreads for experienced traders.", "200 USD", "0.1 pips", "1:Unlimited", "No commission", Color(0xFFC48A2F)),
    Product("Raw Spread", "Raw spreads from 0.0 pips with a fixed commission.", "200 USD", "0.0 pips", "1:Unlimited", "Up to 3.50 USD each side per lot", Color(0xFF2FA37A)),
    Product("Zero", "Zero spread on the top instruments most of the day.", "200 USD", "0.0 pips", "1:Unlimited", "From 0.05 USD each side per lot", Color(0xFFC44B5C)),
)

private enum class Step { PRODUCT, PLATFORM, SETUP, NICKNAME }

private val LEVERAGE_OPTIONS = listOf(50, 100, 200, 500, 1000, 2000)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OpenAccountFlow(
    onCreate: (type: AccountType, kind: String, platform: String, leverage: Int, nickname: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(Step.PRODUCT) }
    val pager = rememberPagerState { PRODUCTS.size }
    var platform by remember { mutableStateOf(Brand.name) }
    var type by remember { mutableStateOf(AccountType.REAL) }
    var leverage by remember { mutableIntStateOf(2000) }
    var nickname by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val product = PRODUCTS[pager.currentPage]

    BackHandler {
        when (step) {
            Step.PRODUCT -> onClose()
            Step.PLATFORM -> step = Step.PRODUCT
            Step.SETUP -> step = Step.PLATFORM
            Step.NICKNAME -> step = Step.SETUP
        }
    }
    if (creating) {
        LaunchedEffect(Unit) {
            delay(900)
            onCreate(type, product.name, platform, leverage, nickname)
        }
    }

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                when (step) {
                    Step.PRODUCT -> onClose()
                    Step.PLATFORM -> step = Step.PRODUCT
                    Step.SETUP -> step = Step.PLATFORM
                    Step.NICKNAME -> step = Step.SETUP
                }
            }) {
                Icon(if (step == Step.PRODUCT) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                when (step) {
                    Step.PRODUCT -> "Open account"
                    Step.PLATFORM -> "Select a platform"
                    Step.SETUP -> "Set up your account"
                    Step.NICKNAME -> "Nickname"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when (step) {
            Step.PRODUCT -> {
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 10.dp,
                    modifier = Modifier.weight(1f),
                ) { page -> ProductCard(PRODUCTS[page]) }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(PRODUCTS.size) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (i == pager.currentPage) AppColors.TextPrimary else AppColors.SurfaceHigh),
                        )
                    }
                }
                Text(
                    "Contract specifications",
                    color = AppColors.Accent,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { sheet = "specs" }.padding(vertical = 10.dp),
                )
                YellowButton("Continue") { step = Step.PLATFORM }
            }
            Step.PLATFORM -> Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Each account is linked to only one platform. Choose the one that best suits your trading.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                PlatformRow(Brand.name, "Simple and intuitive—great for all traders.\nUse with the ${Brand.name} app.", recommended = true, link = "More about ${Brand.name} platform") {
                    platform = Brand.name
                    step = Step.SETUP
                }
                HorizontalDivider(color = AppColors.Divider)
                PlatformRow("MT5", "Advanced and flexible.\nUse with MetaTrader 5 (desktop and mobile).") {
                    platform = "MT5"
                    step = Step.SETUP
                }
                HorizontalDivider(color = AppColors.Divider)
                PlatformRow("MT4", "Only for specific use cases.\nUse with MetaTrader 4 (desktop and mobile).") {
                    platform = "MT4"
                    step = Step.SETUP
                }
            }
            Step.SETUP -> {
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, AppColors.Divider, RoundedCornerShape(8.dp))
                            .padding(3.dp),
                    ) {
                        AccountType.entries.forEach { t ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (t == type) AppColors.SurfaceHigh else Color.Transparent)
                                    .clickable { type = t },
                                contentAlignment = Alignment.Center,
                            ) { Text(t.label, fontSize = 14.sp) }
                        }
                    }
                    Text(
                        if (type == AccountType.REAL) "Trade with real money. Withdraw anytime. (In ${Brand.name}, real accounts use virtual funds too.)"
                        else "Practice trading with virtual funds, risk-free.",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
                    )
                    SetupRow("Type") {
                        AccountChip(product.name, type)
                        Spacer(Modifier.width(6.dp))
                        AccountChip(platform, type)
                    }
                    SetupRow("Currency", value = "USD", onClick = { sheet = "currency" })
                    SetupRow("Max leverage", value = "1:$leverage", onClick = { sheet = "leverage" })
                    SetupRow("Nickname", sub = "Optional", value = nickname.ifBlank { null }, onClick = { step = Step.NICKNAME })
                }
                YellowButton(if (creating) null else "Create account") { creating = true }
            }
            Step.NICKNAME -> NicknamePage(nickname) {
                nickname = it
                step = Step.SETUP
            }
        }
    }

    when (sheet) {
        "specs" -> SpecsSheet { sheet = null }
        "currency" -> ChoiceSheet("Currency", listOf("USD"), "USD", { sheet = null }) { sheet = null }
        "leverage" -> ChoiceSheet("Max leverage", LEVERAGE_OPTIONS.map { "1:$it" }, "1:$leverage", { sheet = null }) {
            leverage = it.removePrefix("1:").toInt()
            sheet = null
        }
    }
}

@Composable
private fun ProductCard(p: Product) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.size(110.dp)) {
            // A tilted 3D-looking tile in the product's colour.
            val w = size.width
            val top = Path().apply { moveTo(w * 0.1f, w * 0.42f); lineTo(w * 0.62f, w * 0.12f); lineTo(w * 0.92f, w * 0.34f); lineTo(w * 0.4f, w * 0.66f); close() }
            val side = Path().apply { moveTo(w * 0.4f, w * 0.66f); lineTo(w * 0.92f, w * 0.34f); lineTo(w * 0.9f, w * 0.56f); lineTo(w * 0.4f, w * 0.88f); close() }
            val front = Path().apply { moveTo(w * 0.1f, w * 0.42f); lineTo(w * 0.4f, w * 0.66f); lineTo(w * 0.4f, w * 0.88f); lineTo(w * 0.1f, w * 0.64f); close() }
            drawPath(top, Brush.linearGradient(listOf(p.tint.copy(alpha = 0.95f), p.tint.copy(alpha = 0.6f)), Offset.Zero, Offset(w, w)))
            drawPath(side, p.tint.copy(alpha = 0.45f))
            drawPath(front, p.tint.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(12.dp))
        Text(p.name, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            p.name,
            fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.SurfaceHigh).padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(p.tagline, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        SpecLine("Min deposit", p.minDeposit)
        SpecLine("Min spread", p.spread)
        SpecLine("Max leverage", p.leverage)
        SpecLine("Commission", p.commission)
    }
}

/** "Label .......... value" with a dotted leader, like a spec sheet. */
@Composable
private fun SpecLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = 13.sp)
        Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 4.dp)) {
            drawLine(
                AppColors.TextSecondary, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)),
            )
        }
        Text(value, fontSize = 13.sp, color = AppColors.TextSecondary)
    }
}

@Composable
private fun PlatformRow(name: String, text: String, recommended: Boolean = false, link: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 15.sp)
                if (recommended) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Recommended",
                        fontSize = 11.sp,
                        color = AppColors.DemoChipText,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.DemoChipBg).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(text, fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
            if (link != null) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(link, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

@Composable
// Pass onClick by name: a trailing lambda here would land in [trailing] and run while composing.
private fun SetupRow(label: String, sub: String? = null, value: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 15.sp)
                sub?.let { Text(it, fontSize = 11.sp, color = AppColors.TextSecondary) }
            }
            trailing?.invoke()
            value?.let { Text(it, fontSize = 15.sp) }
            if (onClick != null) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
        HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
    }
}

@Composable
private fun SetupRow(label: String, trailing: @Composable () -> Unit) = SetupRow(label, sub = null, value = null, onClick = null, trailing = { Row { trailing() } })

private val FORBIDDEN = "<>\"&?*#@"

@Composable
private fun NicknamePage(current: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    val invalid = text.any { it in FORBIDDEN }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text("Give this account a custom name to find it faster.", fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(36) },
                singleLine = true,
                textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(AppColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (invalid) AppColors.Sell else AppColors.Divider, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
            Row(Modifier.padding(top = 6.dp)) {
                Text(
                    "Nicknames can't contain special characters: $FORBIDDEN",
                    fontSize = 11.sp,
                    color = if (invalid) AppColors.Sell else AppColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text("${text.length}/36", fontSize = 11.sp, color = AppColors.TextSecondary)
            }
        }
        YellowButton("Save changes", enabled = !invalid) { onSave(text.trim()) }
    }
}

@Composable
private fun YellowButton(text: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) AppColors.Accent else AppColors.SurfaceHigh)
            .clickable(enabled = enabled && text != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (text == null) {
            CircularProgressIndicator(color = AppColors.OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(text, color = if (enabled) AppColors.OnAccent else AppColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(title: String, options: List<String>, selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            options.forEach { o ->
                Row(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(o, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (o == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.Accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpecsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("Contract specifications", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            Row(Modifier.padding(vertical = 6.dp)) {
                Text("Instrument", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
                Text("Contract", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.width(90.dp))
                Text("Leverage", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
            }
            Instruments.byPopularity.forEach { inst ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(inst.display, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(inst.contractSize.toLong().toString(), fontSize = 13.sp, modifier = Modifier.width(90.dp))
                    Text("1:${inst.maxLeverage}", fontSize = 13.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}
