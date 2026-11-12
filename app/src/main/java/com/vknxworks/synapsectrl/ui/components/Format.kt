package com.vknxworks.synapsectrl.ui.components

import android.content.Context
import android.text.format.DateUtils
import com.vknxworks.synapsectrl.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

fun percent(fraction: Double): Int = (fraction * 100).roundToInt().coerceIn(0, 100)

/** "5 minutes ago", "at 14:02", using the phone's locale and clock format. */
fun relativeTime(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** 1,234,567 with the phone's grouping. */
fun formatCount(n: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)

/** Milliseconds from seconds: 12 ms, 0.4 ms. */
fun formatMs(context: Context, seconds: Double): String {
    val ms = seconds * 1000
    val n = if (ms >= 10) ms.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", ms)
    return context.getString(R.string.value_ms, n)
}

/** A countdown: 0:42, 9:05, 1:02:30. */
fun formatCountdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s) else String.format(Locale.getDefault(), "%d:%02d", m, s)
}
