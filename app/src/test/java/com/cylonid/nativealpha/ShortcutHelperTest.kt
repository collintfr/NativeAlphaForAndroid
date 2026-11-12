package com.cylonid.nativealpha

import android.graphics.Bitmap
import android.graphics.Color
import com.cylonid.nativealpha.util.ShortcutHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortcutHelperTest {

    @Test
    fun shortcutIdIsDerivedFromWebAppIdNotTitle() {
        assertEquals("webapp_3", ShortcutHelper.shortcutId(3))
        assertNotEquals(ShortcutHelper.shortcutId(3), ShortcutHelper.shortcutId(4))
    }

    @Test
    fun adaptiveBitmapHasFixedSizeRegardlessOfSource() {
        for (size in listOf(16, 512, 2048)) {
            val src = Bitmap.createBitmap(size, size / 2, Bitmap.Config.ARGB_8888)
            val out = ShortcutHelper.renderAdaptiveBitmap(src)
            assertEquals(ShortcutHelper.ADAPTIVE_ICON_PX, out.width)
            assertEquals(ShortcutHelper.ADAPTIVE_ICON_PX, out.height)
        }
    }

    @Test
    fun transparentIconGetsWhiteBackground() {
        val src = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.TRANSPARENT)
        assertEquals(Color.WHITE, ShortcutHelper.sampleBackgroundColor(src))
    }

    @Test
    fun opaqueIconBackgroundMatchesBorderColour() {
        val src = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.rgb(10, 20, 200))
        assertEquals(Color.rgb(10, 20, 200), ShortcutHelper.sampleBackgroundColor(src))
    }
}
