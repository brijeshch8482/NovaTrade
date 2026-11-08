package com.novatrade.demo.ui.trade

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AddAlarm
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novatrade.demo.data.MarketRepository
import com.novatrade.demo.model.Account
import com.novatrade.demo.model.AccountMetrics
import com.novatrade.demo.model.Category
import com.novatrade.demo.model.Instrument
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.model.Quote
import com.novatrade.demo.model.Timeframe
import com.novatrade.demo.ui.common.InstrumentIcon
import com.novatrade.demo.ui.common.Sparkline
import com.novatrade.demo.ui.instrument.AccountCapsule
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.util.format
import com.novatrade.demo.util.percent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface ListTab {
    val title: String

    data object Favorites : ListTab { override val title = "Favorites" }
    data object MostTraded : ListTab { override val title = "Most traded" }
    data object TopMovers : ListTab { override val title = "Top Movers" }
    data class Group(val category: Category) : ListTab { override val title = category.label }
    data object All : ListTab { override val title = "All" }
}

private val TABS: List<ListTab> = listOf(ListTab.Favorites, ListTab.MostTraded, ListTab.TopMovers) +
    Category.entries.map { ListTab.Group(it) } + ListTab.All

private enum class Sort(val label: String) { POPULARITY("Sort by popularity"), NAME("Sort by name"), CHANGE("Sort by daily change"), PRICE("Sort by price") }

/** Up moves are blue in watchlists, like the broker app. */
private val UpBlue = AppColors.Buy
private val CardBg = AppColors.Surface
private val Skeleton = AppColors.SurfaceHigh
private val BarsRed = Color(0xFFD93F26)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TradeListScreen(
    quotes: Map<String, Quote>,
    favorites: List<String>,
    market: MarketRepository,
    account: Account,
    metrics: AccountMetrics,
    onAccountClick: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onAlerts: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableStateOf(Sort.POPULARITY) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tab = TABS[tabIndex]

    LaunchedEffect(tabIndex) {
        // Brief skeleton when switching lists, like the broker app loading a watchlist.
        loading = true
        delay(300)
        loading = false
    }

    if (searching) {
        SearchPage(quotes, market, favorites, onToggleFavorite, onOpen, query, { query = it }) {
            searching = false
            query = ""
        }
        return
    }

    val list: List<Instrument> = when (tab) {
        ListTab.Favorites -> favorites.mapNotNull(Instruments::find)
        ListTab.MostTraded -> Instruments.byPopularity.filter { it.popularity < 999 }
        ListTab.TopMovers -> Instruments.all.filter { quotes[it.symbol]?.let { q -> abs(q.changePercent) > 0.001 } == true }
            .sortedByDescending { abs(quotes.getValue(it.symbol).changePercent) }.take(25)
        is ListTab.Group -> sorted(Instruments.all.filter { it.category == tab.category }, sort, quotes)
        ListTab.All -> sorted(Instruments.all, sort, quotes)
    }

    LazyColumn(modifier.fillMaxSize(), state = listState) {
        item {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                AccountCapsule(account, metrics, onAccountClick)
            }
        }
        item {
            Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Trade", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAlerts) { Icon(Icons.Outlined.AddAlarm, contentDescription = "Price alerts", modifier = Modifier.size(22.dp)) }
            }
        }
        stickyHeader {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Background),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(Modifier.weight(1f)) {
                    itemsIndexed(TABS) { i, t ->
                        val selected = i == tabIndex
                        Column(
                            Modifier
                                .clickable {
                                    tabIndex = i
                                    editing = false
                                    scope.launch { listState.scrollToItem(2) }
                                }
                                .padding(top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                t.title,
                                fontSize = 13.sp,
                                color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.height(2.dp).width(if (selected) 56.dp else 0.dp).background(AppColors.TextPrimary))
                        }
                    }
                }
                IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    ListTab.Favorites -> {
                        Chip("Sorted manually")
                        Chip(if (editing) "Done" else "Edit", icon = !editing) { editing = !editing }
                    }
                    ListTab.MostTraded -> Chip("↓ Sorted by popularity")
                    ListTab.TopMovers -> Chip("↓ Sorted by daily change")
                    else -> SortChip(sort) { sort = it }
                }
            }
        }
        when {
            loading -> items(6) { SkeletonRow() }
            list.isEmpty() -> item {
                Text(
                    if (tab == ListTab.Favorites) "Long-press any instrument to add it to Favorites"
                    else "No instruments right now",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            editing && tab == ListTab.Favorites -> items(list, key = { "e" + it.symbol }) { inst ->
                EditRow(inst, onUp = { onMoveFavorite(inst.symbol, -1) }, onDown = { onMoveFavorite(inst.symbol, 1) }) { onToggleFavorite(inst.symbol) }
            }
            else -> items(list, key = { it.symbol }) { inst ->
                InstrumentCard(inst, quotes[inst.symbol], market, onLongPress = { onToggleFavorite(inst.symbol) }) { onOpen(inst.symbol) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun sorted(list: List<Instrument>, sort: Sort, quotes: Map<String, Quote>): List<Instrument> = when (sort) {
    Sort.POPULARITY -> list.sortedBy { it.popularity }
    Sort.NAME -> list.sortedBy { it.display }
    Sort.CHANGE -> list.sortedByDescending { abs(quotes[it.symbol]?.changePercent ?: 0.0) }
    Sort.PRICE -> list.sortedByDescending { quotes[it.symbol]?.bid ?: 0.0 }
}

@Composable
private fun Chip(text: String, icon: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(AppColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.sp, color = if (onClick != null) AppColors.TextPrimary else AppColors.TextSecondary)
        if (icon) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SortChip(sort: Sort, onSort: (Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(AppColors.Surface)
                .clickable { open = true }
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sort.label, fontSize = 12.sp)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Sort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, fontWeight = if (s == sort) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onSort(s)
                    },
                )
            }
        }
    }
}

/** Three small red bars beside the symbol, as on the broker watchlist. */
@Composable
private fun Bars() {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(start = 6.dp)) {
        repeat(3) { Box(Modifier.size(width = 3.dp, height = 10.dp).background(BarsRed)) }
    }
}

