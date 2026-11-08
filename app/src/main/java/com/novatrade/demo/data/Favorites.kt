package com.novatrade.demo.data

import android.content.Context
import com.novatrade.demo.model.Instruments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Favourite instrument symbols in the user's order, kept in SharedPreferences. */
class Favorites(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val _symbols = MutableStateFlow(
        (prefs.getString(KEY, null)?.split(",")?.filter { it.isNotBlank() } ?: DEFAULT).filter { Instruments.find(it) != null }
    )
    val symbols: StateFlow<List<String>> = _symbols.asStateFlow()

    fun toggle(symbol: String) = set(if (symbol in _symbols.value) _symbols.value - symbol else _symbols.value + symbol)

    /** Moves [symbol] up (-1) or down (+1) in the list. */
    fun move(symbol: String, delta: Int) {
        val list = _symbols.value.toMutableList()
        val i = list.indexOf(symbol)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        set(list)
    }

    private fun set(next: List<String>) {
        _symbols.value = next
        prefs.edit().putString(KEY, next.joinToString(",")).apply()
    }

    private companion object {
        const val KEY = "symbols"
        val DEFAULT = listOf("XAUUSD", "BTCUSD", "AAPL", "EURUSD", "GBPUSD", "USDJPY", "USTEC", "USOIL")
    }
}
