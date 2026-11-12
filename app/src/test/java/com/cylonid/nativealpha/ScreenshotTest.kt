package com.cylonid.nativealpha

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.cylonid.nativealpha.model.DataManager
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.ui.AppViewModel
import com.cylonid.nativealpha.ui.HomeScreen
import com.cylonid.nativealpha.ui.NativeAlphaTheme
import com.cylonid.nativealpha.ui.UsageScreen
import com.cylonid.nativealpha.ui.WebAppSettingsScreen
import com.cylonid.nativealpha.usage.UsageMath
import com.cylonid.nativealpha.usage.UsageRepository
import com.cylonid.nativealpha.util.ShortcutHelper
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens to PNG (build/screenshots) for visual review. Not an assertion test.
 * Run with: ./gradlew testStandardDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: AppViewModel

    @Before fun seed() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dm = DataManager.getInstance()
        dm.loadAppData()
        if (dm.websites.isEmpty()) {
            val sites = listOf(
                Triple("https://mail.proton.me", "Proton Mail", 0xFF6D4AFF.toInt()),
                Triple("https://calendar.google.com", "Calendar", 0xFF1A73E8.toInt()),
                Triple("https://www.bankaustria.at", "Banking", 0xFFE2001A.toInt()),
                Triple("https://news.ycombinator.com", "Hacker News", 0xFFFF6600.toInt()),
                Triple("https://github.com", "GitHub", 0xFF24292F.toInt()),
            )
            sites.forEachIndexed { i, (url, title, color) ->
                val w = WebApp(url, dm.incrementedID, dm.incrementedOrder)
                w.title = title
                if (i == 3) { w.dailyLimitMinutes = 30; w.limitMode = WebApp.LIMIT_MODE_BLOCK; w.navLockMode = WebApp.NAV_LOCK_PROMPT; w.allowedHosts = mutableListOf("ycombinator.com") }
                if (i == 1) w.dailyLimitMinutes = 60
                dm.addWebsite(w)
            }
            val repo = UsageRepository.get(app)
            val now = System.currentTimeMillis()
            val day = UsageMath.startOfDay(now)
            val minutes = listOf(42, 18, 6, 31, 0)
            minutes.forEachIndexed { id, m -> if (m > 0) repo.updateSessionEnd(repo.startSession(id, day + 3_600_000L), day + 3_600_000L + m * 60_000L) }
            for (d in 1..6) repo.updateSessionEnd(repo.startSession(d % 4, day - d * 86_400_000L), day - d * 86_400_000L + (20 + d * 9) * 60_000L)
        }
        // Files are reset per test while DataManager (a singleton) is not, so always write icons.
        val colors = listOf(0xFF6D4AFF.toInt(), 0xFF1A73E8.toInt(), 0xFFE2001A.toInt(), 0xFFFF6600.toInt())
        colors.forEachIndexed { id, c -> ShortcutHelper.saveIcon(app, id, ShortcutHelper.renderAdaptiveBitmap(logo(c, dm.websites[id].title.first()))) }
        vm = AppViewModel(app)
        vm.refresh()
    }

    private fun logo(color: Int, letter: Char): Bitmap {
        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(color)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; textSize = 120f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        c.drawText(letter.toString(), 96f, 138f, p)
        return b
    }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(300); compose.waitForIdle()
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun awaitItems() = compose.waitUntil(5000) { vm.items.value.isNotEmpty() }

    @Test fun home() {
        compose.setContent { NativeAlphaTheme { HomeScreen(vm, {}, {}, {}, {}, {}, {}) } }
        awaitItems(); save("home")
    }

    @Test fun homeDark() {
        compose.setContent { NativeAlphaTheme(dark = true) { HomeScreen(vm, {}, {}, {}, {}, {}, {}) } }
        awaitItems(); save("home_dark")
    }

    @Test fun webAppSettings() {
        compose.setContent { NativeAlphaTheme { WebAppSettingsScreen(vm, 3) {} } }
        save("webapp_settings")
    }

    @Test fun usage() {
        compose.setContent { NativeAlphaTheme { UsageScreen(vm) {} } }
        save("usage_today")
    }
}
