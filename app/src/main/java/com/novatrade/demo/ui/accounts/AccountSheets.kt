package com.novatrade.demo.ui.accounts

import com.novatrade.demo.Brand

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.model.Quote
import com.novatrade.demo.trading.Notice
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.instrument.Segmented
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.dateTime
import com.novatrade.demo.util.usd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    accounts: List<Account>,
    activeNumber: Long,
    quotes: Map<String, Quote>,
    engine: TradingEngine,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val activeType = accounts.first { it.number == activeNumber }.type
    var type by remember { mutableStateOf(activeType) }
    val shown = accounts.filter { it.type == type }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.SheetBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.76f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TypeToggle(type) { type = it }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            "No ${type.label.lowercase()} accounts yet",
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(shown, key = { it.number }) { acc ->
                    val selected = acc.number == activeNumber && shown.size > 1
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.SheetCard)
                            .border(1.dp, if (selected) AppColors.Accent.copy(alpha = 0.6f) else AppColors.SheetCard, RoundedCornerShape(12.dp))
                            .clickable { onSelect(acc.number) }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row {
                            Text(acc.nickname.ifBlank { acc.kind }, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(com.novatrade.demo.ui.common.balanceText(engine.metricsFor(acc, quotes).equity), fontSize = 15.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AccountChip(Brand.name, acc.type)
                            AccountChip(acc.kind, acc.type)
                            Text("# ${acc.number}", fontSize = 12.sp)
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SheetCard)
                    .clickable(onClick = onCreate),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Open account", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Outlined two-part Real | Demo switch. */
@Composable
private fun TypeToggle(selected: AccountType, onSelect: (AccountType) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF3A444D), RoundedCornerShape(10.dp))
            .padding(4.dp),
    ) {
        AccountType.entries.forEach { t ->
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (t == selected) AppColors.SheetCard else Color.Transparent)
                    .clickable { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateAccountSheet(onCreate: (AccountType, Int) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(AccountType.DEMO) }
    var leverage by remember { mutableIntStateOf(TradingEngine.DEFAULT_LEVERAGE) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Open new account", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Segmented(AccountType.entries.map { it.label }, AccountType.entries.indexOf(type)) { type = AccountType.entries[it] }
            Text(
                if (type == AccountType.DEMO) {
                    "Demo accounts start with 10,000.00 USD of virtual money."
                } else {
                    "Real accounts start at 0.00 USD. Use Deposit to add virtual money; nothing real is ever charged."
                },
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )
            Text("Leverage", color = AppColors.TextSecondary, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TradingEngine.LEVERAGES.forEach { lev ->
                    FilterChip(
                        selected = leverage == lev,
                        onClick = { leverage = lev },
                        label = { Text("1:$lev") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Accent,
                            selectedLabelColor = AppColors.OnAccent,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onCreate(type, leverage) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent),
            ) { Text("Create ${type.label} account", fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(notifications: List<Notice>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            Text(
                "Notifications",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (notifications.isEmpty()) {
                Text(
                    "No notifications yet. Trades, SL/TP hits and deposits will show up here.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(notifications) { n ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(n.message, fontSize = 14.sp)
                        Text(dateTime(n.time), color = AppColors.TextSecondary, fontSize = 11.sp)
                    }
                    HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    }
}
