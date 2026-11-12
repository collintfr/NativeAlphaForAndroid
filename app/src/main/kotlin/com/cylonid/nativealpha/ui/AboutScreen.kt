package com.cylonid.nativealpha.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cylonid.nativealpha.BuildConfig
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.browser.GeckoRuntimeHolder
import com.mikepenz.aboutlibraries.LibsBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.action_about)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 48.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text("${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about_engine, org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION, GeckoRuntimeHolder.ublock?.metaData?.version ?: "–"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            section(context.getString(R.string.section_links)) {
                ClickRow(stringResource(R.string.source_code), "github.com/cylonid/NativeAlphaForAndroid") { open("https://github.com/cylonid/NativeAlphaForAndroid") }
                ClickRow(stringResource(R.string.license), "GNU GPL v3") { open("https://www.gnu.org/licenses/gpl-3.0.html") }
                ClickRow(stringResource(R.string.open_source_licenses)) {
                    LibsBuilder().withActivityTitle(context.getString(R.string.open_source_licenses)).start(context)
                }
            }
        }
    }
}
