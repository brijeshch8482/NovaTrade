package com.novatrade.demo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novatrade.demo.data.ChartPrefs
import com.novatrade.demo.data.DrawingStore
import com.novatrade.demo.data.Favorites
import com.novatrade.demo.data.InsightsRepository
import com.novatrade.demo.data.MarketRepository
import com.novatrade.demo.data.PriceAlerts
import com.novatrade.demo.data.ProfilePrefs
import com.novatrade.demo.model.AccountType
import com.novatrade.demo.model.Timeframe
import com.novatrade.demo.trading.OrderResult
import com.novatrade.demo.trading.TradingEngine
import com.novatrade.demo.ui.accounts.AccountSwitcherSheet
import com.novatrade.demo.ui.accounts.AccountsScreen
import com.novatrade.demo.ui.accounts.OpenAccountFlow
import com.novatrade.demo.ui.common.LocalHideBalances
import com.novatrade.demo.ui.deposit.DemoDepositScreen
import com.novatrade.demo.ui.deposit.DepositFlow
import com.novatrade.demo.ui.deposit.DepositStart
import com.novatrade.demo.ui.deposit.PaymentMode
import com.novatrade.demo.ui.insights.InsightsScreen
import com.novatrade.demo.ui.instrument.Glyph
import com.novatrade.demo.ui.instrument.InstrumentScreen
import com.novatrade.demo.ui.instrument.LayoutControls
import com.novatrade.demo.ui.instrument.ToolGlyph
import com.novatrade.demo.ui.performance.PerformanceScreen
import com.novatrade.demo.ui.profile.LockScreen
import com.novatrade.demo.ui.profile.ProfileScreen
import com.novatrade.demo.ui.profile.SettingsScreen
import com.novatrade.demo.ui.profile.WelcomeScreen
import com.novatrade.demo.ui.theme.AppColors
import com.novatrade.demo.ui.trade.AlertsScreen
import com.novatrade.demo.ui.trade.TradeListScreen
import com.novatrade.demo.util.usd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector?) {
    ACCOUNTS("Accounts", Icons.Outlined.GridView),
    TRADE("Trade", null),
    INSIGHTS("Insights", Icons.Outlined.Language),
    PERFORMANCE("Performance", null),
    PROFILE("Profile", Icons.Outlined.AccountCircle),
}

/** Full-screen pages that cover the tabs. */
private enum class Page { SETTINGS, OPEN_ACCOUNT, DEMO_DEPOSIT, ALERTS }

