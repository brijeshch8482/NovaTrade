package com.novatrade.demo.ui.accounts

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountMetrics
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.model.Transaction
import com.novatrade.demo.model.TransactionKind
import com.novatrade.demo.trading.Notice
import com.novatrade.demo.trading.OrderResult
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.common.DepositCircle
import com.novatrade.demo.ui.common.WithdrawCircle
import com.novatrade.demo.ui.deposit.DepositStart
import com.novatrade.demo.ui.deposit.PaymentMethodSheet
import com.novatrade.demo.ui.deposit.PaymentMode
import com.novatrade.demo.ui.instrument.InstrumentLeverageSheet
import com.novatrade.demo.ui.instrument.TransferSheet
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.dateTime
import com.novatrade.demo.util.percent
import com.novatrade.demo.util.usd

private enum class DetailsSheet { PAYMENT, DEMO_WITHDRAW, TRANSFER, LEVERAGE, LOG, RENAME }

private val InfoBg = Color(0xFF042546)

/**
 * Full-screen account details from the Details button, like the broker app: a Funds tab with the
 * account figures and money actions, and a Settings tab with the account's set-up.
 */
@Composable
fun AccountDetailsPage(
    account: Account,
    metrics: AccountMetrics,
    accounts: List<Account>,
    notifications: List<Notice>,
    engine: TradingEngine,
    onResult: (OrderResult) -> Unit,
    onOpenDeposit: (DepositStart, PaymentMode) -> Unit,
    onDemoDeposit: () -> Unit,
    onOpenAccount: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<DetailsSheet?>(null) }
    var history by remember { mutableStateOf(false) }

    if (history) {
        TransactionHistoryPage(account, onClose = { history = false }, modifier = modifier)
        return
    }
    BackHandler(onBack = onClose)

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            Text(account.nickname.ifBlank { account.kind }, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Funds", "Settings").forEachIndexed { i, t ->
                Column(Modifier.weight(1f).clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t, fontSize = 14.sp, color = if (i == tab) AppColors.TextPrimary else AppColors.TextSecondary, modifier = Modifier.padding(vertical = 13.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (i == tab) AppColors.TextPrimary else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tab == 0) {
                Card {
                    DetailRow("Balance", usd(metrics.balance))
                    DetailRow("Equity", usd(metrics.equity))
                    DetailRow("Floating P/L", usd(metrics.floatingProfit))
                    DetailRow("Margin", usd(metrics.margin))
                    DetailRow("Free margin", usd(metrics.freeMargin))
                    DetailRow("Margin level", metrics.marginLevel?.let(::percent) ?: "—")
                    DetailRow("Leverage", "1:${account.leverage}")
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppColors.Surface).padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    RoundAction("Deposit", DepositCircle) {
                        if (account.type == AccountType.DEMO) onDemoDeposit() else sheet = DetailsSheet.PAYMENT
                    }
                    RoundAction("Withdraw", WithdrawCircle) {
                        if (account.type == AccountType.DEMO) sheet = DetailsSheet.DEMO_WITHDRAW
                        else onOpenDeposit(DepositStart.METHODS, PaymentMode.WITHDRAW)
                    }
                    RoundAction("History", Icons.Outlined.History) { history = true }
                    RoundAction("Transfer", Icons.Filled.SwapHoriz) { sheet = DetailsSheet.TRANSFER }
                }
            } else {
                Card {
                    DetailRow("Type") {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AccountChip(account.platform, account.type)
                            AccountChip(account.kind, account.type)
                            AccountChip(account.type.label, account.type)
                        }
                    }
                    DetailRow("Number", "#${account.number}")
                    DetailRow("Nickname", account.nickname.ifBlank { account.kind }, onClick = { sheet = DetailsSheet.RENAME })
                }
                SectionTitle("${account.kind} account")
                Card {
                    DetailRow("Commission per side up to", commissionOf(account.kind))
                    DetailRow("Minimum spread", minSpreadOf(account.kind))
                    DetailRow("Max leverage", "1:${account.leverage}", onClick = { sheet = DetailsSheet.LEVERAGE })
                }
                SectionTitle("Trading platform — ${account.platform}")
                Card {
                    DetailRow("Trading log", null, onClick = { sheet = DetailsSheet.LOG })
                }
                val mt = account.platform.startsWith("MT")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(InfoBg).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF4A8EF0), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (mt) "This account works only on MetaTrader ${account.platform.removePrefix("MT")}."
                            else "This account works only on ${account.platform} Terminal and the ${account.platform} app.",
                            fontSize = 14.sp,
                        )
                    }
                    Box(
                        Modifier
                            .padding(start = 30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0B1B2E))
                            .clickable(onClick = onOpenAccount)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(if (mt) "Switch to ${com.novatrade.demo.Brand.name}" else "Switch to MT5", fontSize = 13.sp) }
                }
            }
        }
    }

    when (sheet) {
        DetailsSheet.PAYMENT -> PaymentMethodSheet(
            onCryptoWallet = {
                sheet = null
                onOpenDeposit(DepositStart.WALLET, PaymentMode.DEPOSIT)
            },
            onAllMethods = {
                sheet = null
                onOpenDeposit(DepositStart.METHODS, PaymentMode.DEPOSIT)
            },
            onDismiss = { sheet = null },
        )
        DetailsSheet.DEMO_WITHDRAW -> AmountSheet("Withdraw", "Available: ${usd(metrics.freeMargin.coerceAtLeast(0.0))}", onDismiss = { sheet = null }) { amount ->
            val r = engine.withdraw(amount)
            if (r is OrderResult.Success) sheet = null
            onResult(r)
        }
        DetailsSheet.TRANSFER -> TransferSheet(accounts, account.number, engine, onResult) { sheet = null }
        DetailsSheet.LEVERAGE -> InstrumentLeverageSheet(account.leverage, onPick = {
            engine.setLeverage(it)
            sheet = null
        }) { sheet = null }
        DetailsSheet.LOG -> NotificationsSheet(notifications) { sheet = null }
        DetailsSheet.RENAME -> RenameDialog(account.nickname.ifBlank { account.kind }, onDismiss = { sheet = null }) {
            engine.rename(account.number, it)
            sheet = null
        }
        null -> Unit
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppColors.Surface).padding(vertical = 4.dp)) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String?, onClick: (() -> Unit)? = null) =
    DetailRow(label, onClick) { value?.let { Text(it, fontSize = 14.sp) } }