@Composable
fun InstrumentCard(inst: Instrument, quote: Quote?, market: MarketRepository, onLongPress: (() -> Unit)? = null, onClick: () -> Unit) {
    val candles by remember(inst.symbol) { market.candles(inst.symbol, Timeframe.M15) }.collectAsStateWithLifecycle()
    val spark = candles?.takeLast(96)?.map { it.close }.orEmpty()
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CardBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentIcon(inst, size = 24)
                Spacer(Modifier.width(8.dp))
                Text(inst.display, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Bars()
            }
            Text(
                inst.name,
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Sparkline(spark, Modifier.width(52.dp).height(22.dp), baseline = quote?.dayOpen, upColor = UpBlue)
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
            Text(quote?.let { inst.format(it.bid) } ?: "No data", fontSize = 14.sp, maxLines = 1)
            if (quote == null || quote.dayOpen == quote.bid && !quote.live) {
                Text("N/A", fontSize = 12.sp, color = AppColors.TextSecondary)
            } else {
                val up = quote.changePercent >= 0
                Text(
                    (if (up) "↑ " else "↓ ") + percent(abs(quote.changePercent)),
                    fontSize = 12.sp,
                    color = if (up) UpBlue else AppColors.Loss,
                )
            }
        }
    }
}

@Composable
private fun SkeletonRow() {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(Modifier.size(width = 150.dp, height = 16.dp).background(Skeleton))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.6f).height(8.dp).background(Skeleton))
    }
}

@Composable
private fun EditRow(inst: Instrument, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CardBg)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentIcon(inst, size = 24)
        Spacer(Modifier.width(8.dp))
        Text(inst.display, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
        IconButton(onClick = onDown) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove", tint = AppColors.Loss) }
    }
}

@Composable
private fun SearchPage(
    quotes: Map<String, Quote>,
    market: MarketRepository,
    favorites: List<String>,
    onToggleFavorite: (String) -> Unit,
    onOpen: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    androidx.activity.compose.BackHandler(onBack = onClose)
    val q = query.trim().lowercase()
    val results = Instruments.byPopularity.filter {
        q.isEmpty() || it.symbol.lowercase().contains(q) || it.display.lowercase().contains(q) || it.name.lowercase().contains(q)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
            Row(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Surface)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search instruments", color = AppColors.TextSecondary, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(AppColors.Accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp).clickable { onQuery("") })
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (results.isEmpty()) {
                item { Text("No instruments found", color = AppColors.TextSecondary, modifier = Modifier.padding(24.dp)) }
            }
            items(results, key = { it.symbol }) { inst ->
                InstrumentCard(inst, quotes[inst.symbol], market, onLongPress = { onToggleFavorite(inst.symbol) }) { onOpen(inst.symbol) }
            }
        }
    }
}
