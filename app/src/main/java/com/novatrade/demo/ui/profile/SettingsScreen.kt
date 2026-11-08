package com.novatrade.demo.ui.profile

import com.novatrade.demo.Brand

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HideSource
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novatrade.demo.data.ProfilePrefs
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.theme.AppColors

private enum class SettingsSheet { NOTIFICATIONS, LANGUAGE, APPEARANCE, SECURITY_TYPE, INTERVAL, LEVERAGE }

private val INTERVALS = listOf(0 to "Immediately", 1 to "1 minute", 5 to "5 minutes", 10 to "10 minutes", 30 to "30 minutes")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: ProfilePrefs,
    engine: TradingEngine,
    onMessage: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPin by profile.hasPin.collectAsStateWithLifecycle()
    val biometrics by profile.biometrics.collectAsStateWithLifecycle()
    val interval by profile.pinInterval.collectAsStateWithLifecycle()
    val secure by profile.secureScreen.collectAsStateWithLifecycle()
    val flip by profile.flipToHide.collectAsStateWithLifecycle()
    val notify by profile.notify.collectAsStateWithLifecycle()
    val account by engine.account.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var pinSetup by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = !pinSetup, onBack = onClose)
    if (pinSetup) {
        PinSetupScreen(onDone = { pin ->
            profile.setPin(pin)
            // Fingerprint sign-in comes on with a new PIN; it can be switched off below.
            if (pin != null) profile.setBiometrics(true)
            pinSetup = false
            onMessage(if (pin == null) "PIN removed" else "New PIN set")
        }, onCancel = { pinSetup = false }, canRemove = hasPin, modifier = modifier)
        return
    }

    Column(modifier.fillMaxSize().background(AppColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Header("Preferences")
            Item(Icons.Outlined.Notifications, "Notifications") { sheet = SettingsSheet.NOTIFICATIONS }
            Item(Icons.Outlined.Translate, "Language") { sheet = SettingsSheet.LANGUAGE }
            Item(Icons.Outlined.Palette, "Appearance", value = "Device settings") { sheet = SettingsSheet.APPEARANCE }

            Header("Security")
            Item(Icons.Outlined.Shield, "Security type", value = "Phone") { sheet = SettingsSheet.SECURITY_TYPE }
            Item(Icons.Outlined.Pin, if (hasPin) "Set new PIN" else "Set PIN") { pinSetup = true }
            Item(
                Icons.Outlined.Timer, "Skip PIN interval", sub = "Select when PIN is required",
                value = INTERVALS.firstOrNull { it.first == interval }?.second ?: "$interval minutes",
            ) { sheet = SettingsSheet.INTERVAL }
            ToggleItem(Icons.Outlined.Fingerprint, "Sign-in with biometrics", checked = biometrics) {
                if (!hasPin) onMessage("Set a PIN first to use biometrics") else profile.setBiometrics(it)
            }
            ToggleItem(Icons.Outlined.HideSource, "Hide screen content on recent tasks / disable screenshots", checked = secure) {
                profile.setSecureScreen(it)
            }
            ToggleItem(
                Icons.Outlined.VisibilityOff, "Hide balances with a flip gesture",
                sub = "Flip your device screen down to quickly hide and show balances", checked = flip,
            ) { profile.setFlipToHide(it) }

            Header("Security actions")
            Item(Icons.AutoMirrored.Outlined.Logout, "Secure my account", sub = "Log out from all devices except this one", danger = true) {
                confirm = "secure"
            }
            Item(Icons.Outlined.DeleteOutline, "Terminate ${Brand.name} Personal Area or Crypto Wallet", sub = "This action cannot be reversed.", danger = true) {
                confirm = "terminate"
            }
        }
    }

    when (sheet) {
        SettingsSheet.NOTIFICATIONS -> PickSheet("Notifications", { sheet = null }) {
            ToggleItem(Icons.Outlined.Notifications, "Trading notifications", sub = "Stop Loss, Take Profit, pending orders, stop out and price alerts", checked = notify) {
                profile.setNotify(it)
            }
        }
        SettingsSheet.LANGUAGE -> PickSheet("Language", { sheet = null }) { Choice("English", true) { sheet = null } }
        SettingsSheet.APPEARANCE -> PickSheet("Appearance", { sheet = null }) {
            Choice("Device settings", true) { sheet = null }
            Text("${Brand.name} uses its dark theme, made for charts.", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
        }
        SettingsSheet.SECURITY_TYPE -> PickSheet("Security type", { sheet = null }) {
            Choice("Phone", true) { sheet = null }
            Text("Sign-in confirmations are handled on this phone.", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
        }
        SettingsSheet.INTERVAL -> PickSheet("Skip PIN interval", { sheet = null }) {
            INTERVALS.forEach { (min, label) ->
                Choice(label, min == interval) {
                    profile.setPinInterval(min)
                    sheet = null
                }
            }
        }
        SettingsSheet.LEVERAGE -> PickSheet("Leverage", { sheet = null }) {
            TradingEngine.LEVERAGES.forEach { lev ->
                Choice("1:$lev", lev == account.leverage) {
                    engine.setLeverage(lev)
                    sheet = null
                }
            }
        }
        null -> Unit
    }

    confirm?.let { which ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = AppColors.SheetCard,
            title = { Text(if (which == "secure") "Secure my account" else "Terminate Personal Area") },
            text = {
                Text(
                    if (which == "secure") "You'll stay signed in on this device. No other devices are signed in to this profile."
                    else "All accounts, positions, history and drawings on this device will be deleted. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (which == "secure") {
                        onMessage("Your account is secured")
                    } else {
                        engine.terminate()
                        onMessage("All accounts were deleted. A fresh real and demo account were created.")
                        onClose()
                    }
                }) { Text(if (which == "secure") "Continue" else "Terminate", color = AppColors.Sell) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = AppColors.TextPrimary) } },
        )
    }
}

