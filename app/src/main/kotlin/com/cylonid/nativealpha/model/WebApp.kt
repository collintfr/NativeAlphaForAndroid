package com.cylonid.nativealpha.model

import android.app.Activity
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.ShortcutIconUtils
import com.cylonid.nativealpha.util.ShortcutHelper
import com.cylonid.nativealpha.util.Utility
import java.util.*

data class AdblockConfig(val label: String, val value: String)

data class WebApp(var baseUrl: String, val ID: Int) {
    var title: String
    var isActiveEntry = true
    var isOverrideGlobalSettings = true

    var isOpenUrlExternal = false
    var isAllowCookies = true
    var isAllowThirdPartyCookies = false
    var isRestorePage = false
    var isAllowJs = true
    var isRequestDesktop = false
    var isClearCache = false
    /** Gecko private session: nothing (cookies, storage, history) survives closing the app. */
    var isPrivateMode = false
    var isUseAdblock = false
    var isSendSavedataRequest = false
    var isBlockImages = false
    var isAllowHttp = false
    var isAllowLocationAccess = false
    var userAgent: String? = null
    var isUseCustomUserAgent = false
    var isAutoreload = false
    var timeAutoreload = 0
    var isForceDarkMode = false
    var isUseTimespanDarkMode = false
    var timespanDarkModeBegin: String? = "22:00"
    var timespanDarkModeEnd: String? = "06:00"
    var isIgnoreSslErrors = false
    var isShowExpertSettings = false
    var isSafeBrowsing = false
    var isBlockThirdPartyRequests = false
    var containerId: Int = Const.NO_CONTAINER
    var isUseContainer = false
    var isDrmAllowed = false
    var isShowFullscreen = false
    var isKeepAwake = false
    var isCameraPermission = false
    var isMicrophonePermission = false
    var isEnableZooming = false
    var isBiometricProtection = false
    var isAllowMediaPlaybackInBackground = false
    var order = 0
    var alwaysUseFallbackContextMenu = false
    var adBlockSettings = mutableListOf<AdblockConfig>()

    // ---- Usage controls (per web app, never inherited from the global template) ----
    // Gson bypasses constructors for this class, so 0/null must be the sensible defaults.
    /** Daily time limit in minutes; 0 = no limit. */
    var dailyLimitMinutes: Int = 0
    /** One of LIMIT_MODE_*. */
    var limitMode: Int = LIMIT_MODE_WARN
    /** One of NAV_LOCK_*. */
    var navLockMode: Int = NAV_LOCK_OFF
    /** Extra hosts (and their subdomains) the navigation lock lets through, e.g. SSO providers. */
    var allowedHosts: MutableList<String>? = null

    /** Navigation lock mode, honouring the legacy "open external URLs in browser" flag. */
    val effectiveNavLockMode: Int
        get() = if (navLockMode == NAV_LOCK_OFF && isOpenUrlExternal) NAV_LOCK_OPEN_EXTERNAL else navLockMode

    fun allowedHostList(): List<String> = allowedHosts ?: emptyList()

    companion object {
        const val LIMIT_MODE_WARN = 0
        const val LIMIT_MODE_BLOCK = 1

        const val NAV_LOCK_OFF = 0
        const val NAV_LOCK_OPEN_EXTERNAL = 1
        const val NAV_LOCK_PROMPT = 2
        const val NAV_LOCK_BLOCK = 3
    }

    init {
        title = baseUrl.replace("http://", "").replace("https://", "").replace("www.", "")
        initDefaultSettings()
    }

    constructor(baseUrl: String, ID: Int, order: Int): this(baseUrl, ID) {
        this.order = order
    }

    constructor(baseUrl: String, ID: Int, adBlockSettings: MutableList<AdblockConfig>): this(baseUrl, ID) {
        this.adBlockSettings = adBlockSettings
    }

    constructor(other: WebApp) : this(other.baseUrl, other.ID) {
        title = other.title
        isOverrideGlobalSettings = other.isOverrideGlobalSettings
        containerId = other.containerId
        isUseContainer = other.isUseContainer
        copySettings(other)
        dailyLimitMinutes = other.dailyLimitMinutes
        limitMode = other.limitMode
        navLockMode = other.navLockMode
        allowedHosts = other.allowedHosts?.toMutableList()
    }



    //This part of the copy ctor should be callable independently from actual object construction to copy values of the global web app template
    fun copySettings(other: WebApp) {
        isOpenUrlExternal = other.isOpenUrlExternal
        isAllowCookies = other.isAllowCookies
        isAllowThirdPartyCookies = other.isAllowThirdPartyCookies
        isRestorePage = other.isRestorePage
        isAllowJs = other.isAllowJs
        isActiveEntry = other.isActiveEntry
        isRequestDesktop = other.isRequestDesktop
        isClearCache = other.isClearCache
        isPrivateMode = other.isPrivateMode
        isUseAdblock = other.isUseAdblock
        isSendSavedataRequest = other.isSendSavedataRequest
        isBlockImages = other.isBlockImages
        isAllowHttp = other.isAllowHttp
        isAllowLocationAccess = other.isAllowLocationAccess
        userAgent = other.userAgent
        isUseCustomUserAgent = other.isUseCustomUserAgent
        isAutoreload = other.isAutoreload
        timeAutoreload = other.timeAutoreload
        isForceDarkMode = other.isForceDarkMode
        isUseTimespanDarkMode = other.isUseTimespanDarkMode
        timespanDarkModeBegin = other.timespanDarkModeBegin
        timespanDarkModeEnd = other.timespanDarkModeEnd
        isIgnoreSslErrors = other.isIgnoreSslErrors
        isShowExpertSettings = other.isShowExpertSettings
        isSafeBrowsing = other.isSafeBrowsing
        isBlockThirdPartyRequests = other.isBlockThirdPartyRequests
        isDrmAllowed = other.isDrmAllowed
        isShowFullscreen = other.isShowFullscreen
        isKeepAwake = other.isKeepAwake
        isCameraPermission = other.isCameraPermission
        isMicrophonePermission = other.isMicrophonePermission
        isEnableZooming = other.isEnableZooming
        isBiometricProtection = other.isBiometricProtection
        isAllowMediaPlaybackInBackground = other.isAllowMediaPlaybackInBackground
        order = other.order
        alwaysUseFallbackContextMenu = other.alwaysUseFallbackContextMenu
        adBlockSettings = other.adBlockSettings
    }

    private fun initDefaultSettings() {
        if (baseUrl.contains("facebook.com")) {
            userAgent = Const.DESKTOP_USER_AGENT
            isUseCustomUserAgent = true
        }
    }

    /*
        This function is used for settings where the ctor needs to have a different setting because
        we want different behaviour for already existing and newly created Web Apps.
            */
    fun applySettingsForNewWebApp() {
        isOverrideGlobalSettings = false
    }

    fun markInactive(activity: Activity) {
        isActiveEntry = false
        ShortcutIconUtils.deleteShortcuts(
            listOf(ID),
            activity
        )
        ShortcutHelper.deleteIcon(activity, ID)
        com.cylonid.nativealpha.usage.UsageRepository.get(activity).deleteForWebApp(ID)
        com.cylonid.nativealpha.browser.SessionStore.clear(activity, ID)
    }


    val alphanumericBaseUrl: String
        get() = baseUrl.replace("\\P{Alnum}".toRegex(), "").replace("https", "").replace("http", "").replace("www", "")

}
