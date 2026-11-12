package com.cylonid.nativealpha

import com.cylonid.nativealpha.usage.UsageInterval
import com.cylonid.nativealpha.usage.UsageMath
import com.cylonid.nativealpha.usage.UsageMath.LimitState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class UsageMathTest {
    private val tz = TimeZone.getTimeZone("Europe/Vienna")
    private fun cal() = Calendar.getInstance(tz)
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        cal().apply { clear(); set(y, mo - 1, d, h, mi) }.timeInMillis
    private val min = 60_000L

    @Test fun clipsIntervalsToRange() {
        val iv = listOf(
            UsageInterval(1, at(2026, 3, 1, 23, 50), at(2026, 3, 2, 0, 20)), // crosses midnight
            UsageInterval(1, at(2026, 3, 2, 10), at(2026, 3, 2, 10, 15)),
        )
        val day = UsageMath.startOfDay(at(2026, 3, 2, 12), cal())
        val next = UsageMath.startOfNextDay(at(2026, 3, 2, 12), cal())
        assertEquals(35 * min, UsageMath.clippedTotal(iv, day, next))
    }

    @Test fun dayBoundariesAreDstSafe() {
        // 2026-03-29 is a 23h day in Vienna (spring forward).
        val start = UsageMath.startOfDay(at(2026, 3, 29, 12), cal())
        val next = UsageMath.startOfNextDay(at(2026, 3, 29, 12), cal())
        assertEquals(23 * 60 * min, next - start)
    }

    @Test fun dailyTotalsOldestFirst() {
        val iv = listOf(
            UsageInterval(1, at(2026, 5, 10, 9), at(2026, 5, 10, 9, 30)),
            UsageInterval(1, at(2026, 5, 12, 9), at(2026, 5, 12, 10)),
        )
        val totals = UsageMath.dailyTotals(iv, at(2026, 5, 12, 20), 3, cal())
        assertEquals(listOf(30 * min, 0L, 60 * min), totals.map { it.second })
    }

    @Test fun limitStates() {
        assertEquals(LimitState.NONE, UsageMath.limitState(999 * min, 0))
        assertEquals(LimitState.OK, UsageMath.limitState(23 * min, 30))
        assertEquals(LimitState.NEAR, UsageMath.limitState(24 * min, 30))
        assertEquals(LimitState.REACHED, UsageMath.limitState(30 * min, 30))
    }

    @Test fun formatting() {
        assertEquals("<1m", UsageMath.formatDuration(30_000))
        assertEquals("45m", UsageMath.formatDuration(45 * min))
        assertEquals("2h 5m", UsageMath.formatDuration(125 * min))
    }
}
