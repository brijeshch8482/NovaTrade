package com.novatrade.demo.util

import com.novatrade.demo.model.Instrument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Instrument.format(price: Double): String = String.format(Locale.US, "%.${digits}f", price)

fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)

fun usd(v: Double): String = money(v) + " USD"

fun signedUsd(v: Double): String = (if (v >= 0) "+" else "") + usd(v)

fun signedMoney(v: Double): String = (if (v >= 0) "+" else "") + money(v)

fun lots(v: Double): String = String.format(Locale.US, "%.2f", v)

fun signedPercent(v: Double): String = String.format(Locale.US, "%+.2f%%", v)

fun percent(v: Double): String = String.format(Locale.US, "%.2f%%", v)

fun dateTime(millis: Long): String = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.US).format(Date(millis))
