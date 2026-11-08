package com.novatrade.demo.ui.profile

import com.novatrade.demo.Brand

import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ModeNight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmsFailed
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novatrade.demo.data.ProfilePrefs
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.usd
import kotlinx.coroutines.delay

private val GreenPillBg = AppColors.DemoChipBg
private val GreenPillText = AppColors.DemoChipText
private val GreyPillBg = AppColors.RealChipBg

private enum class Info { VERIFICATION, SWAP_FREE, HMR, NBP, REFERRAL, COPY, SUPPORT_HUB, TICKET, LIVECHAT, SUGGEST, LEGAL, RATE, UPDATE, ABOUT }

@Composable
fun ProfileScreen(
    accounts: List<Account>,
    engine: TradingEngine,
    profile: ProfilePrefs,
    onSettings: () -> Unit,
    onCryptoWallet: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name by profile.name.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf<Info?>(null) }
    var moreBenefits by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    LaunchedEffect(Unit) {
        delay(350)
        loading = false
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                    }
                    Text("Profile", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                }
            }
            item {
                Section("Verification")
                Row1(Icons.Outlined.Person, "Verification", pill = "Fully verified" to true) { info = Info.VERIFICATION }
                Section("Benefits")
                if (loading) {
                    repeat(3) { SkeletonRow() }
                } else {
                    Row1(Icons.Outlined.ModeNight, "Swap-free", pill = "Qualified" to true) { info = Info.SWAP_FREE }
                    Row1(Icons.Outlined.ViewWeek, "Flexible HMR", pill = "Extended" to false) { info = Info.HMR }
                    if (moreBenefits) {
                        Row1(Icons.Outlined.Shield, "Negative Balance Protection") { info = Info.NBP }
                    } else {
                        Text(
                            "Show more",
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(AppColors.Surface)
                                .clickable { moreBenefits = true }
                                .padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                Section("Crypto wallet")
                Row1(Icons.Outlined.AccountBalanceWallet, "Balance", value = "0.00 USD", onClick = onCryptoWallet)
                Section("Referral program")
                Row1(
                    Icons.Outlined.Redeem, "Earn a stable income by introducing clients to ${Brand.name}",
                    iconBg = Color(0xFF2C2547), iconTint = Color(0xFFB79CFF),
                ) { info = Info.REFERRAL }
                Section("Copy Trading", sub = "For investors")
                Row1(Icons.Outlined.Work, "Investment wallet", value = "0.00 USD", iconBg = Color(0xFF16382B), iconTint = Color(0xFF4CD08A)) { info = Info.COPY }
                Section("Support")
                Row1(Icons.Outlined.FavoriteBorder, "Support hub", sub = "Find answers to your questions") { info = Info.SUPPORT_HUB }
                Row1(Icons.Outlined.SmsFailed, "Open a ticket", sub = "Fill the request form and we'll get back to you") { info = Info.TICKET }
                Row1(Icons.Outlined.ChatBubbleOutline, "LiveChat", sub = "Feel free to contact our customer support") { info = Info.LIVECHAT }
                Row1(Icons.Outlined.Lightbulb, "Suggest a feature", sub = "Help us become better") { info = Info.SUGGEST }
                Row1(Icons.Outlined.AssignmentTurnedIn, "Legal Documents", sub = "${Brand.name} Practice") { info = Info.LEGAL }
                Row1(Icons.Outlined.ThumbUp, "Rate the app", sub = "Tell us what you think of ${Brand.name}") { info = Info.RATE }
                Row1(Icons.Outlined.DownloadForOffline, "Update the app", sub = "Download the latest version of the app") { info = Info.UPDATE }
                Row1(Icons.Outlined.Info, "About the app") { info = Info.ABOUT }
                HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                Row1(
                    Icons.AutoMirrored.Outlined.Logout, "Log Out", sub = "forex@gmail.com",
                    iconBg = Color(0xFF3A1F22), iconTint = AppColors.Sell,
                ) { confirmLogout = true }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (collapsed) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Background)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = AppColors.SheetCard,
            title = { Text("Log out?") },
            text = { Text("Your accounts and trades stay saved on this device. Sign in again to continue trading.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    profile.logOut()
                }) { Text("Log Out", color = AppColors.Sell) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel", color = AppColors.TextPrimary) } },
        )
    }

    info?.let { which ->
        when (which) {
            Info.TICKET -> FormSheet("Open a ticket", "Describe your question and we'll get back to you in Notifications.", "Submit request", true, { info = null }) {
                info = null
                onMessage("Ticket #${(System.currentTimeMillis() % 100_000)} created. We'll reply in Notifications.")
            }
            Info.SUGGEST -> FormSheet("Suggest a feature", "What would make ${Brand.name} better for you?", "Send suggestion", false, { info = null }) {
                info = null
                onMessage("Thanks! Your suggestion was sent.")
            }
            Info.LIVECHAT -> LiveChatSheet { info = null }
            Info.RATE -> RateSheet({ info = null }) { stars ->
                info = null
                onMessage("Thanks for rating ${Brand.name} $stars★")
            }
            Info.REFERRAL -> InfoSheet(
                "Referral program",
                "Invite friends to practice trading with ${Brand.name}. Share the app and learn together, no money involved.",
                action = "Share ${Brand.name}",
                onAction = { ctx ->
                    ctx.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "I'm practicing trading with virtual money on ${Brand.name}. Join me!"),
                            "Share ${Brand.name}",
                        ),
                    )
                },
            ) { info = null }
            else -> {
                val (title, body) = infoText(which, accounts)
                InfoSheet(title, body) { info = null }
            }
        }
    }
}

