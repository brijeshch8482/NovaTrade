package com.novatrade.demo.ui.deposit

import com.novatrade.demo.Brand

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.ui.common.Coin
import com.novatrade.demo.ui.common.Stepper
import com.novatrade.demo.ui.common.toPriceOrNull
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.usd
import kotlinx.coroutines.delay

/** A crypto deposit method: coin plus the network it travels on. */
data class CryptoMethod(val code: String, val name: String, val network: String?, val decimals: Int)

val CRYPTO_METHODS = listOf(
    CryptoMethod("BTC", "Bitcoin (BTC)", null, 8),
    CryptoMethod("BNB", "BNB (BNB)", null, 8),
    CryptoMethod("ETH", "Ether (ETH)", null, 8),
    CryptoMethod("USDT", "Tether (USDT BEP20)", "BNB", 2),
    CryptoMethod("USDT", "Tether (USDT ERC20)", "ETH", 2),
    CryptoMethod("USDT", "Tether (USDT TRC20)", "TRX", 2),
    CryptoMethod("TRX", "Tronix (TRX)", null, 6),
    CryptoMethod("USDC", "USD Coin (USDC BEP20)", "BNB", 2),
    CryptoMethod("USDC", "USD Coin (USDC ERC20)", "ETH", 2),
)

enum class DepositStart { WALLET, METHODS }

enum class PaymentMode(val title: String) { DEPOSIT("Deposit"), WITHDRAW("Withdrawal") }

internal val PageBg = AppColors.Background
private val CardBorder = Color(0xFF2A343D)
internal val FieldBorder = Color(0xFF3A444D)
private val BannerBg = Color(0xFF0E3A22)

/** "Choose payment method" sheet shown when depositing to a real account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodSheet(onCryptoWallet: () -> Unit, onAllMethods: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.SheetBg) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Choose payment method", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.SheetCard),
            ) {
                MethodRow(Icons.Outlined.AccountBalanceWallet, "Crypto wallet", "0.00 USD", onCryptoWallet)
                HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(start = 56.dp))
                MethodRow(Icons.Outlined.FileDownload, "All payment methods", null, onAllMethods)
            }
        }
    }
}

@Composable
private fun MethodRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        value?.let { Text(it, fontSize = 14.sp) }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/**
 * Full-screen payment flow modelled on a broker's crypto wallet: wallet → deposit or withdrawal form.
 * Nothing touches real money; deposits add and withdrawals remove virtual funds only.
 */
@Composable
fun DepositFlow(
    start: DepositStart,
    mode: PaymentMode,
    accounts: List<Account>,
    activeNumber: Long,
    freeMarginOf: (Account) -> Double,
    onDeposit: (accountNumber: Long, amount: Double) -> Unit,
    /** Returns null on success, or the reason it failed. */
    onWithdraw: (accountNumber: Long, amount: Double) -> String?,
    onMessage: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var method by remember { mutableStateOf<CryptoMethod?>(if (start == DepositStart.METHODS) CRYPTO_METHODS[5] else null) }
    var formMode by remember { mutableStateOf(mode) }
    var loadingFor by remember { mutableStateOf<Pair<CryptoMethod, PaymentMode>?>(null) }
    var receipt by remember { mutableStateOf<WithdrawReceipt?>(null) }

    BackHandler {
        when {
            receipt != null -> onClose()
            method != null && start == DepositStart.WALLET -> method = null
            else -> onClose()
        }
    }

    // Brief loader between pages, like a web-backed payment screen.
    loadingFor?.let { target ->
        LaunchedEffect(target) {
            delay(700)
            method = target.first
            formMode = target.second
            loadingFor = null
        }
    }

    Column(modifier.fillMaxSize().background(PageBg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppColors.Background)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { onMessage("Support chat is not available in the demo") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = "Support chat")
            }
        }

        when {
            receipt != null -> WithdrawDonePage(receipt!!, onDone = onClose)
            loadingFor != null -> LoadingDots()
            method == null -> WalletPage(
                onDeposit = { loadingFor = it to PaymentMode.DEPOSIT },
                onWithdraw = { loadingFor = it to PaymentMode.WITHDRAW },
                onMessage = onMessage,
            )
            formMode == PaymentMode.WITHDRAW -> WithdrawPage(
                method = method!!,
                accounts = accounts,
                activeNumber = activeNumber,
                freeMarginOf = freeMarginOf,
                onMethodChange = { method = it },
                onWithdraw = { number, amount ->
                    onWithdraw(number, amount).also { error ->
                        if (error == null) receipt = WithdrawReceipt(amount, method!!, accounts.first { it.number == number }, System.currentTimeMillis())
                    }
                },
                onMessage = onMessage,
            )
            else -> DepositPage(
                method = method!!,
                accounts = accounts,
                activeNumber = activeNumber,
                onMethodChange = { method = it },
                onDeposit = onDeposit,
                onMessage = onMessage,
            )
        }
    }
}

@Composable
internal fun LoadingDots() {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(90)
            step = (step + 1) % 8
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(8) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.4f)),
            )
        }
    }
}

