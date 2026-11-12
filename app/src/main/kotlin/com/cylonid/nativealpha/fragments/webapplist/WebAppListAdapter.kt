package com.cylonid.nativealpha.fragments.webapplist

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.transition.Visibility
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.startActivity
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.ShortcutDialogFragment
import android.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import com.cylonid.nativealpha.WebAppSettingsActivity
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.util.Const
import com.cylonid.nativealpha.util.WebViewLauncher.startWebView
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeAdapter
import com.google.android.material.internal.VisibilityAwareImageButton
import java.util.ArrayList

class WebAppListAdapter(dataSet: List<WebApp> = emptyList(), private val activityOfFragment: Activity)
    : DragDropSwipeAdapter<WebApp, WebAppListAdapter.ViewHolder>(dataSet) {

    class ViewHolder(webAppLayout: View) : DragDropSwipeAdapter.ViewHolder(webAppLayout) {
        val dragAnchor : ImageView = itemView.findViewById(R.id.dragAnchor)
        val titleView: TextView = itemView.findViewById(R.id.btnWebAppTitle)

    }

    override fun getViewHolder(itemView: View) = ViewHolder(itemView)
    override fun onBindViewHolder(item: WebApp, viewHolder: ViewHolder, position: Int) {
        viewHolder.titleView.text = item.title
        viewHolder.titleView.setOnClickListener {
            openWebView(
                item
            )
        }
        viewHolder.titleView.setOnLongClickListener { anchor ->
            val menu = PopupMenu(activityOfFragment, anchor)
            menu.menu.add(0, 1, 0, R.string.add_to_home_screen)
            menu.menu.add(0, 2, 1, R.string.action_settings)
            menu.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    1 -> (activityOfFragment as? FragmentActivity)?.let {
                        ShortcutDialogFragment.newInstance(item).show(it.supportFragmentManager, "SCFetcher-" + item.ID)
                    }
                    2 -> activityOfFragment.startActivity(
                        Intent(activityOfFragment, WebAppSettingsActivity::class.java)
                            .putExtra(Const.INTENT_WEBAPPID, item.ID)
                    )
                }
                true
            }
            menu.show()
            true
        }
    }

    fun updateWebAppList() {
        dataSet = DataManager.getInstance().activeWebsites
    }


    private fun openWebView(webapp: WebApp) {
        startWebView(webapp, activityOfFragment)
    }


    override fun getViewToTouchToStartDraggingItem(item: WebApp, viewHolder: ViewHolder, position: Int) = viewHolder.dragAnchor

}