private fun infoText(which: Info, accounts: List<Account>): Pair<String, String> = when (which) {
    Info.VERIFICATION -> "Verification" to "Your profile is fully verified. All deposit and withdrawal methods are available. Everything in ${Brand.name} uses virtual money."
    Info.SWAP_FREE -> "Swap-free" to "Qualified. Positions held overnight are never charged or paid swaps, on every instrument."
    Info.HMR -> "Flexible HMR" to "Extended. Hedged margin requirements are relaxed, so opposite positions on the same instrument need less margin."
    Info.NBP -> "Negative Balance Protection" to "If a fast market move pushes your balance below zero, it is reset to zero. You can never lose more than you deposited."
    Info.COPY -> "Investment wallet" to "Copy Trading lets you follow strategies of other traders. Your investment wallet balance: 0.00 USD."
    Info.SUPPORT_HUB -> "Support hub" to "• Everything here is practice money: deposits, withdrawals and trades are virtual.\n\n" +
        "• Crypto and gold prices stream live from Binance. Forex, metals, energies, indices and stocks come from Yahoo Finance; between updates small simulated ticks move the price.\n\n" +
        "• Markets close like real ones: forex on weekends, stocks outside US hours. Crypto trades 24/7.\n\n" +
        "• Margin = volume × contract size × price ÷ leverage. Stop out happens below 30% margin level."
    Info.LEGAL -> "Legal Documents" to "${Brand.name} is a practice app for learning to trade. It is not a broker, holds no funds and gives no financial advice. " +
        "Market data: Binance, Yahoo Finance, Forex Factory and FXStreet. Charts: TradingView Lightweight Charts™ (Apache License 2.0)."
    Info.UPDATE -> "Update the app" to "You're using the latest version of ${Brand.name} (1.0)."
    Info.ABOUT -> "About the app" to "${Brand.name} 1.0\n\nAccounts on this device: ${accounts.size} (${accounts.count { it.type == AccountType.REAL }} real, ${accounts.count { it.type == AccountType.DEMO }} demo)\n\n" +
        "Balance across accounts: ${usd(accounts.sumOf { it.balance })}"
    else -> "" to ""
}

