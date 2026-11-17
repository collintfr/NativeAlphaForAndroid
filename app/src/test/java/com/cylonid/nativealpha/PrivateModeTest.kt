package com.cylonid.nativealpha

import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.model.deserializer.WebAppDeserializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivateModeTest {
    private val gson: Gson = GsonBuilder().registerTypeAdapter(WebApp::class.java, WebAppDeserializer()).create()

    @Test fun copyCarriesPrivateMode() {
        val a = WebApp("https://reddit.com", 1).apply { isPrivateMode = true }
        assertTrue(WebApp(a).isPrivateMode)
        assertTrue(WebApp("https://x.org", 2).apply { copySettings(a) }.isPrivateMode)
    }

    @Test fun roundTripsAndDefaultsToOff() {
        val a = WebApp("https://reddit.com", 1).apply { isPrivateMode = true }
        assertTrue(gson.fromJson(gson.toJson(a), WebApp::class.java).isPrivateMode)
        val legacy = """{"baseUrl":"https://reddit.com","ID":1,"title":"reddit"}"""
        assertFalse(gson.fromJson(legacy, WebApp::class.java).isPrivateMode)
    }
}