@Composable
private fun Header(title: String) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun IconCircle(icon: ImageVector, danger: Boolean = false) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (danger) Color(0xFF3A1F22) else AppColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) AppColors.Sell else AppColors.TextPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, sub: String? = null, value: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon, danger)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            sub?.let { Text(it, fontSize = 11.sp, color = AppColors.TextSecondary) }
        }
        value?.let { Text(it, fontSize = 14.sp) }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun ToggleItem(icon: ImageVector, title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            sub?.let { Text(it, fontSize = 11.sp, color = AppColors.TextSecondary) }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppColors.Toggle, checkedThumbColor = Color.White,
                uncheckedTrackColor = AppColors.SurfaceHigh, uncheckedThumbColor = Color(0xFF6A7178), uncheckedBorderColor = AppColors.SurfaceHigh,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.Accent)
    }
}

/** PIN length of new PINs; older 4-digit PINs keep their length. */
const val NEW_PIN_LENGTH = 6

private val KeyBg = Color(0xFF1B2026)

/** Lowercase wordmark at the top of the PIN screens. */
@Composable
fun BrandMark() {
    Text(Brand.wordmark, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, color = AppColors.TextPrimary)
}

/**
 * PIN entry like the broker app: wordmark, title, dots, round keys with fingerprint bottom-left
 * and delete bottom-right, then optional [footer] links.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String?,
    error: String?,
    onComplete: (String) -> Unit,
    extraKey: (@Composable () -> Unit)? = null,
    onExtra: (() -> Unit)? = null,
    length: Int = NEW_PIN_LENGTH,
    footer: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var pin by remember(title) { mutableStateOf("") }
    LaunchedEffect(error) { if (error != null) pin = "" }
    Column(
        modifier.fillMaxSize().background(AppColors.Background).systemBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        BrandMark()
        Spacer(Modifier.weight(0.6f))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, fontSize = 13.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(length) { i ->
                Box(Modifier.size(15.dp).clip(CircleShape).background(if (i < pin.length) AppColors.TextPrimary else Color(0xFF2E343B)))
            }
        }
        Text(error ?: "", fontSize = 13.sp, color = AppColors.Sell, modifier = Modifier.padding(top = 12.dp).height(18.dp))
        Spacer(Modifier.height(24.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("x", "0", "<"))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    row.forEach { key ->
                        val digit = key != "x" && key != "<"
                        Box(
                            Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(if (digit) KeyBg else Color.Transparent)
                                .clickable(enabled = key != "x" || extraKey != null) {
                                    when (key) {
                                        "<" -> pin = pin.dropLast(1)
                                        "x" -> onExtra?.invoke()
                                        else -> if (pin.length < length) {
                                            pin += key
                                            if (pin.length == length) onComplete(pin)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (key) {
                                "<" -> Icon(Icons.Outlined.Backspace, contentDescription = "Delete", tint = AppColors.TextSecondary, modifier = Modifier.size(34.dp))
                                "x" -> extraKey?.invoke()
                                else -> Text(key, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        footer?.invoke()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PinSetupScreen(onDone: (String?) -> Unit, onCancel: () -> Unit, canRemove: Boolean, modifier: Modifier) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onCancel)
    Box(modifier.fillMaxSize()) {
        PinPad(
            title = if (first == null) "Create PIN" else "Repeat PIN",
            subtitle = if (first == null) "You'll use it to open ${Brand.name}" else null,
            error = error,
            onComplete = { pin ->
                when {
                    first == null -> {
                        first = pin
                        error = null
                    }
                    pin == first -> onDone(pin)
                    else -> {
                        first = null
                        error = "PINs didn't match. Try again."
                    }
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.weight(1f))
            if (canRemove) TextButton(onClick = { onDone(null) }) { Text("Remove PIN", color = AppColors.Sell) }
        }
    }
}
