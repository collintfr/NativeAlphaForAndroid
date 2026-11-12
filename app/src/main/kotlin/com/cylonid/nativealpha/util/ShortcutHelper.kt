package com.cylonid.nativealpha.util

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Creates, updates and publishes launcher shortcuts for web apps.
 *
 * Shortcut IDs are derived from the (stable) web app ID rather than the title, so deleting and
 * re-adding a site with the same name can no longer collide with a disabled shortcut.
 * Every web app is also published as a dynamic shortcut, which is what launcher search
 * (Pixel Launcher) and shortcut indexing (KISS) pick up.
 */
object ShortcutHelper {
    private const val TAG = "ShortcutHelper"

    /** Adaptive icons are 108dp; 432px corresponds to xxxhdpi and stays well below binder limits. */
    const val ADAPTIVE_ICON_PX = 432

    /** Content is kept inside the 66dp safe zone of the 108dp canvas. */
    private const val SAFE_ZONE_RATIO = 60f / 108f

    const val ACTION_SHORTCUT_PINNED = "com.cylonid.nativealpha.SHORTCUT_PINNED"
    const val EXTRA_TITLE = "title"

    @JvmStatic
    fun shortcutId(webAppId: Int) = "webapp_$webAppId"

    // ---- Icon storage -------------------------------------------------------------------------

    private fun iconFile(context: Context, webAppId: Int) =
        File(File(context.filesDir, "icons").apply { mkdirs() }, "$webAppId.png")