@Composable
private fun Section(title: String, sub: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        sub?.let { Text(it, fontSize = 11.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun Row1(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    value: String? = null,
    pill: Pair<String, Boolean>? = null,
    iconBg: Color = AppColors.Surface,
    iconTint: Color = AppColors.TextPrimary,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            sub?.let { Text(it, fontSize = 11.sp, color = AppColors.TextSecondary) }
        }
        pill?.let { (text, green) ->
            Text(
                text,
                fontSize = 11.sp,
                color = if (green) GreenPillText else AppColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (green) GreenPillBg else GreyPillBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        value?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun SkeletonRow() {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(AppColors.Surface))
        Spacer(Modifier.width(14.dp))
        Column {
            Box(Modifier.size(width = 90.dp, height = 12.dp).background(AppColors.Surface))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(width = 60.dp, height = 8.dp).background(AppColors.Surface))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun YellowButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent, contentColor = AppColors.OnAccent,
            disabledContainerColor = AppColors.SheetCard, disabledContentColor = AppColors.TextSecondary,
        ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun InfoSheet(
    title: String,
    body: String,
    action: String? = null,
    onAction: ((android.content.Context) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Sheet(onDismiss) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(body, fontSize = 14.sp, color = AppColors.TextSecondary)
        if (action != null && onAction != null) YellowButton(action) { onAction(context) }
    }
}

@Composable
private fun FormSheet(title: String, hint: String, button: String, withSubject: Boolean, onDismiss: () -> Unit, onSubmit: () -> Unit) {
    var subject by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    Sheet(onDismiss) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, fontSize = 13.sp, color = AppColors.TextSecondary)
        val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.Accent, cursorColor = AppColors.Accent)
        if (withSubject) {
            OutlinedTextField(subject, { subject = it.take(60) }, label = { Text("Subject") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(text, { text = it.take(500) }, label = { Text("Message") }, minLines = 4, colors = colors, modifier = Modifier.fillMaxWidth())
        YellowButton(button, enabled = text.isNotBlank() && (!withSubject || subject.isNotBlank()), onClick = onSubmit)
    }
}

@Composable
private fun RateSheet(onDismiss: () -> Unit, onRate: (Int) -> Unit) {
    var stars by remember { mutableIntStateOf(0) }
    Sheet(onDismiss) {
        Text("Rate the app", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("How do you like ${Brand.name}?", fontSize = 13.sp, color = AppColors.TextSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { i ->
                Icon(
                    if (i <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "$i stars",
                    tint = if (i <= stars) AppColors.Accent else AppColors.TextSecondary,
                    modifier = Modifier.size(44.dp).clickable { stars = i }.padding(4.dp),
                )
            }
        }
        YellowButton("Submit", enabled = stars > 0) { onRate(stars) }
    }
}

private data class ChatLine(val mine: Boolean, val text: String)

/** Simple support chat with an assistant that answers common questions about the app. */
@Composable
private fun LiveChatSheet(onDismiss: () -> Unit) {
    val lines = remember { mutableStateListOf(ChatLine(false, "Hi! I'm the ${Brand.name} assistant. Ask me about deposits, margin, leverage, stop out or market hours.")) }
    var text by remember { mutableStateOf("") }
    Sheet(onDismiss) {
        Text("LiveChat", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines) { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.mine) Arrangement.End else Arrangement.Start) {
                    Text(
                        line.text,
                        fontSize = 13.sp,
                        color = if (line.mine) AppColors.OnAccent else AppColors.TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (line.mine) AppColors.Accent else AppColors.SheetCard)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it.take(200) }, placeholder = { Text("Type a message") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.Accent, cursorColor = AppColors.Accent),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                val q = text.trim()
                if (q.isNotEmpty()) {
                    lines += ChatLine(true, q)
                    lines += ChatLine(false, answer(q))
                    text = ""
                }
            }) { Text("Send", color = AppColors.Accent) }
        }
    }
}

private fun answer(q: String): String {
    val s = q.lowercase()
    return when {
        "deposit" in s -> "Tap Deposit on your account card. Demo accounts top up instantly; real accounts go through the crypto wallet flow. It's all virtual money."
        "withdraw" in s -> "Tap Withdraw on the account card. You can withdraw free margin; funds used by open positions are locked until they close."
        "margin" in s -> "Margin = volume × contract size × price ÷ leverage. You'll see the exact margin before confirming an order."
        "leverage" in s -> "Change leverage in Profile › Settings or when you open a new account. Crypto is capped at 1:200–1:400, stocks at 1:20."
        "stop" in s -> "Stop out closes your biggest losing position when margin level falls below 30%."
        "closed" in s || "weekend" in s || "hour" in s -> "Forex, metals, energies and indices trade Sunday 22:00 to Friday 22:00 UTC. Stocks follow US hours. Crypto and gold trade 24/7."
        "price" in s || "real" in s -> "Crypto and gold stream live from Binance; other markets use Yahoo Finance prices. Money in ${Brand.name} is always virtual."
        else -> "Thanks for your message! For more, check the Support hub. Is there anything else about trading in ${Brand.name} I can help with?"
    }
}
