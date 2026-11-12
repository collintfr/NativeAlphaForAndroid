package com.cylonid.nativealpha.browser

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cylonid.nativealpha.BuildConfig
import com.cylonid.nativealpha.MainActivity
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.helper.BiometricPromptHelper
import com.cylonid.nativealpha.helper.IconPopupMenuHelper
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.EntryPointUtils
import com.cylonid.nativealpha.util.WebViewLauncher
import com.cylonid.nativealpha.usage.UsageGuard
import com.cylonid.nativealpha.usage.UsageMath
import com.google.android.material.snackbar.Snackbar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import kotlin.math.abs

/**
 * Hosts one web app in a GeckoView. Every web app runs in the main process on the shared
 * [GeckoRuntime]; isolation between web apps uses Gecko's per-session `contextId` (separate
 * cookies/storage) instead of the old multi-process WebView sandboxes.
 */
open class BrowserActivity : AppCompatActivity() {

    protected var webappID = -1
    protected lateinit var webapp: WebApp
    private lateinit var runtime: GeckoRuntime
    private lateinit var geckoView: GeckoView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var rootView: FrameLayout
    protected lateinit var session: GeckoSession

    private var canGoBack = false
    private var canGoForward = false
    private var currentUrl: String? = null
    private var quitOnNextBackpress = false
    private var inFullscreen = false
    private var currentlyReloading = true
    private val reloadHandler = Handler(Looper.getMainLooper())
    private var popupMenu: PopupMenu? = null
    private lateinit var usageGuard: UsageGuard
    private var limitBlocked = false
    /** Off-site hosts the user allowed once ("Open here once") for this activity. */
    private val temporarilyAllowedHosts = HashSet<String>()

    // ---- Activity result plumbing for prompts / permissions -----------------------------------