    @JvmStatic
    fun saveIcon(context: Context, webAppId: Int, adaptiveBitmap: Bitmap) {
        try {
            FileOutputStream(iconFile(context, webAppId)).use {
                adaptiveBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist icon for $webAppId", e)
        }
    }

    /** Returns the stored adaptive icon bitmap (full 108dp canvas) for a web app, if any. */
    @JvmStatic
    fun loadIcon(context: Context, webAppId: Int): Bitmap? {
        val f = iconFile(context, webAppId)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    @JvmStatic
    fun deleteIcon(context: Context, webAppId: Int) {
        iconFile(context, webAppId).delete()
    }

    // ---- Icon rendering -----------------------------------------------------------------------

    /**
     * Renders a favicon onto a full adaptive-icon canvas: background sampled from the icon's
     * border, content scaled into the safe zone. Output is always ADAPTIVE_ICON_PX square.
     */
    @JvmStatic
    fun renderAdaptiveBitmap(source: Bitmap): Bitmap {
        val size = ADAPTIVE_ICON_PX
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(sampleBackgroundColor(source))

        val target = (size * SAFE_ZONE_RATIO)
        val scale = target / max(source.width, source.height)
        val w = (source.width * scale).roundToInt()
        val h = (source.height * scale).roundToInt()
        val left = (size - w) / 2
        val top = (size - h) / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, Rect(left, top, left + w, top + h), paint)
        return out
    }

    /**
     * Average colour of the opaque border pixels. Mostly transparent borders → white, so
     * transparent logos sit on a neutral tile like Chrome's WebAPK icons.
     */
    @JvmStatic
    fun sampleBackgroundColor(bmp: Bitmap): Int {
        val w = bmp.width
        val h = bmp.height
        if (w == 0 || h == 0) return Color.WHITE
        var r = 0L; var g = 0L; var b = 0L; var opaque = 0; var total = 0
        val step = max(1, max(w, h) / 64)
        fun sample(x: Int, y: Int) {
            val c = bmp.getPixel(x, y)
            total++
            if (Color.alpha(c) > 200) {
                opaque++; r += Color.red(c); g += Color.green(c); b += Color.blue(c)
            }
        }
        for (x in 0 until w step step) { sample(x, 0); sample(x, h - 1) }
        for (y in 0 until h step step) { sample(0, y); sample(w - 1, y) }
        if (opaque < total / 2) return Color.WHITE
        return Color.rgb((r / opaque).toInt(), (g / opaque).toInt(), (b / opaque).toInt())
    }

    private fun iconFor(context: Context, webApp: WebApp): IconCompat {
        val stored = loadIcon(context, webApp.ID)
        return if (stored != null) IconCompat.createWithAdaptiveBitmap(stored)
        else IconCompat.createWithResource(context, R.mipmap.native_alpha_shortcut)
    }

    // ---- Shortcut construction ----------------------------------------------------------------

    private fun buildShortcut(context: Context, webApp: WebApp, title: String, icon: IconCompat, rank: Int = 0): ShortcutInfoCompat? {
        val intent = WebViewLauncher.createWebViewIntent(webApp, context) ?: return null
        val label = title.ifBlank { webApp.title.ifBlank { "Web App" } }
        return ShortcutInfoCompat.Builder(context, shortcutId(webApp.ID))
            .setIcon(icon)
            .setShortLabel(label.take(25))
            .setLongLabel(label)
            .setIntent(intent)
            .setRank(rank)
            .build()
    }

    /**
     * Asks the default launcher to pin a shortcut. [favicon] may be null (keeps the stored icon or
     * falls back to the app icon). Shows a dialog with a retry option when pinning is impossible.
     */
    @JvmStatic
    fun requestPinShortcut(activity: Activity, webApp: WebApp, title: String, favicon: Bitmap?) {
        val context = activity.applicationContext
        if (favicon != null) saveIcon(context, webApp.ID, renderAdaptiveBitmap(favicon))

        if (title.isNotBlank() && title != webApp.title) {
            webApp.title = title
            DataManager.getInstance().saveWebAppData()
        }

        val shortcut = buildShortcut(context, webApp, title, iconFor(context, webApp))
        if (shortcut == null) {
            showPinFailedDialog(activity, webApp, title, favicon)
            return
        }

        // Re-enable a previously disabled shortcut with the same ID, and refresh any existing pin.
        try {
            ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut))
            ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable/update shortcut", e)
        }

        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            showPinFailedDialog(activity, webApp, title, favicon)
            return
        }

        val callbackIntent = Intent(context, ShortcutPinnedReceiver::class.java)
            .setAction(ACTION_SHORTCUT_PINNED)
            .putExtra(EXTRA_TITLE, shortcut.shortLabel.toString())
        val pending = PendingIntent.getBroadcast(
            context, webApp.ID, callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val accepted = try {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, pending.intentSender)
        } catch (e: Exception) {
            Log.e(TAG, "requestPinShortcut failed", e)
            false
        }
        if (!accepted) showPinFailedDialog(activity, webApp, title, favicon)
        publishDynamicShortcuts(context)
    }

    private fun showPinFailedDialog(activity: Activity, webApp: WebApp, title: String, favicon: Bitmap?) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.shortcut_pin_failed_title)
            .setMessage(R.string.shortcut_pin_failed_text)
            .setPositiveButton(R.string.retry) { _, _ -> requestPinShortcut(activity, webApp, title, favicon) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Most-used web apps of the last 7 days first; ties keep the user's list order. */
    private fun usageRanking(context: Context): Comparator<WebApp> {
        val now = System.currentTimeMillis()
        val totals = try {
            com.cylonid.nativealpha.usage.UsageRepository.get(context).totalsByApp(now - 7 * 86_400_000L, now)
        } catch (e: Exception) { emptyMap() }
        return compareByDescending<WebApp> { totals[it.ID] ?: 0L }.thenBy { it.order }
    }

    // ---- Dynamic shortcuts --------------------------------------------------------------------

    /**
     * Publishes all active web apps as dynamic shortcuts (up to the launcher's limit), ordered by
     * the supplied ranking (most important first). Safe to call often; failures are logged only.
     */
    @JvmStatic
    @JvmOverloads
    fun publishDynamicShortcuts(context: Context, ranking: Comparator<WebApp>? = null) {
        try {
            val appContext = context.applicationContext
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(appContext).coerceAtLeast(1)
            var apps = DataManager.getInstance().activeWebsites.toList()
            apps = apps.sortedWith(ranking ?: usageRanking(appContext))
            val shortcuts = apps.take(max).mapIndexedNotNull { i, app ->
                buildShortcut(appContext, app, app.title, iconFor(appContext, app), i)
            }
            ShortcutManagerCompat.setDynamicShortcuts(appContext, shortcuts)

            // Keep labels/icons of pinned shortcuts in sync as well.
            val pinnedIds = ShortcutManagerCompat.getShortcuts(appContext, ShortcutManagerCompat.FLAG_MATCH_PINNED).map { it.id }.toSet()
            val pinnedUpdates = apps.filter { shortcutId(it.ID) in pinnedIds }
                .mapNotNull { buildShortcut(appContext, it, it.title, iconFor(appContext, it)) }
            if (pinnedUpdates.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(appContext, pinnedUpdates)
        } catch (e: Exception) {
            Log.w(TAG, "Could not publish dynamic shortcuts", e)
        }
    }
}

/** Receives the launcher's confirmation once a pinned shortcut has actually been added. */
class ShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ShortcutHelper.EXTRA_TITLE) ?: ""
        Toast.makeText(context, context.getString(R.string.shortcut_added, title), Toast.LENGTH_SHORT).show()
    }
}