@Composable
fun AppRoot(
    market: MarketRepository,
    engine: TradingEngine,
    favorites: Favorites,
    chartPrefs: ChartPrefs,
    drawingStore: DrawingStore,
    profile: ProfilePrefs,
    alerts: PriceAlerts,
    insights: InsightsRepository,
    onBiometric: ((onSuccess: () -> Unit) -> Unit)?,
) {
    val loggedIn by profile.loggedIn.collectAsStateWithLifecycle()
    val locked by profile.locked.collectAsStateWithLifecycle()
    val hidden by profile.balancesHidden.collectAsStateWithLifecycle()
    if (!loggedIn) {
        WelcomeScreen(profile)
        return
    }
    if (locked) {
        LockScreen(profile, onBiometric)
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.ACCOUNTS) }
    /** Instrument whose chart is open full-screen, if any. */
    var openSymbol by rememberSaveable { mutableStateOf<String?>(null) }
    var timeframeLabel by rememberSaveable { mutableStateOf(Timeframe.H1.label) }
    val timeframe = Timeframe.parse(timeframeLabel) ?: Timeframe.H1
    /** Full-screen deposit flow for real accounts, if open. */
    var depositStart by rememberSaveable { mutableStateOf<DepositStart?>(null) }
    var paymentMode by rememberSaveable { mutableStateOf(PaymentMode.DEPOSIT) }
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var showSwitcher by remember { mutableStateOf(false) }
    var readyToast by remember { mutableStateOf(false) }
    val layoutState by chartPrefs.state.collectAsStateWithLifecycle()
    var chartSettings by remember { mutableStateOf(chartPrefs.state.value.current.settings) }

    val quotes by market.quotes.collectAsStateWithLifecycle()
    val account by engine.account.collectAsStateWithLifecycle()
    val accounts by engine.accounts.collectAsStateWithLifecycle()
    val notifications by engine.notifications.collectAsStateWithLifecycle()
    val metrics by engine.metrics.collectAsStateWithLifecycle()
    val favoriteSymbols by favorites.symbols.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val show: (String) -> Unit = { msg ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }
    val onResult: (OrderResult) -> Unit = { result ->
        when (result) {
            is OrderResult.Success -> show(result.message)
            is OrderResult.Error -> show("⚠ " + result.message)
        }
    }
    LaunchedEffect(engine) {
        launch { engine.events.collect { if (profile.notify.value) show(it) } }
        alerts.fired.collect {
            engine.notify(it)
            if (profile.notify.value) show(it)
        }
    }
    LaunchedEffect(readyToast) {
        if (readyToast) {
            delay(3_000)
            readyToast = false
        }
    }

    // Deposit for the selected account: demo tops up directly, real goes through the payment flow.
    val requestDeposit: () -> Unit = {
        if (account.type == AccountType.REAL) {
            paymentMode = PaymentMode.DEPOSIT
            depositStart = DepositStart.METHODS
        } else {
            page = Page.DEMO_DEPOSIT
        }
    }
    val requestWithdraw: () -> Unit = {
        if (account.type == AccountType.REAL) {
            paymentMode = PaymentMode.WITHDRAW
            depositStart = DepositStart.METHODS
        } else {
            tab = Tab.ACCOUNTS
            openSymbol = null
            show("Use Withdraw on the account card to remove demo funds")
        }
    }

    BackHandler(enabled = openSymbol != null && page == null && depositStart == null) { openSymbol = null }

    CompositionLocalProvider(LocalHideBalances provides hidden) {
        Scaffold(
            containerColor = AppColors.Background,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(data, containerColor = AppColors.Toast, contentColor = AppColors.TextPrimary, shape = RoundedCornerShape(8.dp))
                }
            },
            bottomBar = {
                if (openSymbol == null && depositStart == null && page == null) {
                    Column {
                        if (readyToast) ReadyToast()
                        HorizontalDivider(color = AppColors.Divider, thickness = 0.5.dp)
                        NavigationBar(containerColor = AppColors.Surface, tonalElevation = 0.dp) {
                            Tab.entries.forEach { t ->
                                val selected = tab == t
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { tab = t },
                                    icon = {
                                        if (t.icon != null) Icon(t.icon, contentDescription = t.label)
                                        else NavGlyph(t, if (selected) AppColors.TextPrimary else AppColors.TextSecondary)
                                    },
                                    label = { Text(t.label, maxLines = 1, softWrap = false, fontSize = 10.sp, letterSpacing = (-0.2).sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AppColors.TextPrimary,
                                        selectedTextColor = AppColors.TextPrimary,
                                        indicatorColor = Color.Transparent,
                                        unselectedIconColor = AppColors.TextSecondary,
                                        unselectedTextColor = AppColors.TextSecondary,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (page) {
                Page.SETTINGS -> {
                    SettingsScreen(profile, engine, show, onClose = { page = null }, modifier = modifier)
                    return@Scaffold
                }
                Page.OPEN_ACCOUNT -> {
                    OpenAccountFlow(
                        onCreate = { type, kind, platform, leverage, nickname ->
                            engine.createAccount(type, leverage, kind, nickname, platform)
                            page = null
                            openSymbol = null
                            tab = Tab.ACCOUNTS
                            readyToast = true
                        },
                        onClose = { page = null },
                        modifier = modifier,
                    )
                    return@Scaffold
                }
                Page.DEMO_DEPOSIT -> {
                    DemoDepositScreen(onDeposit = { engine.deposit(it) }, onClose = { page = null }, modifier = modifier)
                    return@Scaffold
                }
                Page.ALERTS -> {
                    AlertsScreen(alerts, quotes, onClose = { page = null }, modifier = modifier)
                    return@Scaffold
                }
                null -> Unit
            }
            depositStart?.let { start ->
                DepositFlow(
                    start = start,
                    mode = paymentMode,
                    accounts = accounts,
                    activeNumber = account.number,
                    freeMarginOf = { engine.metricsFor(it, quotes).freeMargin },
                    onDeposit = { number, amount ->
                        engine.deposit(amount, number)
                        depositStart = null
                        show("Deposited ${usd(amount)} (virtual) to #$number")
                    },
                    onWithdraw = { number, amount ->
                        when (val r = engine.withdraw(amount, number)) {
                            is OrderResult.Success -> null
                            is OrderResult.Error -> r.message
                        }
                    },
                    onMessage = show,
                    onClose = { depositStart = null },
                    modifier = modifier,
                )
                return@Scaffold
            }
            val symbol = openSymbol
            if (symbol != null) {
                InstrumentScreen(
                    symbol = symbol,
                    timeframe = timeframe,
                    quotes = quotes,
                    account = account,
                    accounts = accounts,
                    metrics = metrics,
                    market = market,
                    engine = engine,
                    settings = chartSettings,
                    onSettingsChange = { chartSettings = it },
                    layout = LayoutControls(
                        name = layoutState.currentName,
                        names = layoutState.layouts.map { it.name },
                        dirty = chartSettings != layoutState.current.settings,
                        onSave = {
                            chartPrefs.save(chartSettings)
                            show("Layout \"${layoutState.currentName}\" saved")
                        },
                        onCopy = { name -> chartPrefs.copy(name, chartSettings) },
                        onRename = { name -> chartPrefs.rename(name) },
                        onOpen = { name -> chartSettings = chartPrefs.open(name) },
                    ),
                    drawingStore = drawingStore,
                    onSymbolChange = { openSymbol = it },
                    onRequestDeposit = requestDeposit,
                    onRequestWithdraw = requestWithdraw,
                    alerts = alerts,
                    onTimeframeChange = { timeframeLabel = it.label },
                    onBack = { openSymbol = null },
                    onResult = onResult,
                    modifier = modifier,
                )
                return@Scaffold
            }
            when (tab) {
                Tab.ACCOUNTS -> AccountsScreen(
                    account = account,
                    accounts = accounts,
                    metrics = metrics,
                    quotes = quotes,
                    notifications = notifications,
                    engine = engine,
                    onResult = onResult,
                    onTrade = { tab = Tab.TRADE },
                    onOpenInstrument = { openSymbol = it },
                    onOpenDeposit = { start, mode ->
                        paymentMode = mode
                        depositStart = start
                    },
                    onDemoDeposit = { page = Page.DEMO_DEPOSIT },
                    onOpenAccount = { page = Page.OPEN_ACCOUNT },
                    onAlerts = { page = Page.ALERTS },
                    modifier = modifier,
                )
                Tab.TRADE -> TradeListScreen(
                    quotes = quotes,
                    favorites = favoriteSymbols,
                    market = market,
                    account = account,
                    metrics = metrics,
                    onAccountClick = { showSwitcher = true },
                    onToggleFavorite = { sym ->
                        val adding = sym !in favoriteSymbols
                        favorites.toggle(sym)
                        show(if (adding) "Added to Favorites" else "Removed from Favorites")
                    },
                    onMoveFavorite = favorites::move,
                    onAlerts = { page = Page.ALERTS },
                    onOpen = { openSymbol = it },
                    modifier = modifier,
                )
                Tab.INSIGHTS -> InsightsScreen(
                    quotes = quotes,
                    market = market,
                    insights = insights,
                    account = account,
                    metrics = metrics,
                    onAccountClick = { showSwitcher = true },
                    onOpen = { openSymbol = it },
                    modifier = modifier,
                )
                Tab.PERFORMANCE -> PerformanceScreen(accounts = accounts, account = account, modifier = modifier)
                Tab.PROFILE -> ProfileScreen(
                    accounts = accounts,
                    engine = engine,
                    profile = profile,
                    onSettings = { page = Page.SETTINGS },
                    onCryptoWallet = {
                        paymentMode = PaymentMode.DEPOSIT
                        depositStart = DepositStart.WALLET
                    },
                    onMessage = show,
                    modifier = modifier,
                )
            }
        }

        if (showSwitcher) {
            AccountSwitcherSheet(
                accounts = accounts,
                activeNumber = account.number,
                quotes = quotes,
                engine = engine,
                onSelect = {
                    engine.switchTo(it)
                    showSwitcher = false
                },
                onCreate = {
                    showSwitcher = false
                    page = Page.OPEN_ACCOUNT
                },
                onDismiss = { showSwitcher = false },
            )
        }
    }
}

/** "Your account is ready for trading" bar shown above the tabs after opening an account. */
@Composable
private fun ReadyToast() {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E2219))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AppColors.DemoChipText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Your account is ready for trading", fontSize = 14.sp)
    }
}

/** Trade (two candles) and Performance (three stepped bars) outlines, measured from the broker's tab bar. */
@Composable
private fun NavGlyph(tab: Tab, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f * u)
        val corner = androidx.compose.ui.geometry.CornerRadius(1.4f * u)
        fun outline(l: Float, t: Float, r: Float, b: Float) = drawRoundRect(
            color, androidx.compose.ui.geometry.Offset(l * u, t * u), androidx.compose.ui.geometry.Size((r - l) * u, (b - t) * u), corner, style = stroke,
        )
        fun wick(x: Float, top: Float, bottom: Float) = drawLine(
            color, androidx.compose.ui.geometry.Offset(x * u, top * u), androidx.compose.ui.geometry.Offset(x * u, bottom * u),
            strokeWidth = 1.8f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        if (tab == Tab.TRADE) {
            wick(6.7f, 3.4f, 6.3f); wick(6.7f, 13f, 20f)
            outline(3.7f, 6.3f, 9.7f, 13f)
            wick(17.3f, 3.2f, 4.8f); wick(17.3f, 16.5f, 20.8f)
            outline(14.2f, 4.8f, 20.4f, 16.5f)
        } else {
            outline(2.6f, 11.2f, 8.5f, 20.6f)
            outline(8.5f, 7f, 15.2f, 20.6f)
            outline(15.2f, 3.2f, 21.4f, 20.6f)
        }
    }
}
