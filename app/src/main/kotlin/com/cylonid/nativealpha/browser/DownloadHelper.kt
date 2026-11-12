package com.cylonid.nativealpha.browser

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.util.Utility
import org.mozilla.geckoview.WebResponse

/**
 * Saves a Gecko [WebResponse] to the public Downloads folder. The response body is streamed
 * directly (so cookies, POST results and blob: URLs work), rather than re-requested.
 */
object DownloadHelper {
    private const val TAG = "DownloadHelper"

    fun save(context: Context, response: WebResponse) {
        val appContext = context.applicationContext
        val headers = response.headers.mapKeys { it.key.lowercase() }
        val mime = headers["content-type"]?.substringBefore(';')?.trim()?.ifBlank { null } ?: "application/octet-stream"
        val disposition = headers["content-disposition"]
        val fileName = sanitize(Utility.getFileNameFromDownload(response.uri, disposition, mime)
            ?: Uri.parse(response.uri).lastPathSegment
            ?: ("download." + (MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin")))

        val body = response.body
        if (body == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            body?.close()
            legacyDownload(appContext, response.uri, mime, fileName)
            return
        }
        toast(appContext, appContext.getString(R.string.file_download))
        Thread {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = appContext.contentResolver
                val item = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                resolver.openOutputStream(item)!!.use { out -> body.use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(item, values, null, null)
                toast(appContext, appContext.getString(R.string.download_finished, fileName))
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                toast(appContext, appContext.getString(R.string.download_failed, fileName))
            }
        }.start()
    }

    private fun legacyDownload(context: Context, url: String, mime: String, fileName: String) {
        if (!url.startsWith("http")) {
            toast(context, context.getString(R.string.download_failed, fileName)); return
        }
        try {
            val req = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mime)
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            context.getSystemService(DownloadManager::class.java).enqueue(req)
            toast(context, context.getString(R.string.file_download))
        } catch (e: Exception) {
            Log.e(TAG, "DownloadManager failed", e)
            toast(context, context.getString(R.string.download_failed, fileName))
        }
    }

    private fun sanitize(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)

    private fun toast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
}
