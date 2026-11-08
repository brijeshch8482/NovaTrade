package com.novatrade.demo.model

import com.novatrade.demo.Brand

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Instrument groups, in the order of the Trade screen's tabs. */
enum class Category(val label: String) {
    MAJORS("Majors"), METALS("Metals"), CRYPTO("Crypto"), INDICES("Indices"), STOCKS("Stocks"),
    ENERGIES("Energies"), EXOTIC("Exotic"), MINORS("Minors");

    val isForex: Boolean get() = this == MAJORS || this == MINORS || this == EXOTIC
}

/** Where an instrument's price comes from. */
sealed interface PriceSource {
    /** Live mid price from Binance public market data. */
    data class Binance(val symbol: String) : PriceSource

    /** Price from Yahoo Finance, polled; small simulated ticks move it between polls. */
    data class Yahoo(val ticker: String) : PriceSource
}

/** When an instrument can be traded. */
enum class Hours {
    ALWAYS,

    /** Sunday 22:00 to Friday 22:00 UTC. */
    FX,

    /** Monday to Friday, 9:30 to 16:00 New York time. */
    US_STOCKS;

    fun isOpen(now: Long = System.currentTimeMillis()): Boolean = when (this) {
        ALWAYS -> true
        FX -> {
            val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.of("UTC"))
            when (t.dayOfWeek) {
                DayOfWeek.SATURDAY -> false
                DayOfWeek.FRIDAY -> t.hour < 22
                DayOfWeek.SUNDAY -> t.hour >= 22
                else -> true
            }
        }
        US_STOCKS -> {
            val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.of("America/New_York"))
            val minutes = t.hour * 60 + t.minute
            t.dayOfWeek != DayOfWeek.SATURDAY && t.dayOfWeek != DayOfWeek.SUNDAY && minutes in (9 * 60 + 30) until 16 * 60
        }
    }
}

data class Instrument(
    /** Stable id, also used for saved positions and favourites. */
    val symbol: String,
    /** Short name shown in lists and on the chart, e.g. "EUR/USD", "BTC", "US500". */
    val display: String,
    val name: String,
    val category: Category,
    val digits: Int,
    /** Units of the asset in 1.00 lot. */
    val contractSize: Double,
    /** Spread as a fraction of the mid price. */
    val spreadFraction: Double,
    val source: PriceSource,
    /** Used when no network price is available. */
    val fallbackPrice: Double,
    /** Typical size of one simulated tick, as a fraction of price. */
    val tickVolatility: Double,
    val maxLeverage: Int,
    /** Currency profit is made in; converted to USD with the live rates. */
    val quoteCcy: String = "USD",
    /** Badge codes for the icon: one or two currencies/assets, a country, or a ticker. */
    val icons: List<String>,
    val hours: Hours,
    /** Lower is more popular. */
    val popularity: Int = 999,
    /**
     * Small mean-reverting noise added around a live price, as a fraction of price. Makes thinly
     * traded proxies (PAXG for gold) tick like the real market while staying anchored to it.
     */
    val liveJitter: Double = 0.0,
) {
    /** About one pip: a round step near 0.01% of the price, never finer than the price precision. */
    fun priceStep(price: Double): Double =
        maxOf(10.0.pow(floor(log10(price * 0.0001))), 10.0.pow(-digits))
}

data class Quote(
    val bid: Double,
    val ask: Double,
    val dayOpen: Double,
    val time: Long,
    val live: Boolean,
    /** +1 / -1 / 0 vs. the previous quote, used for price flash colours. */
    val direction: Int = 0,
    /** False while the market is closed (weekends, outside stock hours). */
    val open: Boolean = true,
) {
    val mid: Double get() = (bid + ask) / 2
    val spread: Double get() = ask - bid
    val changePercent: Double get() = if (dayOpen > 0) (bid - dayOpen) / dayOpen * 100 else 0.0
}

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0,
)

enum class TfUnit(val letter: String, val word: String, val plural: String, val seconds: Long) {
    MINUTE("m", "minute", "minutes", 60),
    HOUR("h", "hour", "hours", 3_600),
    DAY("D", "day", "days", 86_400),
    WEEK("W", "week", "weeks", 604_800),
    /** Nominal 30 days; candles still start on calendar months. */
    MONTH("M", "month", "months", 2_592_000),
}

/** A chart interval of [count] [unit]s, e.g. 8 minutes or 12 months. */
data class Timeframe(val count: Int, val unit: TfUnit) {
    /** Short form for the toolbar and storage: "8m", "1h", "1D", "12M". */
    val label: String get() = "$count${unit.letter}"

    /** Long form for menus: "1 minute", "12 months". */
    val title: String get() = "$count ${if (count == 1) unit.word else unit.plural}"

    val seconds: Long get() = count * unit.seconds
    val millis: Long get() = seconds * 1000

