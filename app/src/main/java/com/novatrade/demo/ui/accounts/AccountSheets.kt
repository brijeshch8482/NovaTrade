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
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.novatrade.demo.trading.OrderResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
    val shown = accounts.filter { it.type == type && !it.archived }
    val archived = accounts.filter { it.type == type && it.archived }
    var archivedOpen by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var archiveInfo by remember { mutableStateOf(false) }
    /** Message and whether it reports a success (green) rather than information (blue). */
    var toast by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3_000)
            toast = null
        }
    }

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
            LazyColumn(Modifier.weight(1f)) {
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
                    val selected = acc.number == activeNumber
                    // Swiping left offers Archive; the selected account stays put.
                    SwipeAction("Archive", Icons.Outlined.Inventory2, enabled = !selected, onAction = {
                        when (val r = engine.archive(acc.number)) {
                            is OrderResult.Success -> toast = r.message to false
                            is OrderResult.Error -> toast = r.message to false
                        }
                    }) {
                        SwitcherRow(acc, com.novatrade.demo.ui.common.balanceText(engine.metricsFor(acc, quotes).equity), selected) { onSelect(acc.number) }
                    }
                }
                if (archived.isNotEmpty()) {
                    item(key = "archivedHeader") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { archivedOpen = !archivedOpen }
                                .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Archived accounts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "About archived accounts",
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.clip(CircleShape).clickable { archiveInfo = true }.padding(6.dp).size(18.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (archivedOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (archivedOpen) "Hide archived accounts" else "Show archived accounts",
                            )
                        }
                    }
                    if (archivedOpen) {
                        items(archived, key = { "a${it.number}" }) { acc ->
                            SwipeAction("Restore", Icons.Outlined.Unarchive, enabled = !restoring, onAction = {
                                restoring = true
                                scope.launch {
                                    delay(900)
                                    val r = engine.restore(acc.number)
                                    restoring = false
                                    toast = when (r) {
                                        is OrderResult.Success -> r.message to true
                                        is OrderResult.Error -> r.message to false
                                    }
                                }
                            }) {
                                SwitcherRow(acc, "Unavailable", selected = false, onClick = null)
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                val t = toast
                if (t != null) {
                    SwitcherToast(t.first, success = t.second)
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.SheetCard)
                            .clickable(enabled = !restoring, onClick = onCreate),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (restoring) {
                            CircularProgressIndicator(color = AppColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Open account", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    if (archiveInfo) {
        AlertDialog(
            onDismissRequest = { archiveInfo = false },
            containerColor = AppColors.SheetCard,
            title = { Text("Archived accounts") },
            text = { Text("Archived accounts are hidden from your account lists and can't be used for trading. Their history is kept. Swipe an account to the left to archive or restore it.") },
            confirmButton = { TextButton(onClick = { archiveInfo = false }) { Text("OK", color = AppColors.TextPrimary) } },
        )
    }
}

/** One account in the switcher; the selected one sits on a card. */
@Composable
private fun SwitcherRow(acc: Account, balance: String, selected: Boolean, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppColors.SheetCard else AppColors.SheetBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            Text(acc.nickname.ifBlank { acc.kind }, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(balance, fontSize = 16.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountChip(acc.platform, acc.type)
            AccountChip(acc.kind, acc.type)
            Text("# ${acc.number}", fontSize = 13.sp)
        }
    }
}

/**
 * Row that slides left to uncover one action button at its right edge, like the broker's account list.
 * The button grows with the swipe, so nothing shows behind rounded corners at rest.
 */
@Composable
private fun SwipeAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidth = 64.dp
    val max = with(density) { actionWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (offset.value < -0.5f) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(with(density) { (-offset.value).toDp() })
                    .fillMaxHeight()
                    .background(AppColors.Toggle)
                    .clickable {
                        scope.launch { offset.animateTo(0f) }
                        onAction()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 12.sp, color = Color.White, maxLines = 1, softWrap = false)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-max, 0f)) }
                    },
                    onDragStopped = { velocity ->
                        offset.animateTo(if (offset.value < -max / 2 || velocity < -1_000f) -max else 0f)
                    },
                ),
        ) { content() }
    }
}

/** Confirmation shown in place of the Open account button. */
@Composable
private fun SwitcherToast(message: String, success: Boolean) {
    val accent = if (success) Color(0xFF3FC28A) else Color(0xFF4A8EF0)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (success) Color(0xFF042410) else Color(0xFF042546))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (success) Icons.Outlined.CheckCircle else Icons.Outlined.Info, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, fontSize = 15.sp, color = Color.White)
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