@Composable
private fun DetailRow(label: String, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        value()
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RoundAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(AppColors.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp)
    }
}

private const val FORBIDDEN = "<>\"&?*#@"

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    val invalid = text.any { it in FORBIDDEN }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SheetCard,
        title = { Text("Nickname") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(36) },
                    singleLine = true,
                    isError = invalid,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.Accent, cursorColor = AppColors.Accent),
                )
                Text(
                    "Nicknames can't contain special characters: $FORBIDDEN",
                    fontSize = 11.sp,
                    color = if (invalid) AppColors.Sell else AppColors.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = !invalid) { Text("Save", color = if (invalid) AppColors.TextSecondary else AppColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.TextPrimary) } },
    )
}

private enum class Period(val label: String, val days: Int?) { WEEK("Last 7 days", 7), MONTH("Last 30 days", 30), QUARTER("Last 90 days", 90), ALL("All time", null) }

/** Deposits, withdrawals and transfers of one account, with a period and a type filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionHistoryPage(account: Account, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var period by remember { mutableStateOf(Period.WEEK) }
    var periodMenu by remember { mutableStateOf(false) }
    var kinds by remember { mutableStateOf(TransactionKind.entries.toSet()) }
    var filter by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    val since = period.days?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L
    val shown = account.transactions.filter { it.time >= since && it.kind in kinds }
    val filtered = period != Period.WEEK || kinds.size != TransactionKind.entries.size

    Column(modifier.fillMaxSize().background(AppColors.SheetBg)) {
        IconButton(onClick = onClose, modifier = Modifier.padding(4.dp)) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Transaction history", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    FilterChipButton(period.label, Icons.Outlined.CalendarMonth, active = true, arrow = true) { periodMenu = true }
                    DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                        Period.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(p.label) }, onClick = {
                                period = p
                                periodMenu = false
                            })
                        }
                    }
                }
                FilterChipButton("Filter", Icons.Outlined.FilterAlt, active = kinds.size != TransactionKind.entries.size) { filter = true }
            }
            Spacer(Modifier.height(20.dp))
            if (shown.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("No transaction matches your filters", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Try changing your search terms", fontSize = 13.sp, color = AppColors.TextSecondary)
                    if (filtered) {
                        Spacer(Modifier.height(18.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppColors.Accent)
                                .clickable {
                                    period = Period.WEEK
                                    kinds = TransactionKind.entries.toSet()
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) { Text("Reset filters", fontSize = 14.sp, color = AppColors.OnAccent) }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppColors.Surface)) {
                    shown.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
                        TransactionRow(t)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (filter) {
        ModalBottomSheet(onDismissRequest = { filter = false }, containerColor = AppColors.SheetBg) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("Transaction type", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                TransactionKind.entries.forEach { k ->
                    Row(
                        Modifier.fillMaxWidth().clickable { kinds = if (k in kinds) kinds - k else kinds + k }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(k.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (k in kinds) Icon(Icons.Filled.Check, contentDescription = "Shown", tint = AppColors.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipButton(text: String, icon: ImageVector, active: Boolean, arrow: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFF34424F) else Color.Transparent)
            .border(1.dp, if (active) Color(0xFF34424F) else AppColors.Divider, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp)
        if (arrow) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TransactionRow(t: Transaction) {
    val incoming = t.kind == TransactionKind.DEPOSIT || t.kind == TransactionKind.TRANSFER_IN
    val title = when (t.kind) {
        TransactionKind.TRANSFER_IN -> "Transfer from #${t.otherAccount}"
        TransactionKind.TRANSFER_OUT -> "Transfer to #${t.otherAccount}"
        else -> t.kind.label
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(AppColors.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(
                when (t.kind) {
                    TransactionKind.DEPOSIT -> DepositCircle
                    TransactionKind.WITHDRAWAL -> WithdrawCircle
                    else -> Icons.Filled.SwapHoriz
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(dateTime(t.time), fontSize = 12.sp, color = AppColors.TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (incoming) "+" else "−") + usd(t.amount), fontSize = 15.sp, color = if (incoming) AppColors.Profit else AppColors.TextPrimary)
            Text("Done", fontSize = 12.sp, color = AppColors.Profit)
        }
    }
}
