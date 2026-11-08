package com.novatrade.demo.ui.instrument

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountMetrics
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Side
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.accounts.AccountChip
import com.novatrade.demo.ui.common.Stepper
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.lots
import com.novatrade.demo.util.usd

/** Small saved choices for the chart screen: favourite drawing tools and one-click trading. */
class ToolPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("chart_tools", Context.MODE_PRIVATE)

    fun favorites(): List<String> = prefs.getString("favorites", "")!!.split(",").filter { it.isNotBlank() }

    fun saveFavorites(ids: List<String>) = prefs.edit().putString("favorites", ids.joinToString(",")).apply()

    fun oneClick(): Boolean = prefs.getBoolean("oneClick", false)

    fun saveOneClick(on: Boolean) = prefs.edit().putBoolean("oneClick", on).apply()
}

@Composable
fun rememberToolPrefs(): ToolPrefs {
    val context = LocalContext.current
    return remember { ToolPrefs(context.applicationContext) }
}

/** Cost of crossing the spread, in USD, for [volume] lots. */
fun feeFor(engine: TradingEngine, inst: Instrument, quote: Quote, volume: Double): Double =
    quote.spread * engine.usdPerPoint(inst, volume)

/** "Demo  10,000.00 USD" capsule shown at the top of the chart screen. */
@Composable
fun AccountCapsule(account: Account, metrics: AccountMetrics, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, AppColors.Divider, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountChip(account.type.label, account.type)
        Spacer(Modifier.width(8.dp))
        Text(com.novatrade.demo.ui.common.balanceText(metrics.equity), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
    }
}

/** Order form that replaces the Sell/Buy bar: order mode, volume, confirm, fees and margin. */
@Composable
fun InlineOrderPanel(
    side: Side,
    price: String?,
    volumeText: String,
    onVolumeChange: (String) -> Unit,
    oneClick: Boolean,
    onOneClickChange: (Boolean) -> Unit,
    fee: Double?,
    margin: Double?,
    leverage: Int,
    onAdvanced: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var modeMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val color = if (side == Side.BUY) AppColors.Buy else AppColors.Sell
    val volume = volumeText.replace(',', '.').toDoubleOrNull()

    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.SheetBg)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(bottom = 2.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF4A535C)))
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { modeMenu = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (oneClick) "One-click" else "Regular", fontSize = 14.sp)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Regular", fontWeight = if (!oneClick) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            modeMenu = false
                            onOneClickChange(false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("One-click", fontWeight = if (oneClick) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            modeMenu = false
                            onOneClickChange(true)
                        },
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AppColors.SheetCard)
                    .clickable(onClick = onAdvanced),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = "Order settings", modifier = Modifier.size(18.dp))
            }
        }
        Text("Volume", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        Stepper(
            volumeText, onVolumeChange, 0.01, 2, 0.01, "0.01",
            modifier = Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp)),
            min = TradingEngine.MIN_LOT,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.SheetCard)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", fontSize = 14.sp) }
            Column(
                Modifier
                    .weight(2f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .clickable(enabled = price != null && volume != null, onClick = onConfirm),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Confirm ${side.label} ${volume?.let(::lots) ?: "—"} lots", color = Color.White, fontSize = 13.sp, lineHeight = 15.sp)
                Text(price ?: "—", color = Color.White, fontSize = 12.sp, lineHeight = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Fees: ~ ${fee?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—"} USD  |  ",
                fontSize = 11.sp,
                color = AppColors.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(3) { Box(Modifier.size(width = 3.dp, height = 9.dp).background(AppColors.Sell)) }
            }
            Text(
                " Margin: ${margin?.let(::usd) ?: "—"} (1:$leverage)",
                fontSize = 11.sp,
                color = AppColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.Info,
                contentDescription = "About fees and margin",
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(18.dp).clickable { info = true },
            )
        }
    }

    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            containerColor = AppColors.SheetCard,
            title = { Text("Fees and margin", fontSize = 17.sp) },
            text = {
                Text(
                    "Fees are the cost of the spread for this volume. Margin is the part of your free funds held while the " +
                        "position is open, based on your leverage. Everything here uses virtual money.",
                    fontSize = 14.sp,
                )
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text("OK", color = AppColors.Accent) } },
        )
    }
}

@Composable
fun NotEnoughMoneyDialog(volume: Double, demoEmpty: Boolean, onDismiss: () -> Unit, onDeposit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SheetCard,
        title = { Text("Not Enough Money", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                if (demoEmpty) "Please top up your demo account to start trading."
                else "Margin required for the entered order volume (${lots(volume)} lot) exceeds available free margin on your account.",
                fontSize = 14.sp,
            )
        },
        confirmButton = { TextButton(onClick = onDeposit) { Text("Deposit", color = AppColors.TextPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.TextPrimary) } },
    )
}
