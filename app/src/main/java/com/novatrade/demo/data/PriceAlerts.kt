package com.novatrade.demo.data

import android.content.Context
import com.novatrade.demo.model.Instruments
import com.novatrade.demo.util.format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Fires once when the bid crosses [price] in the given direction. */
data class PriceAlert(val id: Long, val symbol: String, val above: Boolean, val price: Double, val created: Long)

/** Price alerts, checked against every quote update and kept in SharedPreferences. */
class PriceAlerts(context: Context, private val market: MarketRepository, scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
    private val _alerts = MutableStateFlow(load())
    val alerts: StateFlow<List<PriceAlert>> = _alerts.asStateFlow()

    private val _fired = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val fired: SharedFlow<String> = _fired.asSharedFlow()

    init {
        scope.launch {
            market.quotes.collect { quotes ->
                val hit = _alerts.value.filter { a ->
                    val bid = quotes[a.symbol]?.bid ?: return@filter false
                    if (a.above) bid >= a.price else bid <= a.price
                }
                if (hit.isEmpty()) return@collect
                set(_alerts.value - hit.toSet())
                hit.forEach { a ->
                    val inst = Instruments[a.symbol]
                    _fired.tryEmit("Price alert: ${inst.display} is ${if (a.above) "above" else "below"} ${inst.format(a.price)}")
                }
            }
        }
    }

    fun add(symbol: String, above: Boolean, price: Double) {
        set(_alerts.value + PriceAlert(System.currentTimeMillis(), symbol, above, price, System.currentTimeMillis()))
    }

    fun remove(id: Long) = set(_alerts.value.filter { it.id != id })

    private fun set(list: List<PriceAlert>) {
        _alerts.value = list
        prefs.edit().putString("list", JSONArray(list.map {
            JSONObject().put("id", it.id).put("symbol", it.symbol).put("above", it.above).put("price", it.price).put("created", it.created)
        }).toString()).apply()
    }

    private fun load(): List<PriceAlert> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        List(arr.length()) {
            val o = arr.getJSONObject(it)
            PriceAlert(o.getLong("id"), o.getString("symbol"), o.getBoolean("above"), o.getDouble("price"), o.getLong("created"))
        }.filter { Instruments.find(it.symbol) != null }
    }.getOrDefault(emptyList())
}
