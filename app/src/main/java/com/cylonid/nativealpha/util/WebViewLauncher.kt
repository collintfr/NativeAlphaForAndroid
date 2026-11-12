package com.cylonid.nativealpha.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cylonid.nativealpha.browser.BrowserActivity
import com.cylonid.nativealpha.model.WebApp

object WebViewLauncher {
    @JvmStatic
    fun startWebView(webapp: WebApp, c: Context) {
        val intent = createWebViewIntent(webapp, c)
        if (c !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(intent)
    }

    /**
     * Intent that opens [webapp] in its own document task. The data URI makes each web app a
     * distinct task in recents (documentLaunchMode=intoExisting).
     */
    @JvmStatic
    fun createWebViewIntent(webapp: WebApp, c: Context): Intent {
        val intent = Intent(c, BrowserActivity::class.java)
        if (webapp.isBiometricProtection) intent.flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        intent.putExtra(Const.INTENT_WEBAPPID, webapp.ID)
        intent.data = Uri.parse("nativealpha://webapp/" + webapp.ID)
        intent.action = Intent.ACTION_VIEW
        return intent
    }
}
