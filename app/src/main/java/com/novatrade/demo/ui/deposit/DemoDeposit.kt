package com.novatrade.demo.ui.deposit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.ui.common.toPriceOrNull
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.usd
import kotlinx.coroutines.delay

private const val MAX_DEMO_DEPOSIT = 10_000_000_000.0

/** Demo top-up: "Enter deposit amount", then a "Deposit Status" receipt. */
@Composable
fun DemoDepositScreen(onDeposit: (Double) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Double?>(null) }
    val focus = remember { FocusRequester() }
    val amount = text.replace(",", "").toPriceOrNull()
    val valid = amount != null && amount > 0 && amount <= MAX_DEMO_DEPOSIT

    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { if (done == null) runCatching { focus.requestFocus() } }
    if (sending) {
        LaunchedEffect(Unit) {
            delay(800)
            onDeposit(amount!!)
            done = amount
            sending = false
        }
    }

    done?.let {
        DepositStatus(it, onClose, modifier)
        return
    }

    Column(modifier.fillMaxSize().background(AppColors.Background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Deposit", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(60.dp))
            Text("Enter deposit amount", fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    BasicTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(14) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(AppColors.Accent),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).focusRequester(focus),
                    )
                    HorizontalDivider(color = AppColors.TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Text("USD", fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                "0.00 – 10,000,000,000.00 USD",
                fontSize = 12.sp,
                color = if (amount != null && !valid) AppColors.Sell else AppColors.TextPrimary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (valid) AppColors.Accent else AppColors.SurfaceHigh)
                .clickable(enabled = valid && !sending) { sending = true },
            contentAlignment = Alignment.Center,
        ) {
            if (sending) {
                CircularProgressIndicator(color = AppColors.OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Text("Continue", color = if (valid) AppColors.OnAccent else AppColors.TextSecondary, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun DepositStatus(amount: Double, onClose: () -> Unit, modifier: Modifier) {
    BackHandler(onBack = onClose)
    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            Text("Deposit Status", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
        Text("Deposit is in Your Account!", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text(
            "Your deposit is accepted. You can start trading now.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(24.dp))
        Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().background(AppColors.Surface).padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
                Box(Modifier.size(7.dp).clip(CircleShape).background(AppColors.Profit))
                Spacer(Modifier.width(6.dp))
                Text("Accepted", fontSize = 12.sp, color = AppColors.Profit)
            }
            Row {
                Text("Transaction amount", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(usd(amount), fontSize = 12.sp)
            }
        }
        // Torn-receipt zigzag edge under the card.
        Canvas(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(8.dp)) {
            val teeth = 30
            val w = size.width / teeth
            val p = Path().apply {
                moveTo(0f, 0f)
                for (i in 0 until teeth) {
                    lineTo(i * w + w / 2, size.height)
                    lineTo((i + 1) * w, 0f)
                }
                close()
            }
            drawPath(p, AppColors.Surface)
        }
    }
}
