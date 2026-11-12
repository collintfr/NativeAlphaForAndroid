package com.cylonid.nativealpha.usage

import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/** A recorded foreground interval of a web app. */
data class UsageInterval(val webAppId: Int, val startMs: Long, val endMs: Long)

/** Pure helpers for usage aggregation (unit-tested). */
object UsageMath {
    /** Milliseconds of [intervals] that fall inside [from, to). Overlap is clipped. */
    @JvmStatic
    fun clippedTotal(intervals: Iterable<UsageInterval>, from: Long, to: Long): Long =
        intervals.sumOf { max(0L, min(it.endMs, to) - max(it.startMs, from)) }

    /** Local-midnight start of the day containing [timeMs]. */
    @JvmStatic
    fun startOfDay(timeMs: Long, cal: Calendar = Calendar.getInstance()): Long {
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Local-midnight start of the day after the one containing [timeMs] (DST-safe). */
    @JvmStatic
    fun startOfNextDay(timeMs: Long, cal: Calendar = Calendar.getInstance()): Long {
        cal.timeInMillis = startOfDay(timeMs, cal)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Per-day totals for the [days] days ending with the day of [nowMs], oldest first. */
    @JvmStatic
    fun dailyTotals(intervals: List<UsageInterval>, nowMs: Long, days: Int, cal: Calendar = Calendar.getInstance()): List<Pair<Long, Long>> {
        val result = ArrayList<Pair<Long, Long>>(days)
        var dayStart = startOfDay(nowMs, cal)
        cal.timeInMillis = dayStart
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        dayStart = cal.timeInMillis
        repeat(days) {
            val next = startOfNextDay(dayStart, cal)
            result += dayStart to clippedTotal(intervals, dayStart, next)
            dayStart = next
        }
        return result
    }

    enum class LimitState { NONE, OK, NEAR, REACHED }

    /** Limit state given usage today; NEAR starts at 80 %. */
    @JvmStatic
    fun limitState(usedMs: Long, limitMinutes: Int): LimitState {
        if (limitMinutes <= 0) return LimitState.NONE
        val limitMs = limitMinutes * 60_000L
        return when {
            usedMs >= limitMs -> LimitState.REACHED
            usedMs >= limitMs * 8 / 10 -> LimitState.NEAR
            else -> LimitState.OK
        }
    }

    @JvmStatic
    fun formatDuration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m == 0L -> "${h}h"
            h > 0 -> "${h}h ${m}m"
            totalMin > 0 -> "${m}m"
            else -> "<1m"
        }
    }
}
