package com.cylonid.nativealpha.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.util.ShortcutHelper
import kotlinx.coroutines.launch

/** Two-step sheet: enter URL → review title/icon/limit → create (optionally pin to home screen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWebAppSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) } // 0 = url, 1 = loading, 2 = review
    var title by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf<Bitmap?>(null) }
    var pin by remember { mutableStateOf(true) }
    var limitText by remember { mutableStateOf("") }

    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) icon = decodeBitmap(context, uri)
    }

    fun next() {
        if (url.isBlank()) return
        step = 1
        scope.launch {
            val meta = vm.fetchMetadata(url.trim())
            title = meta.title ?: url.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
            icon = meta.icon
            // Prefer the PWA start URL when the site declares one on the same origin.
            val base = android.net.Uri.parse(if (url.trim().startsWith("http")) url.trim() else "https://" + url.trim()).host ?: ""
            meta.startUrl?.let { start ->
                val host = android.net.Uri.parse(start).host ?: ""
                if (start.startsWith("http") && com.cylonid.nativealpha.browser.HostMatcher.isSameSite(host, base, emptyList())) url = start
            }
            step = 2
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.add_webapp), style = MaterialTheme.typography.headlineSmall)
            when (step) {
                0 -> {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text(stringResource(R.string.url_hint)) },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { next() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { next() }, enabled = url.isNotBlank()) { Text(stringResource(R.string.next)) }
                    }
                }
                1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.fetching_site_info))
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clickable { pickIcon.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            val preview = remember(icon) { icon?.let { ShortcutHelper.renderAdaptiveBitmap(it).asImageBitmap() } }
                            AppIcon(preview, title, 64.dp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.shortcut_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Text(stringResource(R.string.tap_icon_to_change), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { v -> limitText = v.filter { it.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.daily_limit_minutes)) },
                        placeholder = { Text(stringResource(R.string.no_limit)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth().clickable { pin = !pin }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.create_shortcut_on_home_screen), Modifier.weight(1f))
                        Switch(checked = pin, onCheckedChange = { pin = it })
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.back)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val app = vm.addWebApp(url.trim(), title, icon, limitText.toIntOrNull() ?: 0)
                            if (pin) ShortcutHelper.requestPinShortcut(context as Activity, app, app.title, null)
                            onDismiss()
                        }) { Text(stringResource(R.string.create)) }
                    }
                }
            }
        }
    }
}

fun decodeBitmap(context: android.content.Context, uri: android.net.Uri): Bitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val max = 512
        val w = info.size.width; val h = info.size.height
        if (w > max || h > max) {
            val s = max.toFloat() / maxOf(w, h)
            d.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
        }
    }
} catch (e: Exception) { null }
