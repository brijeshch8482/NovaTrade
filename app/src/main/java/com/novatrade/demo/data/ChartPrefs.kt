package com.novatrade.demo.data

import android.content.Context
import com.novatrade.demo.ui.instrument.ChartSettings
import com.novatrade.demo.ui.instrument.ChartType
import com.novatrade.demo.ui.instrument.Indicator
import com.novatrade.demo.ui.instrument.ScaleLabel
import com.novatrade.demo.ui.instrument.ScaleLine
import com.novatrade.demo.ui.instrument.ScaleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ChartLayout(val name: String, val settings: ChartSettings)

data class LayoutState(val layouts: List<ChartLayout>, val currentName: String) {
    val current: ChartLayout get() = layouts.first { it.name == currentName }
}

/** Named chart layouts ("Default" and any copies), kept in SharedPreferences. */
class ChartPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("chart", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LayoutState> = _state.asStateFlow()

    /** Stores [settings] in the current layout. */
    fun save(settings: ChartSettings) = update { s ->
        s.copy(layouts = s.layouts.map { if (it.name == s.currentName) it.copy(settings = settings) else it })
    }

    /** Creates a new layout from [settings] and makes it current. Returns false if the name is taken. */
    fun copy(name: String, settings: ChartSettings): Boolean {
        if (!isFree(name)) return false
        update { it.copy(layouts = it.layouts + ChartLayout(name, settings), currentName = name) }
        return true
    }

    fun rename(name: String): Boolean {
        if (!isFree(name)) return false
        update { s ->
            s.copy(layouts = s.layouts.map { if (it.name == s.currentName) it.copy(name = name) else it }, currentName = name)
        }
        return true
    }

    fun open(name: String): ChartSettings {
        update { it.copy(currentName = name) }
        return _state.value.current.settings
    }

    private fun isFree(name: String) = name.isNotBlank() && _state.value.layouts.none { it.name.equals(name, ignoreCase = true) }

    private fun update(transform: (LayoutState) -> LayoutState) {
        _state.value = transform(_state.value)
        val s = _state.value
        prefs.edit()
            .putString("current", s.currentName)
            .putString("layouts", JSONArray(s.layouts.map { it.toJson() }).toString())
            .apply()
    }

    private fun load(): LayoutState = runCatching {
        val arr = JSONArray(prefs.getString("layouts", null) ?: error("none"))
        val layouts = List(arr.length()) { arr.getJSONObject(it).toLayout() }
        val current = prefs.getString("current", null)?.takeIf { n -> layouts.any { it.name == n } } ?: layouts.first().name
        LayoutState(layouts, current)
    }.getOrDefault(LayoutState(listOf(ChartLayout(DEFAULT, ChartSettings())), DEFAULT))

    private fun ChartLayout.toJson() = JSONObject()
        .put("name", name)
        .put("type", settings.type.name)
        .put("indicators", settings.indicators.joinToString(",") { it.name })
        .put("scale", settings.scale.name)
        .put("autoScale", settings.autoScale)
        .put("grid", settings.grid)
        .put("invertScale", settings.invertScale)
        .put("scaleLeft", settings.scaleLeft)
        .put("lockRatio", settings.lockRatio)
        .put("priceOnly", settings.priceOnly)
        .put("labels", settings.labels.joinToString(",") { it.name })
        .put("lines", settings.lines.joinToString(",") { it.name })
        .put("plusButton", settings.plusButton)

    private fun JSONObject.toLayout() = ChartLayout(
        name = getString("name"),
        settings = ChartSettings(
            type = ChartType.valueOf(getString("type")),
            indicators = getString("indicators").split(",").filter { it.isNotBlank() }.map(Indicator::valueOf).toSet(),
            scale = ScaleMode.valueOf(getString("scale")),
            autoScale = getBoolean("autoScale"),
            grid = getBoolean("grid"),
            invertScale = optBoolean("invertScale"),
            scaleLeft = optBoolean("scaleLeft"),
            lockRatio = optBoolean("lockRatio"),
            priceOnly = optBoolean("priceOnly"),
            // Layouts saved before these settings existed get the defaults.
            labels = if (has("labels")) names("labels", ScaleLabel::valueOf) else ScaleLabel.defaults,
            lines = if (has("lines")) names("lines", ScaleLine::valueOf) else ScaleLine.defaults,
            plusButton = optBoolean("plusButton"),
        ),
    )

    private fun <T> JSONObject.names(key: String, parse: (String) -> T): Set<T> =
        getString(key).split(",").filter { it.isNotBlank() }.mapNotNull { runCatching { parse(it) }.getOrNull() }.toSet()

    private companion object {
        const val DEFAULT = "Default"
    }
}
