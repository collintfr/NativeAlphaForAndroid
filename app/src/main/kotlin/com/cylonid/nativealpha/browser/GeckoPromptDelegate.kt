package com.cylonid.nativealpha.browser

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.cylonid.nativealpha.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import java.util.Calendar
import java.util.Locale

/**
 * Native dialogs for page prompts. GeckoView ships no UI of its own, so without this, `alert()`,
 * `<select>` dropdowns, file inputs, HTTP auth etc. would silently do nothing.
 */
class GeckoPromptDelegate(
    private val activity: AppCompatActivity,
    /** Launches a document picker; the callback receives the chosen URIs or null if cancelled. */
    private val pickFiles: (mimeTypes: Array<String>, multiple: Boolean, callback: (Array<Uri>?) -> Unit) -> Unit,
    /** Decides whether a page may open a popup window (e.g. OAuth flows). */
    private val allowPopup: (String?) -> Boolean = { true },
) : PromptDelegate {

    private fun dialog() = MaterialAlertDialogBuilder(activity)

    private fun alive() = !activity.isFinishing && !activity.isDestroyed

    private inline fun <T : PromptDelegate.BasePrompt> show(
        prompt: T,
        crossinline build: (MaterialAlertDialogBuilder, GeckoResult<PromptResponse>) -> Unit
    ): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        if (!alive()) return GeckoResult.fromValue(prompt.dismiss())
        val b = dialog()
        if (!prompt.title.isNullOrBlank()) b.setTitle(prompt.title)
        b.setOnCancelListener { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
        build(b, result)
        b.show()
        return result
    }

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) =
        show(prompt) { b, r ->
            b.setMessage(prompt.message)
            b.setPositiveButton(android.R.string.ok) { _, _ -> r.complete(prompt.dismiss()) }
        }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) =
        show(prompt) { b, r ->
            b.setMessage(prompt.message)
            b.setPositiveButton(android.R.string.ok) { _, _ -> r.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE)) }
            b.setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)) }
        }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) =
        show(prompt) { b, r ->
            val input = EditText(activity).apply { setText(prompt.defaultValue ?: ""); setSingleLine() }
            b.setMessage(prompt.message)
            b.setView(padded(input))
            b.setPositiveButton(android.R.string.ok) { _, _ -> r.complete(prompt.confirm(input.text.toString())) }
            b.setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.dismiss()) }
        }

    override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt) =
        show(prompt) { b, r ->
            val onlyPassword = (prompt.authOptions.flags and PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
            val user = EditText(activity).apply {
                hint = activity.getString(R.string.username); setText(prompt.authOptions.username ?: ""); setSingleLine()
            }
            val pass = EditText(activity).apply {
                hint = activity.getString(R.string.password); setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                if (!onlyPassword) addView(user)
                addView(pass)
            }
            b.setTitle(prompt.title ?: activity.getString(R.string.http_auth_title))
            b.setMessage(prompt.message)
            b.setView(padded(box))
            b.setPositiveButton(android.R.string.ok) { _, _ ->
                r.complete(
                    if (onlyPassword) prompt.confirm(pass.text.toString())
                    else prompt.confirm(user.text.toString(), pass.text.toString())
                )
            }
            b.setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.dismiss()) }
        }

    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse> {
        // Flatten option groups; separators and group headers become disabled rows.
        val flat = mutableListOf<PromptDelegate.ChoicePrompt.Choice>()
        fun walk(items: Array<PromptDelegate.ChoicePrompt.Choice>?) {
            items?.forEach { c -> if (c.separator) return@forEach; flat += c; if (c.items != null) walk(c.items) }
        }
        walk(prompt.choices)
        val selectable = flat.filter { it.items == null && !it.disabled }
        val labels = selectable.map { it.label }.toTypedArray()

        return show(prompt) { b, r ->
            if (!prompt.message.isNullOrBlank()) b.setTitle(prompt.message)
            when (prompt.type) {
                PromptDelegate.ChoicePrompt.Type.MULTIPLE -> {
                    val checked = selectable.map { it.selected }.toBooleanArray()
                    b.setMultiChoiceItems(labels, checked) { _, i, v -> checked[i] = v }
                    b.setPositiveButton(android.R.string.ok) { _, _ ->
                        r.complete(prompt.confirm(selectable.filterIndexed { i, _ -> checked[i] }.toTypedArray()))
                    }
                    b.setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.dismiss()) }
                }
                else -> {
                    val current = selectable.indexOfFirst { it.selected }
                    b.setSingleChoiceItems(labels, current) { d, i ->
                        r.complete(prompt.confirm(selectable[i])); d.dismiss()
                    }
                }
            }
        }
    }

    override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        if (!alive()) return GeckoResult.fromValue(prompt.dismiss())
        val cal = Calendar.getInstance()
        val type = prompt.type
        fun pickTime(onTime: (Int, Int) -> Unit) {
            TimePickerDialog(activity, { _, h, m -> onTime(h, m) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).apply {
                setOnCancelListener { result.complete(prompt.dismiss()) }
            }.show()
        }
        if (type == PromptDelegate.DateTimePrompt.Type.TIME) {
            pickTime { h, m -> result.complete(prompt.confirm(String.format(Locale.ROOT, "%02d:%02d", h, m))) }
            return result
        }
        DatePickerDialog(activity, { _, y, mo, d ->
            val date = String.format(Locale.ROOT, "%04d-%02d-%02d", y, mo + 1, d)
            when (type) {
                PromptDelegate.DateTimePrompt.Type.MONTH ->
                    result.complete(prompt.confirm(String.format(Locale.ROOT, "%04d-%02d", y, mo + 1)))
                PromptDelegate.DateTimePrompt.Type.WEEK -> {
                    val c = Calendar.getInstance().apply { set(y, mo, d) }
                    result.complete(prompt.confirm(String.format(Locale.ROOT, "%04d-W%02d", c.getWeekYear(), c.get(Calendar.WEEK_OF_YEAR))))
                }
                PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL ->
                    pickTime { h, m -> result.complete(prompt.confirm(String.format(Locale.ROOT, "%sT%02d:%02d", date, h, m))) }
                else -> result.complete(prompt.confirm(date))
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            setOnCancelListener { result.complete(prompt.dismiss()) }
        }.show()
        return result
    }

    override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt) =
        show(prompt) { b, r ->
            val input = EditText(activity).apply { setText(prompt.defaultValue ?: "#000000"); setSingleLine() }
            b.setView(padded(input))
            b.setPositiveButton(android.R.string.ok) { _, _ ->
                val v = input.text.toString().trim()
                r.complete(try { Color.parseColor(v); prompt.confirm(v) } catch (e: IllegalArgumentException) { prompt.dismiss() })
            }
            b.setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.dismiss()) }
        }

    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val mimes = prompt.mimeTypes?.filter { it.contains('/') }?.toTypedArray()?.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
        pickFiles(mimes, prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE) { uris ->
            result.complete(
                when {
                    uris.isNullOrEmpty() -> prompt.dismiss()
                    prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE -> prompt.confirm(activity, uris)
                    else -> prompt.confirm(activity, uris[0])
                }
            )
        }
        return result
    }

    override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt): GeckoResult<PromptResponse> =
        GeckoResult.fromValue(prompt.confirm(if (allowPopup(prompt.targetUri)) AllowOrDeny.ALLOW else AllowOrDeny.DENY))

    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt) =
        show(prompt) { b, r ->
            b.setMessage(R.string.leave_page_question)
            b.setPositiveButton(R.string.leave) { _, _ -> r.complete(prompt.confirm(AllowOrDeny.ALLOW)) }
            b.setNegativeButton(R.string.stay) { _, _ -> r.complete(prompt.confirm(AllowOrDeny.DENY)) }
        }

    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt) =
        show(prompt) { b, r ->
            b.setMessage(R.string.repost_question)
            b.setPositiveButton(R.string.resend) { _, _ -> r.complete(prompt.confirm(AllowOrDeny.ALLOW)) }
            b.setNegativeButton(android.R.string.cancel) { _, _ -> r.complete(prompt.confirm(AllowOrDeny.DENY)) }
        }

    override fun onSharePrompt(session: GeckoSession, prompt: PromptDelegate.SharePrompt): GeckoResult<PromptResponse> {
        val text = listOfNotNull(prompt.text, prompt.uri).joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (!prompt.title.isNullOrBlank()) send.putExtra(Intent.EXTRA_SUBJECT, prompt.title)
        return try {
            activity.startActivity(Intent.createChooser(send, null))
            GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.SUCCESS))
        } catch (e: Exception) {
            GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.FAILURE))
        }
    }

    private fun padded(v: android.view.View): android.view.View {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        return LinearLayout(activity).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}
