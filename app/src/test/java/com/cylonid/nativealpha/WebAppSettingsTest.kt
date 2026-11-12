package com.cylonid.nativealpha

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.test.core.app.ApplicationProvider
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.ui.AppViewModel
import com.cylonid.nativealpha.ui.NativeAlphaTheme
import com.cylonid.nativealpha.ui.WebAppSettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Edits on the per-app settings screen must reach the stored web app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h2000dp")
class WebAppSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun renameAndToggleArePersisted() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dm = DataManager.getInstance()
        dm.loadAppData()
        val w = WebApp("https://example.org", dm.incrementedID, dm.incrementedOrder)
        w.title = "Old name"
        w.isOverrideGlobalSettings = true // show detailed switches
        dm.addWebsite(w)
        val id = w.ID
        assertTrue(dm.getWebAppIgnoringGlobalOverride(id, true)!!.isAllowJs)

        val vm = AppViewModel(app)
        var closed = false
        compose.setContent { NativeAlphaTheme { WebAppSettingsScreen(vm, id) { closed = true } } }

        // Title field: clear (like holding backspace) and type a new name.
        val titleField = hasSetTextAction() and hasText("Shortcut Title")
        compose.onAllNodes(titleField and hasText("Old name")).onFirst().assertExists()
        compose.onAllNodes(titleField).onFirst().performTextClearance()
        compose.onAllNodes(titleField).onFirst().performTextInput("New name")
        compose.onAllNodes(titleField and hasText("New name")).onFirst().assertExists() // field shows the edit

        // Turn JavaScript off.
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("JavaScript"))
        compose.onNodeWithText("JavaScript").performClick()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertTrue(closed)

        val stored = dm.getWebAppIgnoringGlobalOverride(id, true)!!
        assertEquals("New name", stored.title)
        assertFalse(stored.isAllowJs)
    }
}
