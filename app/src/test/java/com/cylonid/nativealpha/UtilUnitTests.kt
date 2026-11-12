package com.cylonid.nativealpha

import com.cylonid.nativealpha.util.IconFetcher
import com.cylonid.nativealpha.util.ShortcutIconUtils
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Offline tests for icon/title discovery (the old versions depended on live websites). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UtilUnitTests {

    @Test fun getWidthFromHTMLElementString() {
        assertEquals(192, ShortcutIconUtils.getWidthFromIcon("192x192"))
        assertEquals(1, ShortcutIconUtils.getWidthFromIcon("any"))
    }

    @Test fun manifestIconsAndStartUrlWin() {
        val doc = Jsoup.parse(
            """<html><head><title>Ignored title</title>
               <link rel="manifest" href="/app.webmanifest">
               <link rel="icon" href="/small.png" sizes="16x16"></head></html>""",
            "https://example.com/start"
        )
        val manifest = """{"name":"Example Long","short_name":"Example","start_url":"/app/?pwa=1",
            "icons":[{"src":"icons/192.png","sizes":"192x192"},{"src":"icons/512.png","sizes":"512x512"},
                     {"src":"icons/mono.png","sizes":"1024x1024","purpose":"monochrome"}]}"""
        val p = IconFetcher.parse(doc) { url -> assertEquals("https://example.com/app.webmanifest", url); manifest }
        assertEquals("Example", p.title)
        assertEquals("https://example.com/app/?pwa=1", p.startUrl)
        assertEquals("https://example.com/icons/512.png", p.icons.lastEntry()!!.value)
        assertEquals(2, p.icons.size)
    }

    @Test fun fallsBackToLinkIcons() {
        val doc = Jsoup.parse(
            """<html><head><title> My Site </title>
               <link rel="shortcut icon" href="/favicon.ico">
               <link rel="icon" href="/icon-96.png" sizes="96x96">
               <link rel="apple-touch-icon" href="/apple.png"></head></html>""",
            "https://site.org/"
        )
        val p = IconFetcher.parse(doc) { null }
        assertEquals("My Site", p.title)
        assertNull(p.startUrl)
        assertEquals("https://site.org/apple.png", p.icons.lastEntry()!!.value)
    }

    @Test fun brokenManifestIsIgnored() {
        val doc = Jsoup.parse("""<link rel="manifest" href="m.json"><link rel="icon" href="a.png" sizes="64x64">""", "https://x.io/")
        val p = IconFetcher.parse(doc) { "not json" }
        assertEquals("https://x.io/a.png", p.icons.lastEntry()!!.value)
    }
}