@Composable
private fun WalletPage(onDeposit: (CryptoMethod) -> Unit, onWithdraw: (CryptoMethod) -> Unit, onMessage: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BannerBg)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⁂", fontSize = 30.sp, color = AppColors.TextPrimary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("How to deposit with crypto", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("In ${Brand.name} every deposit is virtual money for practice.", fontSize = 12.sp, color = AppColors.TextPrimary.copy(alpha = 0.85f))
                }
            }
        }
        item {
            Column {
                Text("Crypto wallet", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text("Total balance", fontSize = 13.sp, color = AppColors.TextSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("0", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(".00 USD", fontSize = 18.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    listOf("Accounts", "External wallets").forEachIndexed { i, t ->
                        Column(
                            Modifier.clickable { tab = i }.padding(end = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                t,
                                fontSize = 14.sp,
                                color = if (tab == i) AppColors.TextPrimary else AppColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                            Box(Modifier.height(2.dp).width(90.dp).background(if (tab == i) AppColors.TextPrimary else Color.Transparent))
                        }
                    }
                }
                HorizontalDivider(color = AppColors.Divider)
            }
        }
        if (tab == 1) {
            item {
                Text(
                    "No external wallets connected.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, FieldBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Account name", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            items(CRYPTO_METHODS, key = { it.name }) { m ->
                CoinCard(m, onDeposit = { onDeposit(m) }, onWithdraw = { onWithdraw(m) }, onMessage = onMessage)
            }
        }
    }
}

@Composable
internal fun MethodIcon(m: CryptoMethod, size: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Coin(m.code, size)
        m.network?.let { Coin(it, size) }
    }
}

@Composable
private fun CoinCard(m: CryptoMethod, onDeposit: () -> Unit, onWithdraw: () -> Unit, onMessage: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MethodIcon(m, 16)
            Spacer(Modifier.width(8.dp))
            Text(m.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("0." + "0".repeat(m.decimals), fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(" " + m.code, fontSize = 15.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text("≈ 0.00 USD", fontSize = 13.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WalletAction("Deposit", com.novatrade.demo.ui.common.DepositCircle, primary = true, onClick = onDeposit)
            WalletAction("Withdrawal", com.novatrade.demo.ui.common.WithdrawCircle, onClick = onWithdraw)
            WalletAction("Transfer", Icons.Outlined.SwapHoriz) { onMessage("No ${m.code} in the crypto wallet to transfer") }
        }
    }
}

@Composable
private fun WalletAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (primary) AppColors.Accent else AppColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (primary) AppColors.OnAccent else AppColors.TextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepositPage(
    method: CryptoMethod,
    accounts: List<Account>,
    activeNumber: Long,
    onMethodChange: (CryptoMethod) -> Unit,
    onDeposit: (Long, Double) -> Unit,
    onMessage: (String) -> Unit,
) {
    var methodMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    // Real accounts first; default to the selected one.
    val targets = accounts.filter { !it.archived }.sortedBy { it.type != AccountType.REAL }
    var target by remember { mutableLongStateOf(activeNumber) }
    var showVirtual by remember { mutableStateOf(false) }
    val account = targets.firstOrNull { it.number == target } ?: targets.first()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Deposit", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "See all payment methods",
            color = AppColors.Accent,
            fontSize = 14.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { methodMenu = true }.padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(16.dp))

        Label("Payment method")
        Box {
            Field(onClick = { methodMenu = true }) {
                MethodIcon(method, 20)
                Spacer(Modifier.width(10.dp))
                Text(method.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                CRYPTO_METHODS.forEach { m ->
                    DropdownMenuItem(
                        leadingIcon = { MethodIcon(m, 18) },
                        text = { Text(m.name, fontWeight = if (m == method) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            methodMenu = false
                            onMethodChange(m)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Label("To account")
        Box {
            Field(onClick = { accountMenu = true }) {
                Text(
                    "${account.kind} #${account.number} (${account.type.label})",
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(usd(account.balance), fontSize = 13.sp)
            }
            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                targets.forEach { a ->
                    DropdownMenuItem(
                        text = { Text("${a.kind} #${a.number} (${a.type.label}) · ${usd(a.balance)}") },
                        onClick = {
                            accountMenu = false
                            target = a.number
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Funds will be credited to the selected account in account currency.", fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { showVirtual = true },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
        ) { Text("Continue", fontSize = 15.sp) }

        HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 24.dp))
        Text("Terms", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        TermRow("Average payment time", "Instant")
        TermRow("Fee", "0%")
        Spacer(Modifier.height(20.dp))
        Text("FAQ", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        listOf("Learn more about crypto", "How do I buy crypto from exchanges?", "How do I verify my crypto address?").forEach { q ->
            Text(
                q,
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onMessage("Help articles are not available in the demo") }
                    .padding(vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showVirtual) {
        VirtualDepositSheet(
            account = account,
            method = method,
            onDismiss = { showVirtual = false },
            onConfirm = { amount ->
                showVirtual = false
                onDeposit(account.number, amount)
            },
        )
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun Field(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, FieldBorder, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun TermRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 13.sp)
    }
}

/** Bottom sheet that adds practice money; there is no real payment anywhere in the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VirtualDepositSheet(account: Account, method: CryptoMethod, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amountText by remember { mutableStateOf("1000") }
    val amount = amountText.toPriceOrNull()?.takeIf { it > 0 }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.SheetBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Virtual deposit", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Practice money only. No crypto is sent and nothing is charged. It is added to ${account.kind} #${account.number} (${account.type.label}).",
                fontSize = 13.sp,
                color = AppColors.TextSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodIcon(method, 18)
                Spacer(Modifier.width(8.dp))
                Text("via ${method.name}", fontSize = 13.sp, color = AppColors.TextSecondary)
            }
            Stepper(amountText, { amountText = it }, 100.0, 2, 1000.0, "Amount in USD", min = 0.0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 500, 1000, 5000, 10000).forEach { v ->
                    OutlinedButton(
                        onClick = { amountText = v.toString() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (v >= 1000) "${v / 1000}K" else "$v", color = AppColors.TextPrimary, fontSize = 12.sp) }
                }
            }
            Button(
                onClick = { amount?.let(onConfirm) },
                enabled = amount != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
            ) { Text("Deposit ${amount?.let(::usd) ?: ""}", fontWeight = FontWeight.SemiBold) }
        }
    }
}
