package com.cylonid.nativealpha.usage

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.cylonid.nativealpha.model.WebApp
import java.util.concurrent.Executors

/**
 * Records foreground time for one web app and evaluates its daily limit.
 * Writes happen on a background thread; callbacks are delivered on the main thread.
 */
class UsageGuard(
    context: Context,
    private val webApp: WebApp,
    private val listener: Listener,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    interface Listener {
        /** Usage passed 80 % of the limit (once per day). */
        fun onLimitNear(usedMs: Long, limitMs: Long)
        /** Limit reached in WARN mode and not snoozed. */
        fun onLimitReachedWarn(usedMs: Long, limitMs: Long)
        /** Limit reached in BLOCK mode (or still reached when resuming). */
        fun onLimitReachedBlock(usedMs: Long, limitMs: Long)
        /** A new day started (or the limit was raised): blocked UI may be lifted. */
        fun onLimitCleared()
    }

    private val repo = UsageRepository.get(context)
    private val main = Handler(Looper.getMainLooper())
    private val io = ioExecutor
    private var sessionId: Long = -1
    private var running = false
    private var blocked = false
    var usedTodayMs: Long = 0
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            heartbeat()
            main.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        val now = clock()
        io.execute {
            sessionId = repo.startSession(webApp.ID, now)
            repo.prune(now)
        }
        main.post(tick)
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacks(tick)
        val id = sessionId
        val now = clock()
        io.execute { if (id >= 0) repo.updateSessionEnd(id, now) }
        sessionId = -1
    }

    private fun heartbeat() {
        val now = clock()
        io.execute {
            val id = sessionId
            if (id >= 0) repo.updateSessionEnd(id, now)
            val used = repo.totalForDay(webApp.ID, now)
            main.post { evaluate(used, now) }
        }
    }

    private fun evaluate(used: Long, now: Long) {
        usedTodayMs = used
        val limitMin = webApp.dailyLimitMinutes
        val limitMs = limitMin * 60_000L
        val day = UsageMath.startOfDay(now)
        when (UsageMath.limitState(used, limitMin)) {
            UsageMath.LimitState.NONE, UsageMath.LimitState.OK -> clearIfBlocked()
            UsageMath.LimitState.NEAR -> {
                clearIfBlocked()
                if (nearNotifiedDay[webApp.ID] != day) {
                    nearNotifiedDay[webApp.ID] = day
                    listener.onLimitNear(used, limitMs)
                }
            }
            UsageMath.LimitState.REACHED -> {
                if (webApp.limitMode == WebApp.LIMIT_MODE_BLOCK) {
                    if (!blocked) { blocked = true; listener.onLimitReachedBlock(used, limitMs) }
                } else {
                    val snoozedUntil = snoozeUntil[webApp.ID] ?: 0L
                    if (now >= snoozedUntil && !warningShowing) {
                        warningShowing = true
                        listener.onLimitReachedWarn(used, limitMs)
                    }
                }
            }
        }
    }

    private fun clearIfBlocked() {
        if (blocked) { blocked = false; listener.onLimitCleared() }
    }

    /** Call when the WARN dialog is dismissed; [snoozeMinutes] > 0 postpones the next warning. */
    fun warningDismissed(snoozeMinutes: Int) {
        warningShowing = false
        if (snoozeMinutes > 0) snoozeUntil[webApp.ID] = clock() + snoozeMinutes * 60_000L
    }

    /**
     * Synchronous check used before the first page load. Returns today's usage if the web app is
     * already blocked for today (and marks it so a later reset is reported), otherwise null.
     */
    fun blockedAtLaunch(): Long? {
        if (webApp.dailyLimitMinutes <= 0 || webApp.limitMode != WebApp.LIMIT_MODE_BLOCK) return null
        val used = repo.totalForDay(webApp.ID, clock())
        if (UsageMath.limitState(used, webApp.dailyLimitMinutes) != UsageMath.LimitState.REACHED) return null
        blocked = true
        usedTodayMs = used
        return used
    }

    /** Forces an immediate evaluation (e.g. on resume). */
    fun checkNow() = heartbeat()

    private var warningShowing = false

    companion object {
        const val TICK_MS = 30_000L
        private val ioExecutor = Executors.newSingleThreadExecutor()
        // Process-wide, deliberately not persisted: snoozes end when the app is killed.
        private val snoozeUntil = HashMap<Int, Long>()
        private val nearNotifiedDay = HashMap<Int, Long>()
    }
}
