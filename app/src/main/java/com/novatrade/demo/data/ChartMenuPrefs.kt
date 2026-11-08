package com.novatrade.demo.data

import android.content.Context
import com.novatrade.demo.model.Timeframe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Custom chart intervals and the starred intervals and chart types, kept in SharedPreferences. */
class ChartMenuPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("chart_menus", Context.MODE_PRIVATE)

    private val _custom = MutableStateFlow(read("custom").mapNotNull(Timeframe::parse))
    val custom: StateFlow<List<Timeframe>> = _custom.asStateFlow()

    private val _starredIntervals = MutableStateFlow(read("starredIntervals").toSet())
    val starredIntervals: StateFlow<Set<String>> = _starredIntervals.asStateFlow()

    private val _starredTypes = MutableStateFlow(read("starredTypes").toSet())
    val starredTypes: StateFlow<Set<String>> = _starredTypes.asStateFlow()

    fun addCustom(tf: Timeframe) {
        _custom.value = _custom.value + tf
        write("custom", _custom.value.map { it.label })
    }

    fun removeCustom(tf: Timeframe) {
        _custom.value = _custom.value - tf
        write("custom", _custom.value.map { it.label })
        if (tf.label in _starredIntervals.value) toggleInterval(tf)
    }

    fun toggleInterval(tf: Timeframe) {
        _starredIntervals.value = _starredIntervals.value.toggle(tf.label)
        write("starredIntervals", _starredIntervals.value.toList())
    }

    fun toggleType(name: String) {
        _starredTypes.value = _starredTypes.value.toggle(name)
        write("starredTypes", _starredTypes.value.toList())
    }

    private fun Set<String>.toggle(v: String) = if (v in this) this - v else this + v

    private fun read(key: String): List<String> =
        prefs.getString(key, null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()

    private fun write(key: String, values: List<String>) = prefs.edit().putString(key, values.joinToString(",")).apply()
}