    /** Start of the candle that contains [t]. Weeks start on Monday, months on the 1st (UTC). */
    fun bucket(t: Long): Long = when (unit) {
        TfUnit.WEEK -> Math.floorDiv(t - MONDAY, millis) * millis + MONDAY
        TfUnit.MONTH -> {
            val d = Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC)
            val index = (d.year * 12 + d.monthValue - 1) / count * count
            monthStart(index)
        }
        else -> Math.floorDiv(t, millis) * millis
    }

    /** Start of the candle after the one starting at [start]. */
    fun next(start: Long): Long = shift(start, 1)

    fun previous(start: Long): Long = shift(start, -1)

    private fun shift(start: Long, by: Int): Long =
        if (unit == TfUnit.MONTH) {
            val d = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC)
            monthStart(d.year * 12 + d.monthValue - 1 + by * count)
        } else {
            start + by * millis
        }

    companion object {
        val M1 = Timeframe(1, TfUnit.MINUTE)
        val M15 = Timeframe(15, TfUnit.MINUTE)
        val H1 = Timeframe(1, TfUnit.HOUR)

        /** The built-in list, in menu order. */
        val standard: List<Timeframe> =
            listOf(1, 2, 3, 5, 10, 15, 30, 45).map { Timeframe(it, TfUnit.MINUTE) } +
                listOf(1, 2, 4).map { Timeframe(it, TfUnit.HOUR) } +
                listOf(Timeframe(1, TfUnit.DAY), Timeframe(1, TfUnit.WEEK), Timeframe(1, TfUnit.MONTH), Timeframe(12, TfUnit.MONTH))

        fun parse(label: String): Timeframe? {
            val unit = TfUnit.entries.firstOrNull { label.endsWith(it.letter) } ?: return null
            val count = label.dropLast(unit.letter.length).toIntOrNull()?.takeIf { it > 0 } ?: return null
            return Timeframe(count, unit)
        }

        /** 1970-01-05, the first Monday of the epoch. */
        private const val MONDAY = 4 * 86_400_000L

        private fun monthStart(index: Int): Long =
            LocalDate.of(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}

/** Merges [candles] into [tf] candles; input must be in time order. */
fun aggregate(candles: List<Candle>, tf: Timeframe): List<Candle> {
    val out = ArrayList<Candle>()
    for (c in candles) {
        val t = tf.bucket(c.time)
        val last = out.lastOrNull()
        if (last != null && last.time == t) {
            out[out.lastIndex] = last.copy(high = maxOf(last.high, c.high), low = minOf(last.low, c.low), close = c.close, volume = last.volume + c.volume)
        } else {
            out += c.copy(time = t)
        }
    }
    return out
}

enum class Side(val label: String) { BUY("Buy"), SELL("Sell") }

enum class CloseReason(val label: String) {
    MANUAL("Closed"), STOP_LOSS("Stop Loss"), TAKE_PROFIT("Take Profit"), STOP_OUT("Stop Out"),
    PARTIAL("Partial close"), CLOSE_BY("Close by")
}

data class Position(
    val id: Long,
    val symbol: String,
    val side: Side,
    val lots: Double,
    val openPrice: Double,
    val openTime: Long,
    val margin: Double,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
)

enum class OrderType { LIMIT, STOP }

data class PendingOrder(
    val id: Long,
    val symbol: String,
    val side: Side,
    val type: OrderType,
    val lots: Double,
    val price: Double,
    val createdTime: Long,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
) {
    val label: String get() = "${side.label} ${if (type == OrderType.LIMIT) "Limit" else "Stop"}"
}

data class ClosedTrade(
    val id: Long,
    val symbol: String,
    val side: Side,
    val lots: Double,
    val openPrice: Double,
    val closePrice: Double,
    val openTime: Long,
    val closeTime: Long,
    val profit: Double,
    val reason: CloseReason,
    /** Levels the position had when it closed, for the TP / SL tags in the history. */
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
)

/** Both kinds use virtual money; the type is only a label, like on a real broker app. */
enum class AccountType(val label: String) { REAL("Real"), DEMO("Demo") }

data class Account(
    val number: Long,
    val type: AccountType = AccountType.DEMO,
    val kind: String = "Standard",
    val balance: Double,
    val leverage: Int,
    val positions: List<Position> = emptyList(),
    val pending: List<PendingOrder> = emptyList(),
    val history: List<ClosedTrade> = emptyList(),
    val nextId: Long = 1,
    val totalDeposits: Double = balance,
    /** Optional custom name shown instead of the account kind. */
    val nickname: String = "",
    val platform: String = Brand.name,
    /** Hidden from the account lists until restored; its history is kept. */
    val archived: Boolean = false,
) {
    /** Header shown on account cards, e.g. "RAJU" or "STANDARD". */
    val title: String get() = nickname.ifBlank { kind }.uppercase()
}

/** Live account figures derived from the account and current quotes. */
data class AccountMetrics(
    val balance: Double,
    val equity: Double,
    val margin: Double,
    val freeMargin: Double,
    /** Percent, or null when no margin is used. */
    val marginLevel: Double?,
    val floatingProfit: Double,
)
