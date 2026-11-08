package com.novatrade.demo

import android.app.Application
import com.novatrade.demo.data.ChartPrefs
import com.novatrade.demo.data.DrawingStore
import com.novatrade.demo.data.Favorites
import com.novatrade.demo.data.InsightsRepository
import com.novatrade.demo.data.MarketRepository
import com.novatrade.demo.data.PriceAlerts
import com.novatrade.demo.data.ProfilePrefs
import com.novatrade.demo.trading.AccountStore
import com.novatrade.demo.trading.TradingEngine
import kotlinx.coroutines.MainScope
import java.io.File

class TradingApp : Application() {
    /** Lives as long as the process so prices and SL/TP checks keep running across screens. */
    private val appScope = MainScope()

    lateinit var market: MarketRepository
        private set
    lateinit var engine: TradingEngine
        private set
    lateinit var favorites: Favorites
        private set
    lateinit var chartPrefs: ChartPrefs
        private set
    lateinit var drawingStore: DrawingStore
        private set
    lateinit var profile: ProfilePrefs
        private set
    lateinit var alerts: PriceAlerts
        private set
    lateinit var insights: InsightsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        Brand.name = getString(R.string.app_name)
        market = MarketRepository(appScope, getSharedPreferences("last_prices", MODE_PRIVATE)).also { it.start() }
        engine = TradingEngine(appScope, market, AccountStore(File(filesDir, "account.json")))
        favorites = Favorites(this)
        chartPrefs = ChartPrefs(this)
        drawingStore = DrawingStore(this)
        profile = ProfilePrefs(this)
        alerts = PriceAlerts(this, market, appScope)
        insights = InsightsRepository(appScope)
    }
}