    private var pendingFileCallback: ((Array<Uri>?) -> Unit)? = null
    private val pickSingle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingFileCallback?.invoke(uri?.let { arrayOf(it) }); pendingFileCallback = null
    }
    private val pickMultiple = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingFileCallback?.invoke(uris.takeIf { it.isNotEmpty() }?.toTypedArray()); pendingFileCallback = null
    }
    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pendingPermissionCallback?.invoke(result.values.all { it }); pendingPermissionCallback = null
    }

    private fun requestAndroidPermissions(perms: Array<String>, callback: (Boolean) -> Unit) {
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { callback(true); return }
        pendingPermissionCallback = callback
        requestPermissions.launch(missing.toTypedArray())
    }

    // ---- Lifecycle ----------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webappID = intent.getIntExtra(Const.INTENT_WEBAPPID, -1)
        EntryPointUtils.entryPointReached(this)
        val loaded = DataManager.getInstance().getWebApp(webappID)
        if (loaded == null) { finish(); return }
        webapp = loaded

        runtime = GeckoRuntimeHolder.get(this)
        setContentView(R.layout.activity_browser)
        rootView = findViewById(R.id.browserRoot)
        geckoView = findViewById(R.id.geckoview)
        progressBar = findViewById(R.id.progressBar)

        if (webapp.isKeepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (webapp.isShowFullscreen) hideSystemBars()
        else if (DataManager.getInstance().settings.alwaysShowSoftwareButtons) showSystemBars()

        session = createSession()
        geckoView.setSession(session)
        usageGuard = UsageGuard(this, webapp, usageListener)
        findViewById<View>(R.id.limitClose).setOnClickListener { finishAndRemoveTask() }
        val blockedUsage = usageGuard.blockedAtLaunch()
        if (blockedUsage != null) showLimitOverlay(blockedUsage) else loadInitialPage()
        setupGestures()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        if (webapp.isBiometricProtection) {
            rootView.visibility = View.INVISIBLE
            BiometricPromptHelper(this).showPrompt(
                { rootView.visibility = View.VISIBLE }, { finish() },
                getString(R.string.bioprompt_restricted_webapp)
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newId = intent.getIntExtra(Const.INTENT_WEBAPPID, -1)
        if (newId != -1 && newId != webappID) {
            // A different web app was routed into this task; give it its own activity instead.
            WebViewLauncher.startWebView(DataManager.getInstance().getWebApp(newId) ?: return, this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!limitBlocked) session.setActive(true)
        if (webapp.isAutoreload && webapp.timeAutoreload > 0) scheduleReload()
        usageGuard.start()
    }

    override fun onPause() {
        super.onPause()
        popupMenu?.dismiss()
        reloadHandler.removeCallbacksAndMessages(null)
        usageGuard.stop()
        if (!webapp.isAllowMediaPlaybackInBackground) session.setActive(false)
        if (webapp.isClearCache || DataManager.getInstance().settings.isClearCache) {
            runtime.storageController.clearData(org.mozilla.geckoview.StorageController.ClearFlags.ALL_CACHES)
        }
    }

    override fun onDestroy() {
        if (::session.isInitialized) {
            session.close()
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        GeckoRuntimeHolder.onConfigurationChanged(newConfig)
    }

    // ---- Session setup ------------------------------------------------------------------------

    private fun createSession(): GeckoSession {
        val desktop = webapp.isRequestDesktop
        val settings = GeckoSessionSettings.Builder()
            .allowJavascript(webapp.isAllowJs)
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .displayMode(GeckoSessionSettings.DISPLAY_MODE_STANDALONE)
            .suspendMediaWhenInactive(!webapp.isAllowMediaPlaybackInBackground)
            .useTrackingProtection(true)
            .apply {
                if (webapp.isUseContainer) contextId(contextIdFor(webapp))
                val ua = webapp.userAgent
                if (webapp.isUseCustomUserAgent && !ua.isNullOrBlank()) {
                    userAgentOverride(ua.replace("\u0000", "").replace("\n", "").replace("\r", ""))
                }
            }
            .build()

        val s = GeckoSession(settings)
        s.navigationDelegate = navigationDelegate
        s.progressDelegate = progressDelegate
        s.contentDelegate = contentDelegate
        s.permissionDelegate = permissionDelegate
        s.promptDelegate = GeckoPromptDelegate(this, ::pickFiles)
        s.open(runtime)
        return s
    }

    private fun pickFiles(mimes: Array<String>, multiple: Boolean, callback: (Array<Uri>?) -> Unit) {
        pendingFileCallback = callback
        try {
            if (multiple) pickMultiple.launch(mimes) else pickSingle.launch(mimes)
        } catch (e: Exception) {
            pendingFileCallback = null
            callback(null)
            com.cylonid.nativealpha.util.NotificationUtils.showToast(this, getString(R.string.no_filemanager))
        }
    }

    private fun loadInitialPage() {
        val saved = if (webapp.isRestorePage) SessionStore.load(this, webapp.ID) else null
        if (saved != null) {
            session.restoreState(saved)
        } else {
            loadUrl(webapp.baseUrl)
        }
    }

    protected fun loadUrl(url: String) {
        if (url.startsWith("http://") && !webapp.isAllowHttp) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.no_https_dialog_title)
                .setMessage(R.string.no_https_dialog_msg)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.no_https_dialog_accept) { _, _ ->
                    webapp.isAllowHttp = true
                    webapp.isOverrideGlobalSettings = true
                    DataManager.getInstance().replaceWebApp(webapp)
                    session.loadUri(url)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> if (currentUrl == null) finish() }
                .show()
            return
        }
        session.load(GeckoSession.Loader().uri(url).apply {
            if (webapp.isSendSavedataRequest) additionalHeaders(mapOf("Save-Data" to "on"))
        })
    }

    // ---- Navigation ---------------------------------------------------------------------------

    /**
     * Decides what happens with a top-level navigation. Returns null to allow it in place;
     * otherwise the request is denied (and possibly handled elsewhere). Subclass hook for the
     * navigation lock.
     */
    protected open fun interceptNavigation(uri: Uri, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
        if (limitBlocked) return GeckoResult.deny()
        if (isSameSite(uri)) return null
        val host = HostMatcher.normalize(uri.host ?: return null)
        if (host in temporarilyAllowedHosts) return null
        return when (webapp.effectiveNavLockMode) {
            WebApp.NAV_LOCK_OPEN_EXTERNAL -> { openExternally(uri); GeckoResult.deny() }
            WebApp.NAV_LOCK_BLOCK -> {
                Snackbar.make(rootView, getString(R.string.nav_blocked, host), Snackbar.LENGTH_LONG).show()
                GeckoResult.deny()
            }
            WebApp.NAV_LOCK_PROMPT -> promptForNavigation(uri, host)
            else -> null
        }
    }

    private fun promptForNavigation(uri: Uri, host: String): GeckoResult<AllowOrDeny> {
        val result = GeckoResult<AllowOrDeny>()
        var done = false
        fun settle(v: AllowOrDeny) { if (!done) { done = true; result.complete(v) } }
        val options = arrayOf(
            getString(R.string.nav_open_here_once),
            getString(R.string.nav_always_allow, HostMatcher.registrableDomain(host)),
            getString(R.string.open_in_browser),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.nav_prompt_title, host))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { temporarilyAllowedHosts += host; settle(AllowOrDeny.ALLOW) }
                    1 -> {
                        val domain = HostMatcher.registrableDomain(host)
                        val stored = DataManager.getInstance().getWebAppIgnoringGlobalOverride(webapp.ID, true)
                        if (stored != null) {
                            val list = stored.allowedHosts ?: mutableListOf()
                            if (domain !in list) list += domain
                            stored.allowedHosts = list
                            DataManager.getInstance().replaceWebApp(stored)
                        }
                        webapp.allowedHosts = (webapp.allowedHosts ?: mutableListOf()).apply { if (domain !in this) add(domain) }
                        settle(AllowOrDeny.ALLOW)
                    }
                    else -> { openExternally(uri); settle(AllowOrDeny.DENY) }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> settle(AllowOrDeny.DENY) }
            .setOnCancelListener { settle(AllowOrDeny.DENY) }
            .show()
        return result
    }

    protected fun isSameSite(uri: Uri): Boolean {
        val host = uri.host ?: return true
        val base = Uri.parse(webapp.baseUrl).host ?: return true
        return HostMatcher.isSameSite(host, base, webapp.allowedHostList())
    }

    protected fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: Exception) {
            com.cylonid.nativealpha.util.NotificationUtils.showToast(this, getString(R.string.no_app_for_link))
        }
    }

    private val navigationDelegate = object : NavigationDelegate {
        override fun onCanGoBack(session: GeckoSession, value: Boolean) { canGoBack = value }
        override fun onCanGoForward(session: GeckoSession, value: Boolean) { canGoForward = value }

        override fun onLocationChange(
            session: GeckoSession, url: String?,
            perms: MutableList<PermissionDelegate.ContentPermission>, hasUserGesture: Boolean
        ) {
            currentUrl = url
        }

        override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
            val uri = Uri.parse(request.uri)
            val scheme = uri.scheme?.lowercase()
            if (scheme != null && scheme !in setOf("http", "https", "about", "data", "blob", "moz-extension", "resource", "file", "javascript")) {
                // tel:, mailto:, intent:, market: … are handed to Android.
                if (scheme == "intent") {
                    try {
                        val i = Intent.parseUri(request.uri, Intent.URI_INTENT_SCHEME)
                        i.addCategory(Intent.CATEGORY_BROWSABLE); i.component = null; i.selector = null
                        startActivity(i)
                    } catch (e: Exception) {
                        val fallback = try { Intent.parseUri(request.uri, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") } catch (_: Exception) { null }
                        if (fallback != null) session.loadUri(fallback)
                    }
                } else openExternally(uri)
                return GeckoResult.deny()
            }
            if (scheme == "http" || scheme == "https") {
                interceptNavigation(uri, request)?.let { return it }
            }
            if (request.target == NavigationDelegate.TARGET_WINDOW_NEW) {
                // Web apps are single-window: open target=_blank links in place.
                session.loadUri(request.uri)
                return GeckoResult.deny()
            }
            return GeckoResult.allow()
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            this@BrowserActivity.session.loadUri(uri)
            return null
        }

        override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String> =
            GeckoResult.fromValue(ErrorPages.build(this@BrowserActivity, uri, error))
    }

    private val progressDelegate = object : ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            if (DataManager.getInstance().settings.isShowProgressbar || currentlyReloading) {
                progressBar.visibility = View.VISIBLE
            }
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            progressBar.setProgressCompat(progress, true)
            if (progress >= 100) { progressBar.visibility = View.GONE; currentlyReloading = false }
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            progressBar.visibility = View.GONE
            currentlyReloading = false
        }

        override fun onSessionStateChange(session: GeckoSession, state: GeckoSession.SessionState) {
            if (webapp.isRestorePage) SessionStore.save(this@BrowserActivity, webapp.ID, state)
        }
    }

    private val contentDelegate = object : ContentDelegate {
        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            inFullscreen = fullScreen
            if (fullScreen) hideSystemBars() else if (!webapp.isShowFullscreen) showSystemBars()
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            DownloadHelper.save(this@BrowserActivity, response)
        }

        override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: ContentDelegate.ContextElement) {
            if (webapp.alwaysUseFallbackContextMenu) return
            showPopupMenu(element)
        }

        override fun onCloseRequest(session: GeckoSession) { finishAndRemoveTask() }

        override fun onCrash(session: GeckoSession) = recoverSession()
        override fun onKill(session: GeckoSession) = recoverSession()
    }

    private fun recoverSession() {
        if (isFinishing) return
        session.close()
        session = createSession()
        geckoView.setSession(session)
        loadUrl(currentUrl ?: webapp.baseUrl)
    }

    // ---- Permissions --------------------------------------------------------------------------

    private val permissionDelegate = object : PermissionDelegate {
        override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<out String>?, callback: PermissionDelegate.Callback) {
            if (permissions.isNullOrEmpty()) { callback.grant(); return }
            requestAndroidPermissions(arrayOf(*permissions)) { ok -> if (ok) callback.grant() else callback.reject() }
        }

        override fun onContentPermissionRequest(session: GeckoSession, perm: PermissionDelegate.ContentPermission): GeckoResult<Int> {
            val allow = PermissionDelegate.ContentPermission.VALUE_ALLOW
            val deny = PermissionDelegate.ContentPermission.VALUE_DENY
            return when (perm.permission) {
                PermissionDelegate.PERMISSION_GEOLOCATION -> askWebAppPermission(
                    "location", webapp.isAllowLocationAccess,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                ) { webapp.isAllowLocationAccess = true }
                PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> askWebAppPermission(
                    "drm", webapp.isDrmAllowed, emptyArray()
                ) { webapp.isDrmAllowed = true }
                PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
                PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
                PermissionDelegate.PERMISSION_STORAGE_ACCESS -> GeckoResult.fromValue(allow)
                else -> GeckoResult.fromValue(deny)
            }
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession, uri: String,
            video: Array<out PermissionDelegate.MediaSource>?, audio: Array<out PermissionDelegate.MediaSource>?,
            callback: PermissionDelegate.MediaCallback
        ) {
            val cam = video?.firstOrNull { it.source == PermissionDelegate.MediaSource.SOURCE_CAMERA }
            val mic = audio?.firstOrNull { it.source == PermissionDelegate.MediaSource.SOURCE_MICROPHONE }
            if (cam == null && mic == null) { callback.reject(); return }

            val camResult = if (cam != null) askWebAppPermission("camera", webapp.isCameraPermission, arrayOf(Manifest.permission.CAMERA)) { webapp.isCameraPermission = true } else GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)
            camResult.accept { camOk ->
                val micResult = if (mic != null) askWebAppPermission("microphone", webapp.isMicrophonePermission, arrayOf(Manifest.permission.RECORD_AUDIO)) { webapp.isMicrophonePermission = true } else GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)
                micResult.accept { micOk ->
                    val v = cam.takeIf { camOk == PermissionDelegate.ContentPermission.VALUE_ALLOW }
                    val a = mic.takeIf { micOk == PermissionDelegate.ContentPermission.VALUE_ALLOW }
                    if (v == null && a == null) callback.reject() else callback.grant(v, a)
                }
            }
        }
    }

    /**
     * If the web app already allows [kind], only the Android runtime permission is requested.
     * Otherwise the user is asked once; "Allow" is persisted on the web app.
     */
    private fun askWebAppPermission(kind: String, alreadyAllowed: Boolean, androidPerms: Array<String>, persist: () -> Unit): GeckoResult<Int> {
        val result = GeckoResult<Int>()
        val allow = PermissionDelegate.ContentPermission.VALUE_ALLOW
        val deny = PermissionDelegate.ContentPermission.VALUE_DENY
        val grantAndroid = {
            if (androidPerms.isEmpty()) result.complete(allow)
            else requestAndroidPermissions(androidPerms) { ok -> result.complete(if (ok) allow else deny) }
        }
        if (alreadyAllowed) { grantAndroid(); return result }
        MaterialAlertDialogBuilder(this)
            .setTitle(stringByName("dialog_permission_${kind}_title"))
            .setMessage(stringByName("dialog_permission_${kind}_txt"))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                webapp.isOverrideGlobalSettings = true
                persist()
                DataManager.getInstance().replaceWebApp(webapp)
                grantAndroid()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.complete(deny) }
            .setOnCancelListener { result.complete(deny) }
            .show()
        return result
    }

    private fun stringByName(name: String): String {
        val id = resources.getIdentifier(name, "string", packageName)
        return if (id != 0) getString(id) else name
    }

    // ---- UI: back, menu, gestures, system bars ------------------------------------------------

    private fun handleBack() {
        if (inFullscreen) { session.exitFullScreen(); return }
        if (canGoBack) { session.goBack(); return }
        if (quitOnNextBackpress) {
            quitOnNextBackpress = false
            moveTaskToBack(true)
            return
        }
        loadUrl(webapp.baseUrl)
        quitOnNextBackpress = true
    }

    private fun showPopupMenu(element: ContentDelegate.ContextElement?) {
        val anchor = findViewById<View>(R.id.anchorCenterScreen)
        val menu = IconPopupMenuHelper.getMenu(anchor, R.menu.wv_context_menu, this)
        popupMenu = menu
        val link = element?.linkUri
        val url = link ?: currentUrl ?: ""
        val title = if (url.length < 40) url else url.substring(0, 40) + "…"
        menu.menu.findItem(R.id.cmUrl).title = SpannableString(title).apply {
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, length, 0)
        }
        menu.menu.findItem(R.id.cmItemForward).isVisible = canGoForward
        menu.menu.findItem(R.id.cmOpenLinkExternal).isVisible = link != null
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.cmItemForward -> session.goForward()
                R.id.cmItemBack -> handleBack()
                R.id.cmItemReload -> { currentlyReloading = true; session.reload() }
                R.id.cmItemCopyUrl -> getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("URL", url))
                R.id.cmItemShareUrl -> ShareCompat.IntentBuilder(this).setType("text/plain").setText(url).startChooser()
                R.id.cmOpenLinkExternal -> openExternally(Uri.parse(url))
                R.id.cmItemCloseWebApp -> finishAndRemoveTask()
                R.id.cmMainMenu -> startActivity(Intent(this, MainActivity::class.java))
                R.id.cmShowAdblockProviders -> startActivity(Intent(this, AdblockDashboardActivity::class.java))
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        menu.show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val threshold = 100 * resources.displayMetrics.density / 2.6f
        var startX = 0f; var startY = 0f; var stopX = 0f; var stopY = 0f; var swiping = false
        geckoView.setOnTouchListener { _, event ->
            val gs = DataManager.getInstance().settings
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    swiping = true; startX = event.getX(0); startY = event.getY(0); stopX = startX; stopY = startY
                }
                MotionEvent.ACTION_MOVE -> if (swiping) { stopX = event.getX(0); stopY = event.getY(0) }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (!swiping) return@setOnTouchListener false
                    swiping = false
                    val fingers = event.pointerCount
                    if (abs(startX - stopX) > threshold) {
                        val left = startX > stopX
                        if (fingers == 3 && gs.isThreeFingerMultitouch) {
                            val dm = DataManager.getInstance()
                            val next = if (left) dm.getPredecessor(webappID) else dm.getSuccessor(webappID)
                            WebViewLauncher.startWebView(next, this); finish()
                        } else if (gs.isTwoFingerMultitouch) {
                            if (left) { if (canGoForward) session.goForward() } else handleBack()
                        }
                    } else if (gs.isMultitouchReload && stopY - startY > threshold) {
                        currentlyReloading = true; session.reload()
                    }
                }
            }
            false
        }
    }

    private fun scheduleReload() {
        reloadHandler.postDelayed({
            currentlyReloading = true
            session.reload()
            scheduleReload()
        }, webapp.timeAutoreload * 1000L)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars() {
        if (webapp.isShowFullscreen) return
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    // ---- Daily limit -----------------------------------------------------------------------

    private val usageListener = object : UsageGuard.Listener {
        override fun onLimitNear(usedMs: Long, limitMs: Long) {
            Snackbar.make(rootView, getString(R.string.limit_near, UsageMath.formatDuration(limitMs - usedMs)), Snackbar.LENGTH_LONG).show()
        }

        override fun onLimitReachedWarn(usedMs: Long, limitMs: Long) {
            if (isFinishing) return
            MaterialAlertDialogBuilder(this@BrowserActivity)
                .setTitle(R.string.limit_reached_title)
                .setMessage(getString(R.string.limit_reached_warn_text, UsageMath.formatDuration(usedMs)))
                .setCancelable(false)
                .setPositiveButton(R.string.close_web_app) { _, _ -> usageGuard.warningDismissed(0); finishAndRemoveTask() }
                .setNegativeButton(R.string.limit_snooze) { _, _ -> usageGuard.warningDismissed(SNOOZE_MINUTES) }
                .show()
        }

        override fun onLimitReachedBlock(usedMs: Long, limitMs: Long) = showLimitOverlay(usedMs)

        override fun onLimitCleared() = hideLimitOverlay()
    }

    private fun showLimitOverlay(usedMs: Long) {
        limitBlocked = true
        popupMenu?.dismiss()
        session.setActive(false)
        findViewById<TextView>(R.id.limitMessage).text =
            getString(R.string.limit_reached_block_text, UsageMath.formatDuration(usedMs))
        findViewById<View>(R.id.limitOverlay).visibility = View.VISIBLE
        if (inFullscreen) session.exitFullScreen()
    }

    private fun hideLimitOverlay() {
        if (!limitBlocked) return
        limitBlocked = false
        findViewById<View>(R.id.limitOverlay).visibility = View.GONE
        session.setActive(true)
        if (currentUrl == null) loadInitialPage()
    }

    companion object {
        const val SNOOZE_MINUTES = 5

        @JvmStatic
        fun contextIdFor(webapp: WebApp) = "nativealpha-webapp-${webapp.ID}"
    }
}
