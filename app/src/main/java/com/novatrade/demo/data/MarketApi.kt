package com.novatrade.demo.data

import com.novatrade.demo.model.Candle
import com.novatrade.demo.model.TfUnit
import com.novatrade.demo.model.Timeframe
import com.novatrade.demo.model.aggregate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Latest price and previous close of a Yahoo Finance symbol. */
data class YahooQuote(val price: Double, val previousClose: Double)

/** One economic calendar event. */
data class CalendarEvent(val title: String, val country: String, val time: Long, val impact: Int, val forecast: String, val previous: String)

data class NewsItem(val title: String, val link: String, val summary: String, val time: Long, val image: String?)

/**
 * Free, key-less market data:
 *  - Binance public market data (crypto, and PAXG as live gold)
 *  - Yahoo Finance (forex, metals, energies, indices, stocks)
 *  - Forex Factory weekly economic calendar and the FXStreet news feed
 */
class MarketApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // ---- Binance ----

    /** [limit] candles of [tf]; intervals Binance lacks (7m, 45m, 3h…) are merged from a smaller one. */
    suspend fun klines(binanceSymbol: String, tf: Timeframe, limit: Int): List<Candle> {
        val (interval, base) = binanceInterval(tf)
        val perCandle = (tf.seconds / base.seconds).coerceAtLeast(1)
        val raw = klines(binanceSymbol, interval, (limit * perCandle).coerceAtMost(1000L).toInt())
        return if (base == tf) raw else aggregate(raw, tf).takeLast(limit)
    }

    private suspend fun klines(binanceSymbol: String, interval: String, limit: Int): List<Candle> {
        val url = "$BINANCE_REST/api/v3/klines".toHttpUrl().newBuilder()
            .addQueryParameter("symbol", binanceSymbol)
            .addQueryParameter("interval", interval)
            .addQueryParameter("limit", limit.toString())
            .build()
        val arr = JSONArray(get(url.toString()))
        return List(arr.length()) { i ->
            val k = arr.getJSONArray(i)
            Candle(
                time = k.getLong(0),
                open = k.getString(1).toDouble(),
                high = k.getString(2).toDouble(),
                low = k.getString(3).toDouble(),
                close = k.getString(4).toDouble(),
                volume = k.getString(5).toDouble(),
            )
        }
    }

    /** Binance interval string and the timeframe it stands for: [tf] itself when supported, else a divisor. */
    private fun binanceInterval(tf: Timeframe): Pair<String, Timeframe> {
        fun largest(options: List<Int>) = options.first { tf.count % it == 0 }
        val base = when (tf.unit) {
            TfUnit.MINUTE -> Timeframe(largest(listOf(30, 15, 5, 3, 1)), TfUnit.MINUTE)
            TfUnit.HOUR -> Timeframe(largest(listOf(12, 8, 6, 4, 2, 1)), TfUnit.HOUR)
            TfUnit.DAY -> Timeframe(largest(listOf(3, 1)), TfUnit.DAY)
            TfUnit.WEEK -> Timeframe(1, TfUnit.WEEK)
            TfUnit.MONTH -> Timeframe(1, TfUnit.MONTH)
        }
        val code = when (base.unit) {
            TfUnit.MINUTE -> "${base.count}m"
            TfUnit.HOUR -> "${base.count}h"
            TfUnit.DAY -> "${base.count}d"
            TfUnit.WEEK -> "1w"
            TfUnit.MONTH -> "1M"
        }
        return code to base
    }

    /** Binance symbol -> 24h open price. */
    suspend fun dayOpens(binanceSymbols: List<String>): Map<String, Double> {
        val arr = JSONArray(get(multiSymbolUrl("/api/v3/ticker/24hr", binanceSymbols)))
        return (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            o.getString("symbol") to o.getString("openPrice").toDouble()
        }
    }

    /** Binance symbol -> mid of best bid/ask. Used when the WebSocket is unavailable. */
    suspend fun bookMids(binanceSymbols: List<String>): Map<String, Double> {
        val arr = JSONArray(get(multiSymbolUrl("/api/v3/ticker/bookTicker", binanceSymbols)))
        return (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            o.getString("symbol") to (o.getString("bidPrice").toDouble() + o.getString("askPrice").toDouble()) / 2
        }
    }

    fun bookTickerSocket(
        binanceSymbols: List<String>,
        onMid: (binanceSymbol: String, mid: Double) -> Unit,
        onClosed: () -> Unit,
    ): WebSocket {
        val streams = binanceSymbols.joinToString("/") { "${it.lowercase()}@bookTicker" }
        val request = Request.Builder().url("$BINANCE_WS/stream?streams=$streams").build()
        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val data = JSONObject(text).optJSONObject("data") ?: return
                val bid = data.optString("b").toDoubleOrNull() ?: return
                val ask = data.optString("a").toDoubleOrNull() ?: return
                onMid(data.getString("s"), (bid + ask) / 2)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onClosed()
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onClosed()
        })
    }

    private fun multiSymbolUrl(path: String, symbols: List<String>): String =
        "$BINANCE_REST$path".toHttpUrl().newBuilder()
            .addQueryParameter("symbols", symbols.joinToString(",", "[", "]") { "\"$it\"" })
            .build().toString()

    // ---- Yahoo Finance ----

    /** Ticker -> latest quote, for up to 20 tickers per call. */
    suspend fun yahooQuotes(tickers: List<String>): Map<String, YahooQuote> {
        val out = HashMap<String, YahooQuote>()
        for (batch in tickers.chunked(20)) {
            val url = "$YAHOO/v7/finance/spark".toHttpUrl().newBuilder()
                .addQueryParameter("symbols", batch.joinToString(","))
                .addQueryParameter("range", "1d")
                .addQueryParameter("interval", "1d")
                .build()
            val results = JSONObject(get(url.toString())).getJSONObject("spark").getJSONArray("result")
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val meta = r.getJSONArray("response").getJSONObject(0).getJSONObject("meta")
                val price = meta.optDouble("regularMarketPrice", Double.NaN)
                val prev = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", Double.NaN))
                if (!price.isNaN()) out[r.getString("symbol")] = YahooQuote(price, if (prev.isNaN()) price else prev)
            }
        }
        return out
    }

    /** OHLC history for a ticker; intervals Yahoo lacks are merged from a smaller one. */
    suspend fun yahooCandles(ticker: String, tf: Timeframe, limit: Int): List<Candle> {
        // Yahoo interval and how far back it serves, in days.
        val (interval, maxDays) = when (tf.unit) {
            TfUnit.MINUTE -> when {
                tf.count % 30 == 0 -> "30m" to 59
                tf.count % 15 == 0 -> "15m" to 59
                tf.count % 5 == 0 -> "5m" to 59
                tf.count % 2 == 0 -> "2m" to 59
                else -> "1m" to 6
            }
            TfUnit.HOUR -> "60m" to 729
            TfUnit.DAY -> "1d" to 36_500
            TfUnit.WEEK -> "1wk" to 36_500
            TfUnit.MONTH -> "1mo" to 36_500
        }
        // Three times the plain span leaves room for weekends and closed hours.
        val days = (tf.seconds * limit * 3 / 86_400).coerceIn(2, maxDays.toLong())
        val now = System.currentTimeMillis() / 1000
        val url = "$YAHOO/v8/finance/chart/".toHttpUrl().newBuilder()
            .addPathSegment(ticker)
            .addQueryParameter("interval", interval)
            .addQueryParameter("period1", (now - days * 86_400).toString())
            .addQueryParameter("period2", now.toString())
            .build()
        val result = JSONObject(get(url.toString())).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val times = result.optJSONArray("timestamp") ?: return emptyList()
        val q = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val o = q.getJSONArray("open")
        val h = q.getJSONArray("high")
        val l = q.getJSONArray("low")
        val c = q.getJSONArray("close")
        val v = q.optJSONArray("volume")
        val raw = (0 until times.length()).mapNotNull { i ->
            if (c.isNull(i) || o.isNull(i) || h.isNull(i) || l.isNull(i)) return@mapNotNull null
            val volume = if (v == null || v.isNull(i)) 0.0 else v.getDouble(i)
            Candle(times.getLong(i) * 1000, o.getDouble(i), h.getDouble(i), l.getDouble(i), c.getDouble(i), volume)
        }
        // Merge bars that fall into the same slot (3h from 1h, and any duplicate stamps).
        return aggregate(raw, tf).takeLast(limit)
    }

    // ---- Calendar and news ----

    suspend fun calendar(): List<CalendarEvent> {
        val arr = JSONArray(get(CALENDAR))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val impact = when (o.optString("impact")) {
                "High" -> 3
                "Medium" -> 2
                "Low" -> 1
                else -> return@mapNotNull null
            }
            val time = runCatching { OffsetDateTime.parse(o.getString("date")).toInstant().toEpochMilli() }.getOrNull()
                ?: return@mapNotNull null
            CalendarEvent(o.getString("title"), o.getString("country"), time, impact, o.optString("forecast"), o.optString("previous"))
        }
    }

    suspend fun news(): List<NewsItem> {
        val xml = get(NEWS)
        val item = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
        fun tag(body: String, name: String) =
            Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
                ?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim().orEmpty()
        val rfc = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US)
        return item.findAll(xml).map { m ->
            val body = m.groupValues[1]
            NewsItem(
                title = decode(tag(body, "title")),
                link = tag(body, "link"),
                summary = decode(tag(body, "description")),
                time = runCatching { ZonedDateTime.parse(tag(body, "pubDate"), rfc).toInstant().toEpochMilli() }.getOrDefault(0L),
                image = Regex("<enclosure url=\"([^\"]+)\"").find(body)?.groupValues?.get(1),
            )
        }.toList()
    }

    suspend fun bytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            res.body!!.bytes()
        }
    }

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code} for $url")
            res.body!!.string()
        }
    }

    private companion object {
        const val BINANCE_REST = "https://data-api.binance.vision"
        const val BINANCE_WS = "wss://data-stream.binance.vision"
        const val YAHOO = "https://query1.finance.yahoo.com"
        const val CALENDAR = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"
        const val NEWS = "https://www.fxstreet.com/rss/news"
        val UA get() = "Mozilla/5.0 (Linux; Android 14) ${com.novatrade.demo.Brand.name.replace(" ", "")}/1.0"
    }
}
