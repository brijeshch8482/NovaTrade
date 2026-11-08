package com.novatrade.demo.ui.profile

import com.novatrade.demo.Brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novatrade.demo.data.ProfilePrefs
import com.novatrade.demo.ui.theme.AppColors

/** PIN screen shown on start and after the app was away longer than the skip interval. */
@Composable
fun LockScreen(profile: ProfilePrefs, onBiometric: ((onSuccess: () -> Unit) -> Unit)?) {
    val biometrics by profile.biometrics.collectAsStateWithLifecycle()
    val interval by profile.pinInterval.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var intervals by remember { mutableStateOf(false) }
    val useBio = biometrics && onBiometric != null
    LaunchedEffect(useBio) { if (useBio) onBiometric?.invoke { profile.unlock() } }
    PinPad(
        title = "Enter PIN to proceed",
        subtitle = null,
        error = error,
        length = profile.pinLength,
        onComplete = { pin ->
            if (profile.checkPin(pin)) profile.unlock() else error = "Wrong PIN. Try again."
        },
        extraKey = if (useBio) {
            { Icon(Icons.Outlined.Fingerprint, contentDescription = "Use fingerprint", modifier = Modifier.size(46.dp)) }
        } else null,
        onExtra = if (useBio) ({ onBiometric?.invoke { profile.unlock() } }) else null,
        footer = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Skip PIN: " + if (interval == 0) "Immediately" else "$interval minute${if (interval == 1) "" else "s"}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { intervals = true }.padding(10.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text("Forgot PIN?", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { forgot = true }.padding(10.dp))
            }
        },
    )
    if (intervals) {
        AlertDialog(
            onDismissRequest = { intervals = false },
            containerColor = AppColors.SheetCard,
            title = { Text("Skip PIN") },
            text = {
                Column {
                    listOf(0, 1, 5, 10, 30).forEach { m ->
                        Text(
                            (if (m == interval) "✓  " else "     ") + if (m == 0) "Immediately" else "$m minute${if (m == 1) "" else "s"}",
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable { profile.setPinInterval(m); intervals = false }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }
    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            containerColor = AppColors.SheetCard,
            title = { Text("Forgot PIN?") },
            text = { Text("You'll be signed out and your PIN will be removed. Your accounts and trades stay on this device. Sign in again and set a new PIN in Settings.") },
            confirmButton = { TextButton(onClick = { forgot = false; profile.forgotPin() }) { Text("Reset PIN", color = AppColors.Accent) } },
            dismissButton = { TextButton(onClick = { forgot = false }) { Text("Cancel", color = AppColors.TextPrimary) } },
        )
    }
}

/** Shown after Log Out: the brand screen with a sign-in form. */
@Composable
fun WelcomeScreen(profile: ProfilePrefs) {
    val current by profile.name.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(current) }
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.weight(1f))
        BrandMark()
        Text("Practice trading with virtual money.\nReal markets, zero risk.", fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            label = { Text("Your name") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.Accent, cursorColor = AppColors.Accent, focusedLabelColor = AppColors.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { profile.logIn(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
        ) { Text("Sign in", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        Text("All accounts in ${Brand.name} use virtual money.", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
    }
}
