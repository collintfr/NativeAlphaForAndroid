package com.cylonid.nativealpha.ui

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.usage.UsageMath
import com.cylonid.nativealpha.usage.UsageRepository
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.IconFetcher
import com.cylonid.nativealpha.util.ShortcutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WebAppItem(
    val app: WebApp,
    val icon: ImageBitmap?,
    val usedTodayMs: Long,
)

/** Daily usage of one web app over a period, for the usage screen. */
data class AppUsage(val app: WebApp, val icon: ImageBitmap?, val totalMs: Long)

data class UsageSummary(
    val apps: List<AppUsage>,
    val totalMs: Long,
    /** (day start, total ms across apps), oldest first; only for multi-day periods. */
    val daily: List<Pair<Long, Long>>,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx get() = getApplication<Application>()
    private val dm get() = DataManager.getInstance()

    private val _items = MutableStateFlow<List<WebAppItem>>(emptyList())
    val items: StateFlow<List<WebAppItem>> = _items.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val iconCache = HashMap<Int, ImageBitmap?>()

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                dm.loadAppData()
                val repo = UsageRepository.get(ctx)
                val now = System.currentTimeMillis()
                val from = UsageMath.startOfDay(now)
                val totals = repo.totalsByApp(from, UsageMath.startOfNextDay(now))
                dm.activeWebsites.map { app ->
                    WebAppItem(app, iconFor(app.ID), totals[app.ID] ?: 0L)
                }
            }
            _items.value = list
            _loaded.value = true
        }
    }

    private fun iconFor(id: Int): ImageBitmap? =
        iconCache.getOrPut(id) { ShortcutHelper.loadIcon(ctx, id)?.asImageBitmap() }

    fun invalidateIcon(id: Int) { iconCache.remove(id) }

    // ---- Mutations ----------------------------------------------------------------------------

    /** Creates a web app; returns it (already persisted). */
    fun addWebApp(url: String, title: String, icon: Bitmap?, dailyLimitMinutes: Int): WebApp {
        val withProtocol = if (url.startsWith("https://") || url.startsWith("http://")) url else "https://$url"
        val app = WebApp(withProtocol, dm.incrementedID, dm.incrementedOrder)
        app.applySettingsForNewWebApp()
        if (title.isNotBlank()) app.title = title.trim()
        app.dailyLimitMinutes = dailyLimitMinutes
        if (icon != null) {
            ShortcutHelper.saveIcon(ctx, app.ID, ShortcutHelper.renderAdaptiveBitmap(icon))
            invalidateIcon(app.ID)
        }
        dm.addWebsite(app)
        refresh()
        return app
    }

    fun delete(app: WebApp, activity: Activity) {
        val stored = dm.getWebAppIgnoringGlobalOverride(app.ID, true) ?: return
        stored.markInactive(activity)
        dm.saveWebAppData()
        closeRunningTasks(activity, app.ID)
        invalidateIcon(app.ID)
        refresh()
    }

    /** Moves a web app up (-1) or down (+1) in the list. */
    fun move(app: WebApp, delta: Int) {
        val active = dm.activeWebsites
        val i = active.indexOfFirst { it.ID == app.ID }
        val j = i + delta
        if (i < 0 || j !in active.indices) return
        val a = dm.websites[active[i].ID]
        val b = dm.websites[active[j].ID]
        // Normalise orders first so swapping always changes the visible order.
        active.forEachIndexed { idx, w -> dm.websites[w.ID].order = idx + 1 }
        val tmp = a.order; a.order = b.order; b.order = tmp
        dm.saveWebAppData()
        refresh()
    }

    /** Copy of a web app for editing, or of the global template for [GLOBAL_ID]. */
    fun editableCopy(id: Int): WebApp? =
        if (id == GLOBAL_ID) WebApp(dm.settings.globalWebApp)
        else dm.getWebAppIgnoringGlobalOverride(id, true)?.let { WebApp(it) }

    fun save(edited: WebApp) {
        if (edited.ID == Int.MAX_VALUE) {
            dm.settings.globalWebApp = edited
            dm.saveGlobalSettings()
        } else {
            dm.replaceWebApp(edited)
        }
    }

    fun setIcon(app: WebApp, bitmap: Bitmap) {
        ShortcutHelper.saveIcon(ctx, app.ID, ShortcutHelper.renderAdaptiveBitmap(bitmap))
        invalidateIcon(app.ID)
        ShortcutHelper.publishDynamicShortcuts(ctx)
        refresh()
    }

    /** Fetches site metadata off the main thread. */
    suspend fun fetchMetadata(url: String): IconFetcher.Result = withContext(Dispatchers.IO) {
        val withProtocol = if (url.startsWith("http")) url else "https://$url"
        IconFetcher.fetch(withProtocol)
    }

    /**
     * Pins a home screen shortcut, fetching an icon first if none is stored yet.
     */
    fun addToHomeScreen(activity: Activity, app: WebApp) {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { ShortcutHelper.loadIcon(ctx, app.ID) }
            val favicon = if (stored == null) fetchMetadata(app.baseUrl).icon else null
            ShortcutHelper.requestPinShortcut(activity, app, app.title, favicon)
            if (favicon != null) { invalidateIcon(app.ID); refresh() }
        }
    }

    fun usageSummary(days: Int, onResult: (UsageSummary) -> Unit) {
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                val repo = UsageRepository.get(ctx)
                val now = System.currentTimeMillis()
                val end = UsageMath.startOfNextDay(now)
                val start = java.util.Calendar.getInstance().apply {
                    timeInMillis = UsageMath.startOfDay(now); add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
                }.timeInMillis
                val intervals = repo.intervals(start, end)
                val byApp = intervals.groupBy { it.webAppId }.mapValues { (_, v) -> UsageMath.clippedTotal(v, start, end) }
                val apps = dm.websites.filter { byApp.containsKey(it.ID) }
                    .map { AppUsage(it, iconFor(it.ID), byApp[it.ID] ?: 0L) }
                    .sortedByDescending { it.totalMs }
                UsageSummary(apps, apps.sumOf { it.totalMs }, if (days > 1) UsageMath.dailyTotals(intervals, now, days) else emptyList())
            }
            onResult(summary)
        }
    }

    companion object {
        const val GLOBAL_ID = -2

        /** Ends running tasks of a web app so changed settings apply on next launch. */
        fun closeRunningTasks(activity: Activity, webAppId: Int) {
            val am = activity.getSystemService(ActivityManager::class.java) ?: return
            for (task in am.appTasks) {
                val id = task.taskInfo?.baseIntent?.getIntExtra(Const.INTENT_WEBAPPID, -1) ?: -1
                if (id == webAppId) task.finishAndRemoveTask()
            }
        }
    }
}
