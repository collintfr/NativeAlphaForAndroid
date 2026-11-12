package com.cylonid.nativealpha.browser

import android.content.Context
import org.mozilla.geckoview.GeckoSession

/** Persists GeckoSession history per web app for the "restore last page" option. */
object SessionStore {
    private const val PREFS = "gecko_session_state"

    fun save(context: Context, webAppId: Int, state: GeckoSession.SessionState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(webAppId.toString(), state.toString()).apply()
    }

    fun load(context: Context, webAppId: Int): GeckoSession.SessionState? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(webAppId.toString(), null) ?: return null
        return try { GeckoSession.SessionState.fromString(raw) } catch (e: Exception) { null }
    }

    fun clear(context: Context, webAppId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(webAppId.toString()).apply()
    }
}
