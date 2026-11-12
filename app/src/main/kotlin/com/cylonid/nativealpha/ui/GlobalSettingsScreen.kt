package com.cylonid.nativealpha.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.browser.GeckoRuntimeHolder
import com.cylonid.nativealpha.model.DataManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onDefaultWebAppSettings: () -> Unit,
    onAdblockDashboard: () -> Unit,
) {
    val context = LocalContext.current
    val dm = DataManager.getInstance()
    var s by remember { mutableStateOf(dm.settings.copy()) }
    fun save() { dm.settings = s }
    BackHandler { save(); onBack() }

    var ublockEnabled by remember { mutableStateOf(GeckoRuntimeHolder.ublock?.metaData?.enabled ?: true) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            save()
            val ok = dm.saveSharedPreferencesToFile(uri)
            Toast.makeText(context, if (ok) R.string.export_success else R.string.export_failure, Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = dm.loadSharedPreferencesFromFile(uri)
            if (ok) {
                dm.loadAppData()
                s = dm.settings.copy()
                vm.refresh()
            }
            Toast.makeText(context, if (ok) R.string.import_success_short else R.string.import_failure, Toast.LENGTH_LONG).show()
        }
    }

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.global_settings)) },
                navigationIcon = { IconButton(onClick = { save(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                scrollBehavior = scroll
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 48.dp)) {
            section(context.getString(R.string.section_webapps)) {
                ClickRow(stringResource(R.string.default_webapp_settings), stringResource(R.string.default_webapp_settings_hint)) { save(); onDefaultWebAppSettings() }
            }
            section(context.getString(R.string.adblock_settings)) {
                SwitchRow(stringResource(R.string.adblock_enabled), ublockEnabled, stringResource(R.string.adblock_enabled_hint)) { v ->
                    ublockEnabled = v
                    GeckoRuntimeHolder.setUblockEnabled(context, v)
                }
                ClickRow(stringResource(R.string.adblock_dashboard), stringResource(R.string.adblock_dashboard_hint)) { onAdblockDashboard() }
            }
            section(context.getString(R.string.section_gestures)) {
                SwitchRow(stringResource(R.string.two_finger_swipe), s.isTwoFingerMultitouch) { s = s.copy(isTwoFingerMultitouch = it) }
                SwitchRow(stringResource(R.string.three_finger_swipe), s.isThreeFingerMultitouch) { s = s.copy(isThreeFingerMultitouch = it) }
                SwitchRow(stringResource(R.string.two_finger_reload), s.isMultitouchReload) { s = s.copy(isMultitouchReload = it) }
            }
            section(context.getString(R.string.section_display)) {
                SwitchRow(stringResource(R.string.always_show_progress), s.isShowProgressbar) { s = s.copy(isShowProgressbar = it) }
                SwitchRow(stringResource(R.string.always_show_system_bars), s.alwaysShowSoftwareButtons) { s = s.copy(alwaysShowSoftwareButtons = it) }
            }
            section(context.getString(R.string.section_privacy)) {
                SwitchRow(stringResource(R.string.clear_cache_on_exit_all), s.isClearCache) { s = s.copy(isClearCache = it) }
            }
            section(context.getString(R.string.section_backup)) {
                ClickRow(stringResource(R.string.export_settings), stringResource(R.string.export_settings_hint)) {
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                    export.launch("NativeAlpha_Backup_$stamp")
                }
                ClickRow(stringResource(R.string.import_settings), stringResource(R.string.import_settings_hint)) {
                    import.launch(arrayOf("*/*"))
                }
            }
        }
    }
}
