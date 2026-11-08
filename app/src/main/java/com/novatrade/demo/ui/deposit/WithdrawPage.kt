package com.novatrade.demo.ui.deposit

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.ui.common.toPriceOrNull
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.dateTime
import com.novatrade.demo.util.money
import com.novatrade.demo.util.usd
import kotlin.random.Random

data class WithdrawReceipt(val amount: Double, val method: CryptoMethod, val account: Account, val time: Long) {
    /** Transaction id shown on the receipt; derived from the time so it stays stable. */
    val id: String get() = "W" + (time % 100_000_000).toString().padStart(8, '0')
}

private const val MIN_WITHDRAW = 1.0

/** Withdrawal form: method, source account, amount, then a confirmation sheet. */
@Composable
internal fun WithdrawPage(
    method: CryptoMethod,
    accounts: List<Account>,
    activeNumber: Long,
    freeMarginOf: (Account) -> Double,
    onMethodChange: (CryptoMethod) -> Unit,
    onWithdraw: (Long, Double) -> String?,
    onMessage: (String) -> Unit,
) {
    var methodMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    val sources = accounts.filter { !it.archived }.sortedBy { it.type != AccountType.REAL }
    var source by remember { mutableLongStateOf(activeNumber) }
    val account = sources.firstOrNull { it.number == source } ?: sources.first()
    val available = freeMarginOf(account).coerceAtLeast(0.0)
    var amountText by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    val amount = amountText.toPriceOrNull()
    val error = when {
        amountText.isBlank() || amount == null -> null
        amount < MIN_WITHDRAW -> "Minimum amount is ${usd(MIN_WITHDRAW)}"
        amount > available -> "Not enough funds. Available: ${usd(available)}"
        else -> null
    }
    val canContinue = amount != null && error == null && amount >= MIN_WITHDRAW

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
    ) {
        Text("Withdrawal", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
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

        Label("From account")
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
                sources.forEach { a ->
                    DropdownMenuItem(
                        text = { Text("${a.kind} #${a.number} (${a.type.label}) · ${usd(a.balance)}") },
                        onClick = {
                            accountMenu = false
                            source = a.number
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Label("Amount")
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, if (error != null) AppColors.Sell else FieldBorder, RoundedCornerShape(4.dp))
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (amountText.isEmpty()) Text("0.00", fontSize = 15.sp, color = AppColors.TextSecondary)
                BasicTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(AppColors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("USD", fontSize = 14.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text(
                "Max",
                fontSize = 13.sp,
                color = AppColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { amountText = String.format(java.util.Locale.US, "%.2f", available) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Text(
            error ?: "Available for withdrawal: ${usd(available)}",
            fontSize = 12.sp,
            color = if (error != null) AppColors.Sell else AppColors.TextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { confirm = true },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Accent,
                contentColor = AppColors.OnAccent,
                disabledContainerColor = AppColors.SheetCard,
                disabledContentColor = AppColors.TextSecondary,
            ),
        ) { Text("Continue", fontSize = 15.sp) }

        HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 24.dp))
        Text("Terms", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        TermRow("Processing time", "Instant")
        TermRow("Fee", "0%")
        TermRow("Limits", "${money(MIN_WITHDRAW)} – ${money(available.coerceAtLeast(MIN_WITHDRAW))} USD")
        Spacer(Modifier.height(20.dp))
        Text("FAQ", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        listOf("Why can't I withdraw my full balance?", "How long does a withdrawal take?", "Which payment methods can I withdraw to?").forEach { q ->
            Text(
                q,
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable {
                        onMessage(
                            if (q.startsWith("Why")) "Funds used as margin for open positions can't be withdrawn until those positions are closed"
                            else "Help articles are not available in the demo",
                        )
                    }
                    .padding(vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirm && amount != null) {
        ConfirmWithdrawSheet(
            amount = amount,
            method = method,
            account = account,
            onDismiss = { confirm = false },
            onConfirm = {
                confirm = false
                onWithdraw(account.number, amount)?.let(onMessage)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmWithdrawSheet(amount: Double, method: CryptoMethod, account: Account, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    // A pretend wallet address, so the summary looks complete; nothing is ever sent anywhere.
    val address = remember(method) {
        val prefix = when (method.network ?: method.code) {
            "BTC" -> "bc1q"
            "TRX" -> "T"
            else -> "0x"
        }
        prefix + List(30) { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.SheetBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Confirm withdrawal", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SheetCard)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryRow("Amount", usd(amount))
                SummaryRow("Fee", "0.00 USD")
                SummaryRow("You will receive", usd(amount))
                HorizontalDivider(color = AppColors.Divider)
                SummaryRow("Payment method", method.name)
                SummaryRow("From account", "${account.kind} #${account.number}")
                SummaryRow("Wallet address", address.take(8) + "…" + address.takeLast(6))
            }
            Text(
                "Virtual withdrawal: the amount is removed from your practice balance. No crypto is sent.",
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
            ) { Text("Confirm", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Withdrawal request submitted" page shown after confirming. */
@Composable
internal fun WithdrawDonePage(receipt: WithdrawReceipt, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(AppColors.Profit.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.Profit, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Withdrawal request submitted", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "${usd(receipt.amount)} has been withdrawn from ${receipt.account.kind} #${receipt.account.number}.",
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SheetCard)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryRow("Status", "Completed")
            SummaryRow("Amount", usd(receipt.amount))
            SummaryRow("Payment method", receipt.method.name)
            SummaryRow("Transaction ID", receipt.id)
            SummaryRow("Date", dateTime(receipt.time))
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
        ) { Text("Done", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
    }
}
