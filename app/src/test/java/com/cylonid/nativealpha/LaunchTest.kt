package com.cylonid.nativealpha

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.annotation.Config

/** Smoke test: the launcher activity starts and renders without crashing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchTest {
    @Test fun mainActivityStarts() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }
}
