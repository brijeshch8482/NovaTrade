package com.novatrade.demo.data

import com.novatrade.demo.model.Candle
import com.novatrade.demo.model.Timeframe
import java.util.Random
import kotlin.math.exp
import kotlin.math.sqrt

object PriceSimulator {
    private val random = Random()

    /** Chance that a simulated instrument moves on a given 250 ms tick. */
    const val MOVE_PROBABILITY = 0.6
    private const val TICKS_PER_SECOND = 4 * MOVE_PROBABILITY

    fun gaussian(): Double = random.nextGaussian()

    fun step(price: Double, tickVolatility: Double): Double =
        if (random.nextDouble() < MOVE_PROBABILITY) price * exp(gaussian() * tickVolatility) else price

    /**
     * Random-walk candle history whose last close is exactly [endPrice], with volatility
     * consistent with the live tick simulation so history and live ticks look alike.
     */
    fun history(endPrice: Double, tf: Timeframe, count: Int, tickVolatility: Double, now: Long): List<Candle> {
        val subSteps = 12
        val stepSigma = tickVolatility * sqrt(TICKS_PER_SECOND * tf.seconds / subSteps)
        val raw = ArrayList<DoubleArray>(count) // log-space open, high, low, close
        var x = 0.0
        repeat(count) {
            val open = x
            var high = x
            var low = x
            repeat(subSteps) {
                x += gaussian() * stepSigma
                if (x > high) high = x
                if (x < low) low = x
            }
            raw += doubleArrayOf(open, high, low, x)
        }
        val shift = kotlin.math.ln(endPrice) - x
        // Candle start times, oldest first; months differ in length so step back one at a time.
        val times = LongArray(count)
        times[count - 1] = tf.bucket(now)
        for (i in count - 2 downTo 0) times[i] = tf.previous(times[i + 1])
        return raw.mapIndexed { i, c ->
            Candle(
                time = times[i],
                open = exp(c[0] + shift),
                high = exp(c[1] + shift),
                low = exp(c[2] + shift),
                close = exp(c[3] + shift),
                // Busier candles move more, so derive a plausible volume from the range.
                volume = (c[1] - c[2]) / stepSigma * (0.6 + random.nextDouble() * 0.8),
            )
        }
    }
}
