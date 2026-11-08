package com.novatrade.demo.data

import android.content.Context

/** Chart drawings per instrument, stored as the JSON the chart page produces. */
class DrawingStore(context: Context) {
    private val prefs = context.getSharedPreferences("drawings", Context.MODE_PRIVATE)

    fun load(symbol: String): String = prefs.getString(symbol, null) ?: "[]"

    fun save(symbol: String, json: String) {
        prefs.edit().putString(symbol, json).apply()
    }
}
