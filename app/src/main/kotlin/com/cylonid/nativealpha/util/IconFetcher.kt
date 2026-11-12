package com.cylonid.nativealpha.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.util.TreeMap

/**
 * Finds a site's title, best icon and PWA start URL (web manifest → link icons → apple-touch
 * icons). Blocking; call from a background thread.
 */
object IconFetcher {
    private const val TAG = "IconFetcher"

    data class Result(val title: String?, val iconUrl: String?, val startUrl: String?, val icon: Bitmap?)

    @JvmStatic
    fun fetch(baseUrl: String): Result {
        if (baseUrl.isBlank()) return Result(null, null, null, null)
        var title: String? = null
        var startUrl: String? = null
        val icons = TreeMap<Int, String>()
        var url = baseUrl
        try {
            var doc = Jsoup.connect(url).ignoreHttpErrors(true).userAgent(Const.DESKTOP_USER_AGENT)
                .followRedirects(true).timeout(8000).get()

            doc.selectFirst("meta[http-equiv=refresh]")?.attr("content")?.let { content ->
                Regex(".*URL='?(.*)$", RegexOption.IGNORE_CASE).matchEntire(content)?.groupValues?.get(1)?.let { redirect ->
                    url = URL(URL(url), redirect.trim('\'', '"')).toString()
                    doc = Jsoup.connect(url).followRedirects(true).timeout(8000).get()
                }
            }

            val parsed = parse(doc) { manifestUrl ->
                Jsoup.connect(manifestUrl).ignoreContentType(true).timeout(8000).execute().body()
            }
            title = parsed.title
            startUrl = parsed.startUrl
            icons.putAll(parsed.icons)
        } catch (e: Exception) {
            Log.d(TAG, "Fetching $url failed", e)
        }
        if (icons.isEmpty()) {
            try { icons[1] = URL(URL(url), "/favicon.ico").toString() } catch (_: Exception) {}
        }
        // Try best icons first, falling back to smaller ones if decoding fails.
        var bitmap: Bitmap? = null
        var chosen: String? = null
        for (candidate in icons.descendingMap().values.take(4)) {
            bitmap = loadBitmap(candidate)
            if (bitmap != null) { chosen = candidate; break }
        }
        return Result(title, chosen, startUrl, bitmap)
    }

    data class Parsed(val title: String?, val startUrl: String?, val icons: TreeMap<Int, String>)

    /**
     * Extracts title, start URL and icon candidates (width → URL) from a page. The web manifest
     * wins; link icons are only used when it has none. [loadManifest] returns the manifest body.
     */
    @JvmStatic
    fun parse(doc: org.jsoup.nodes.Document, loadManifest: (String) -> String?): Parsed {
        var title: String? = null
        var startUrl: String? = null
        val icons = TreeMap<Int, String>()
        doc.selectFirst("link[rel=manifest]")?.let { mf ->
            try {
                val manifestUrl = mf.absUrl("href")
                val json = JSONObject(loadManifest(manifestUrl) ?: return@let)
                title = json.optString("short_name").ifBlank { json.optString("name") }.ifBlank { null }
                json.optString("start_url").takeIf { it.isNotBlank() }?.let { startUrl = URL(URL(manifestUrl), it).toString() }
                val arr = json.optJSONArray("icons")
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val src = o.optString("src")
                    if (src.isBlank() || o.optString("purpose").contains("monochrome")) continue
                    icons[ShortcutIconUtils.getWidthFromIcon(o.optString("sizes"))] = URL(URL(manifestUrl), src).toString()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Manifest parse failed", e)
            }
        }
        if (title == null) title = doc.selectFirst("title")?.text()?.trim()?.ifBlank { null }
        if (icons.isEmpty()) {
            val links = doc.select("link[rel~=(?i)^(shortcut )?icon$], link[rel=apple-touch-icon], link[rel=apple-touch-icon-precomposed]")
            for (l in links) {
                val href = l.absUrl("href")
                if (href.isBlank()) continue
                val sizes = l.attr("sizes")
                val w = if (sizes.isNotBlank() && sizes != "any") ShortcutIconUtils.getWidthFromIcon(sizes)
                else if (l.attr("rel").contains("apple")) 180 else 1
                icons[w] = href
            }
        }
        return Parsed(title, startUrl, icons)
    }

    @JvmStatic
    fun loadBitmap(url: String?): Bitmap? {
        if (url == null) return null
        return try {
            val con = URL(url).openConnection() as HttpURLConnection
            con.connectTimeout = 6000; con.readTimeout = 6000
            con.setRequestProperty("User-Agent", Const.DESKTOP_USER_AGENT)
            con.inputStream.use { BitmapFactory.decodeStream(it) }?.takeIf { it.width >= 16 }
        } catch (e: Exception) {
            null
        }
    }
}
