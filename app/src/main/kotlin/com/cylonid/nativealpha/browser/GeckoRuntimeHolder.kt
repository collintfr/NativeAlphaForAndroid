package com.cylonid.nativealpha.browser

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.cylonid.nativealpha.BuildConfig
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File

/**
 * Owns the single [GeckoRuntime] of this app (Gecko allows only one per process and one process
 * per profile), and installs the bundled uBlock Origin extension on first use.
 */
object GeckoRuntimeHolder {
    private const val TAG = "GeckoRuntimeHolder"
    const val UBLOCK_ID = "uBlock0@raymondhill.net"
    private const val UBLOCK_ASSET = "extensions/ublock_origin.xpi"
    private const val PREFS = "gecko_runtime"
    private const val PREF_UBO_ASSET_SIZE = "ubo_asset_size"

    @Volatile private var runtime: GeckoRuntime? = null
    @Volatile var ublock: WebExtension? = null
        private set
    private val ublockListeners = mutableListOf<(WebExtension?) -> Unit>()

    @JvmStatic
    @Synchronized
    fun get(context: Context): GeckoRuntime {
        runtime?.let { return it }
        val appContext = context.applicationContext
        val settings = GeckoRuntimeSettings.Builder()
            .consoleOutput(BuildConfig.DEBUG)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .aboutConfigEnabled(BuildConfig.DEBUG)
            .loginAutofillEnabled(false)
            .doubleTapZoomingEnabled(true)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    // Total Cookie Protection: third-party cookies are partitioned per site.
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    .antiTracking(ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .build()
            )
            .build()
        val rt = GeckoRuntime.create(appContext, settings)
        runtime = rt
        setupExtensions(appContext, rt)
        return rt
    }

    @JvmStatic
    fun onConfigurationChanged(newConfig: Configuration) {
        runtime?.configurationChanged(newConfig)
    }

    /** Invokes [listener] with uBO once it's known (immediately if already installed). */
    @JvmStatic
    @Synchronized
    fun whenUblockReady(listener: (WebExtension?) -> Unit) {
        val current = ublock
        if (current != null) listener(current) else ublockListeners += listener
    }

    @Synchronized
    private fun publishUblock(ext: WebExtension?) {
        ublock = ext
        ublockListeners.toList().forEach { it(ext) }
        ublockListeners.clear()
    }

    private fun setupExtensions(context: Context, rt: GeckoRuntime) {
        val controller = rt.webExtensionController
        // Only our own bundled install is ever requested, so accept it (incl. private browsing).
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(extension.id == UBLOCK_ID, true, false))

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
                newDataCollectionPermissions: Array<out String>
            ): GeckoResult<org.mozilla.geckoview.AllowOrDeny> =
                if (extension.id == UBLOCK_ID) GeckoResult.allow() else GeckoResult.deny()
        }

        controller.list().accept({ list ->
            val existing = list?.firstOrNull { it.id == UBLOCK_ID }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val assetSize = bundledAssetSize(context)
            val bundledChanged = prefs.getLong(PREF_UBO_ASSET_SIZE, -1L) != assetSize
            if (existing != null && !bundledChanged) {
                publishUblock(existing)
                return@accept
            }
            installBundledUblock(context, controller) { ext ->
                if (ext != null) prefs.edit().putLong(PREF_UBO_ASSET_SIZE, assetSize).apply()
                publishUblock(ext ?: existing)
            }
        }, { e ->
            Log.e(TAG, "Listing extensions failed", e)
            publishUblock(null)
        })
    }

    private fun bundledAssetSize(context: Context): Long = try {
        context.assets.openFd(UBLOCK_ASSET).use { it.length }
    } catch (e: Exception) {
        // Compressed assets can't be opened as fd; fall back to stream length.
        try { context.assets.open(UBLOCK_ASSET).use { it.available().toLong() } } catch (e2: Exception) { -1L }
    }

    private fun installBundledUblock(context: Context, controller: WebExtensionController, done: (WebExtension?) -> Unit) {
        try {
            val target = File(context.filesDir, "ublock_origin.xpi")
            context.assets.open(UBLOCK_ASSET).use { input -> target.outputStream().use { input.copyTo(it) } }
            controller.install(target.toURI().toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE)
                .accept({ ext ->
                    Log.i(TAG, "uBlock Origin installed: ${ext?.metaData?.version}")
                    target.delete()
                    done(ext)
                }, { e ->
                    Log.e(TAG, "uBlock Origin install failed", e)
                    done(null)
                })
        } catch (e: Exception) {
            Log.e(TAG, "uBlock Origin asset missing", e)
            done(null)
        }
    }

    /** Enables or disables uBO globally. */
    @JvmStatic
    fun setUblockEnabled(context: Context, enabled: Boolean, done: (() -> Unit)? = null) {
        val controller = get(context).webExtensionController
        whenUblockReady { ext ->
            if (ext == null) { done?.invoke(); return@whenUblockReady }
            val result = if (enabled) controller.enable(ext, WebExtensionController.EnableSource.USER)
            else controller.disable(ext, WebExtensionController.EnableSource.USER)
            result.accept({ updated -> if (updated != null) ublock = updated; done?.invoke() }, { done?.invoke() })
        }
    }
}
