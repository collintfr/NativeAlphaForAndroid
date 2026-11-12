package com.cylonid.nativealpha.browser

import android.content.Context
import android.text.Html
import android.util.Base64
import com.cylonid.nativealpha.R
import org.mozilla.geckoview.WebRequestError

/** Builds a small self-contained error page as a data: URI. */
object ErrorPages {
    fun build(context: Context, uri: String?, error: WebRequestError): String {
        val (title, detail) = when (error.category) {
            WebRequestError.ERROR_CATEGORY_SECURITY -> R.string.error_title_security to R.string.error_detail_security
            WebRequestError.ERROR_CATEGORY_NETWORK -> when (error.code) {
                WebRequestError.ERROR_UNKNOWN_HOST, WebRequestError.ERROR_OFFLINE -> R.string.error_title_offline to R.string.error_detail_offline
                else -> R.string.error_title_network to R.string.error_detail_network
            }
            WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> R.string.error_title_safebrowsing to R.string.error_detail_safebrowsing
            else -> R.string.error_title_generic to R.string.error_detail_network
        }
        val esc = { s: String -> Html.escapeHtml(s) }
        val html = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              :root{color-scheme:light dark;font-family:system-ui,sans-serif}
              body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}
              main{max-width:420px}
              h1{font-size:1.4rem;margin:0 0 .5rem}
              p{opacity:.8;line-height:1.45}
              code{font-size:.8rem;word-break:break-all;opacity:.6}
              button{margin-top:1rem;padding:.7rem 1.4rem;border-radius:999px;border:0;font-size:1rem;background:#4f5ad6;color:#fff}
            </style></head><body><main>
            <h1>${esc(context.getString(title))}</h1>
            <p>${esc(context.getString(detail))}</p>
            <code>${esc(uri ?: "")}</code><br>
            <button onclick="location.reload()">${esc(context.getString(R.string.retry))}</button>
            </main></body></html>
        """.trimIndent()
        return "data:text/html;charset=utf-8;base64," + Base64.encodeToString(html.toByteArray(), Base64.NO_WRAP)
    }
}
