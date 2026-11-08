package com.novatrade.demo.ui.trade

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novatrade.demo.data.PriceAlerts
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.model.Quote
import com.novatrade.demo.ui.common.InstrumentIcon
import com.novatrade.demo.ui.common.Stepper
import com.novatrade.demo.ui.common.toPriceOrNull
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.dateTime
import com.novatrade.demo.util.format

@Composable
fun AlertsScreen(alerts: PriceAlerts, quotes: Map<String, Quote>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val list by alerts.alerts.collectAsStateWithLifecycle()
    var create by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Price alerts", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        if (list.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = AppColors.TextSecondary)
                Spacer(Modifier.height(12.dp))
                Text("No price alerts", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Get notified when an instrument reaches your price.", fontSize = 13.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(list, key = { it.id }) { a ->
                    val inst = Instruments[a.symbol]
                    Row(
                        Modifier
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.Surface)
                            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InstrumentIcon(inst, size = 26)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(inst.display, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Bid ${if (a.above) "≥" else "≤"} ${inst.format(a.price)} · now ${quotes[a.symbol]?.let { inst.format(it.bid) } ?: "—"}",
                                fontSize = 12.sp, color = AppColors.TextSecondary,
                            )
                            Text("Created ${dateTime(a.created)}", fontSize = 11.sp, color = AppColors.TextSecondary)
                        }
                        IconButton(onClick = { alerts.remove(a.id) }) { Icon(Icons.Filled.Close, contentDescription = "Delete alert", tint = AppColors.TextSecondary) }
                    }
                }
            }
        }
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AppColors.Accent)
                .clickable { create = true },
            contentAlignment = Alignment.Center,
        ) { Text("Create alert", color = AppColors.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
    }
    if (create) CreateAlertSheet(null, alerts, quotes) { create = false }
}

/** Sheet to create a price alert; [fixedSymbol] preselects the instrument (from the chart). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAlertSheet(
    fixedSymbol: String?,
    alerts: PriceAlerts,
    quotes: Map<String, Quote>,
    /** Starting price, e.g. where the chart's plus button was tapped; the bid otherwise. */
    initialPrice: Double? = null,
    onDone: (String?) -> Unit,
) {
    var symbol by remember { mutableStateOf(fixedSymbol ?: "XAUUSD") }
    var picker by remember { mutableStateOf(false) }
    val inst = Instruments[symbol]
    val bid = quotes[symbol]?.bid
    var priceText by remember(symbol) { mutableStateOf((initialPrice ?: bid)?.let(inst::format) ?: "") }
    val price = priceText.toPriceOrNull()
    ModalBottomSheet(onDismissRequest = { onDone(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppColors.SheetBg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create price alert", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp))
                    .clickable(enabled = fixedSymbol == null) { picker = !picker }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentIcon(inst, size = 26)
                Spacer(Modifier.width(10.dp))
                Text(inst.display, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(bid?.let(inst::format) ?: "—", fontSize = 13.sp, color = AppColors.TextSecondary)
                if (fixedSymbol == null) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            if (picker) {
                LazyColumn(Modifier.heightIn(max = 260.dp).fillMaxWidth().background(AppColors.Surface)) {
                    items(Instruments.byPopularity, key = { it.symbol }) { i ->
                        Row(Modifier.fillMaxWidth().clickable { symbol = i.symbol; picker = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            InstrumentIcon(i, size = 20)
                            Spacer(Modifier.width(10.dp))
                            Text(i.display, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
                    }
                }
            }
            Text("Alert price", fontSize = 12.sp, color = AppColors.TextSecondary)
            Stepper(priceText, { priceText = it }, inst.priceStep(bid ?: inst.fallbackPrice), inst.digits, bid, "Price", modifier = Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp)))
            val above = price != null && bid != null && price >= bid
            Text(
                if (price == null || bid == null) "Enter a price" else "You'll be notified when the bid ${if (above) "rises to" else "falls to"} ${inst.format(price)}",
                fontSize = 12.sp, color = AppColors.TextSecondary,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (price != null && bid != null) AppColors.Accent else AppColors.SurfaceHigh)
                    .clickable(enabled = price != null && bid != null) {
                        alerts.add(symbol, above, price!!)
                        onDone("Alert set: ${inst.display} ${if (above) "≥" else "≤"} ${inst.format(price)}")
                    },
                contentAlignment = Alignment.Center,
            ) { Text("Create alert", color = if (price != null) AppColors.OnAccent else AppColors.TextSecondary, fontSize = 15.sp) }
        }
    }
}
