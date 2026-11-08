package com.novatrade.demo.data

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Economic calendar and market news for the Insights tab, refreshed every 15 minutes. */
class InsightsRepository(private val scope: CoroutineScope) {
    private val api = MarketApi()

    private val _events = MutableStateFlow<List<CalendarEvent>?>(null)
    /** Upcoming events (null while loading). */
    val events: StateFlow<List<CalendarEvent>?> = _events.asStateFlow()

    private val _news = MutableStateFlow<List<NewsItem>?>(null)
    val news: StateFlow<List<NewsItem>?> = _news.asStateFlow()

    private val images = HashMap<String, MutableStateFlow<ImageBitmap?>>()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                runCatching { api.calendar() }.onSuccess { list ->
                    _events.value = list.filter { it.time >= now - 30 * 60_000 }.sortedBy { it.time }
                }.onFailure { if (_events.value == null) _events.value = emptyList() }
                runCatching { api.news() }.onSuccess { _news.value = it.sortedByDescending { n -> n.time } }
                    .onFailure { if (_news.value == null) _news.value = emptyList() }
                delay(15 * 60_000L)
            }
        }
    }

    fun image(url: String): StateFlow<ImageBitmap?> = images.getOrPut(url) {
        MutableStateFlow<ImageBitmap?>(null).also { flow ->
            scope.launch {
                flow.value = runCatching {
                    val bytes = api.bytes(url)
                    withContext(Dispatchers.Default) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
}
