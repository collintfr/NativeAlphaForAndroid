package com.cylonid.nativealpha.browser

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.cylonid.nativealpha.R
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Shows uBlock Origin's own dashboard (filter lists, my filters, trusted sites). */
class AdblockDashboardActivity : AppCompatActivity() {
    private var session: GeckoSession? = null
    private var canGoBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adblock_dashboard)
        val geckoView = findViewById<GeckoView>(R.id.geckoview)
        val status = findViewById<TextView>(R.id.status)
        val runtime = GeckoRuntimeHolder.get(this)

        GeckoRuntimeHolder.whenUblockReady { ext ->
            runOnUiThread {
                val url = ext?.metaData?.optionsPageUrl
                if (url == null) {
                    status.visibility = View.VISIBLE
                    status.setText(R.string.adblock_unavailable)
                    return@runOnUiThread
                }
                val s = GeckoSession()
                s.navigationDelegate = object : GeckoSession.NavigationDelegate {
                    override fun onCanGoBack(session: GeckoSession, value: Boolean) { canGoBack = value }
                }
                s.promptDelegate = GeckoPromptDelegate(this, { _, _, cb -> cb(null) })
                s.open(runtime)
                geckoView.setSession(s)
                s.loadUri(url)
                session = s
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) session?.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        session?.close()
        super.onDestroy()
    }
}
