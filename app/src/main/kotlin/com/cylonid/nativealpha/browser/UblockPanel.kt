package com.cylonid.nativealpha.browser

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

/**
 * Connects a browsing session to uBlock Origin's toolbar button ("browser action") and shows its
 * popup — the per-site on/off switch, element zapper and logger — in a bottom sheet.
 */
class UblockPanel(private val activity: AppCompatActivity, private val session: GeckoSession) {
    private var extension: WebExtension? = null
    private var defaultAction: WebExtension.Action? = null
    private var tabAction: WebExtension.Action? = null
    private var dialog: BottomSheetDialog? = null

    val isAvailable: Boolean get() = extension != null && (tabAction ?: defaultAction) != null

    private val delegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            if (session == null) defaultAction = action else tabAction = action
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = showPopup()
        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = showPopup()
    }

    fun attach() {
        GeckoRuntimeHolder.whenUblockReady { ext ->
            activity.runOnUiThread {
                if (ext == null || activity.isDestroyed) return@runOnUiThread
                extension = ext
                ext.setActionDelegate(delegate)
                session.webExtensionController.setActionDelegate(ext, delegate)
            }
        }
    }

    /** Opens uBO's popup for the current page. Returns false if uBO isn't ready. */
    fun open(): Boolean {
        val action = (tabAction?.withDefault(defaultAction ?: tabAction!!)) ?: defaultAction ?: return false
        action.click()
        return true
    }

    private fun showPopup(): GeckoResult<GeckoSession>? {
        if (activity.isFinishing || activity.isDestroyed) return null
        dialog?.dismiss()
        val popupSession = GeckoSession()
        popupSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) { dialog?.dismiss() }
        }
        popupSession.open(GeckoRuntimeHolder.get(activity))

        val view = GeckoView(activity).apply {
            setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW) // SurfaceView doesn't render reliably inside dialogs
            setSession(popupSession)
        }
        val height = (activity.resources.displayMetrics.heightPixels * 0.7f).toInt()
        val container = FrameLayout(activity).apply {
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        }
        dialog = BottomSheetDialog(activity).apply {
            setContentView(container)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false // let the popup scroll instead of dragging the sheet
            setOnDismissListener {
                view.releaseSession()
                popupSession.close()
                dialog = null
                // uBO's power switch only applies after a reload; do it for the user.
                this@UblockPanel.session.reload()
            }
            show()
        }
        Log.d("NA-Browser", "uBlock Origin popup opened")
        return GeckoResult.fromValue(popupSession)
    }
}
