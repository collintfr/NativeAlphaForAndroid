package com.cylonid.nativealpha.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cylonid.nativealpha.BuildConfig
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.browser.HostMatcher
import com.cylonid.nativealpha.model.WebApp

/**
 * Settings for one web app, or for the template new web apps inherit (id = GLOBAL_ID).
 * Changes are saved when leaving the screen; running instances of the web app are closed so
 * they pick up the new settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAppSettingsScreen(vm: AppViewModel, webAppId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val initial = remember(webAppId) { vm.editableCopy(webAppId) }
    if (initial == null) { onBack(); return }
    // Copy-on-write: every change produces a new WebApp so Compose sees a new value.
    // WebApp is a data class whose equals() only compares baseUrl and ID, so the default
    // structural policy would drop every edit that doesn't change the URL.
    var draft by remember(webAppId) { mutableStateOf(initial, referentialEqualityPolicy()) }
    fun update(block: WebApp.() -> Unit) { draft = WebApp(draft).apply(block) }
    val isGlobal = webAppId == AppViewModel.GLOBAL_ID
    val plus = BuildConfig.FLAVOR.contains("extended")

    fun saveAndExit() {
        val final = draft
        if (final.title.isBlank()) final.title = final.baseUrl
        vm.save(final)
        if (!isGlobal) AppViewModel.closeRunningTasks(activity, draft.ID)
        vm.refresh()
        onBack()
    }
    BackHandler { saveAndExit() }

    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) decodeBitmap(context, uri)?.let { vm.setIcon(draft, it) }
    }
    var addHostDialog by remember { mutableStateOf(false) }

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(if (isGlobal) stringResource(R.string.default_webapp_settings) else draft.title) },
                navigationIcon = { IconButton(onClick = { saveAndExit() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                scrollBehavior = scroll
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(top = padding.calculateTopPadding(), bottom = 48.dp)) {

            if (!isGlobal) {
                section(context.getString(R.string.section_general)) {
                    OutlinedTextField(
                        value = draft.title, onValueChange = { v -> update { title = v } },
                        label = { Text(stringResource(R.string.shortcut_title)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp)
                    )
                    OutlinedTextField(
                        value = draft.baseUrl, onValueChange = { v -> update { baseUrl = v.trim() } },
                        label = { Text(stringResource(R.string.url_hint)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 8.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.addToHomeScreen(activity, draft) }) { Text(stringResource(R.string.add_to_home_screen)) }
                        OutlinedButton(onClick = { pickIcon.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.change_icon)) }
                    }
                }

                // ---- Usage controls (always per app) ----
                section(context.getString(R.string.section_time_limit)) {
                    var limitText by remember(webAppId) { mutableStateOf(if (draft.dailyLimitMinutes > 0) draft.dailyLimitMinutes.toString() else "") }
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { v ->
                            limitText = v.filter { it.isDigit() }.take(4)
                            update { dailyLimitMinutes = limitText.toIntOrNull() ?: 0 }
                        },
                        label = { Text(stringResource(R.string.daily_limit_minutes)) },
                        placeholder = { Text(stringResource(R.string.no_limit)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp)
                    )
                    if (draft.dailyLimitMinutes > 0) {
                        SegmentedRow(
                            listOf(WebApp.LIMIT_MODE_WARN to stringResource(R.string.limit_mode_warn), WebApp.LIMIT_MODE_BLOCK to stringResource(R.string.limit_mode_block)),
                            draft.limitMode
                        ) { update { limitMode = it } }
                        Hint(stringResource(if (draft.limitMode == WebApp.LIMIT_MODE_BLOCK) R.string.limit_mode_block_hint else R.string.limit_mode_warn_hint))
                    }
                }

                section(context.getString(R.string.section_nav_lock)) {
                    val mode = draft.effectiveNavLockMode
                    SegmentedRow(
                        listOf(
                            WebApp.NAV_LOCK_OFF to stringResource(R.string.nav_lock_off),
                            WebApp.NAV_LOCK_PROMPT to stringResource(R.string.nav_lock_prompt),
                            WebApp.NAV_LOCK_BLOCK to stringResource(R.string.nav_lock_block),
                            WebApp.NAV_LOCK_OPEN_EXTERNAL to stringResource(R.string.nav_lock_external),
                        ),
                        mode
                    ) { m -> update { navLockMode = m; isOpenUrlExternal = m == WebApp.NAV_LOCK_OPEN_EXTERNAL; isOverrideGlobalSettings = isOverrideGlobalSettings } }
                    Hint(stringResource(when (mode) {
                        WebApp.NAV_LOCK_PROMPT -> R.string.nav_lock_prompt_hint
                        WebApp.NAV_LOCK_BLOCK -> R.string.nav_lock_block_hint
                        WebApp.NAV_LOCK_OPEN_EXTERNAL -> R.string.nav_lock_external_hint
                        else -> R.string.nav_lock_off_hint
                    }))
                    if (mode == WebApp.NAV_LOCK_PROMPT || mode == WebApp.NAV_LOCK_BLOCK) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            draft.allowedHostList().forEach { host ->
                                InputChip(
                                    selected = false,
                                    onClick = { update { allowedHosts = allowedHostList().filter { it != host }.toMutableList() } },
                                    label = { Text(host) },
                                    trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.delete), Modifier.size(18.dp)) }
                                )
                            }
                        }
                        TextButton(onClick = { addHostDialog = true }, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                            Text(stringResource(R.string.add_allowed_domain))
                        }
                    }
                }

                section(context.getString(R.string.section_inheritance)) {
                    SwitchRow(stringResource(R.string.use_default_settings), !draft.isOverrideGlobalSettings, stringResource(R.string.use_default_settings_hint)) {
                        update { isOverrideGlobalSettings = !it }
                    }
                }
            }

            val showDetails = isGlobal || draft.isOverrideGlobalSettings
            if (showDetails) {
                section(context.getString(R.string.section_privacy)) {
                    SwitchRow(stringResource(R.string.enable_javascript), draft.isAllowJs) { update { isAllowJs = it } }
                    if (plus && !isGlobal) {
                        SwitchRow(stringResource(R.string.isolated_storage), draft.isUseContainer, stringResource(R.string.isolated_storage_hint)) {
                            update { isUseContainer = it; containerId = if (it) ID else com.cylonid.nativealpha.util.Const.NO_CONTAINER }
                        }
                    }
                    SwitchRow(stringResource(R.string.allow_http), draft.isAllowHttp) { update { isAllowHttp = it } }
                    SwitchRow(stringResource(R.string.clear_cache_on_exit), draft.isClearCache) { update { isClearCache = it } }
                    SwitchRow(stringResource(R.string.private_mode), draft.isPrivateMode, stringResource(R.string.private_mode_hint)) { update { isPrivateMode = it } }
                    if (!draft.isPrivateMode) {
                        SwitchRow(stringResource(R.string.restore_page), draft.isRestorePage, stringResource(R.string.restore_page_hint)) { update { isRestorePage = it } }
                    }
                }
                section(context.getString(R.string.section_permissions)) {
                    SwitchRow(stringResource(R.string.location_access), draft.isAllowLocationAccess) { update { isAllowLocationAccess = it } }
                    SwitchRow(stringResource(R.string.camera_access), draft.isCameraPermission) { update { isCameraPermission = it } }
                    SwitchRow(stringResource(R.string.microphone_access), draft.isMicrophonePermission) { update { isMicrophonePermission = it } }
                    SwitchRow(stringResource(R.string.drm_content), draft.isDrmAllowed) { update { isDrmAllowed = it } }
                    if (plus) SwitchRow(stringResource(R.string.biometric_access), draft.isBiometricProtection, stringResource(R.string.biometric_access_hint)) { update { isBiometricProtection = it } }
                }
                section(context.getString(R.string.section_display)) {
                    SwitchRow(stringResource(R.string.request_desktop_site), draft.isRequestDesktop) { update { isRequestDesktop = it } }
                    if (plus) {
                        SwitchRow(stringResource(R.string.fullscreen), draft.isShowFullscreen) { update { isShowFullscreen = it } }
                        SwitchRow(stringResource(R.string.keep_awake), draft.isKeepAwake) { update { isKeepAwake = it } }
                    }
                    SwitchRow(stringResource(R.string.background_media), draft.isAllowMediaPlaybackInBackground) { update { isAllowMediaPlaybackInBackground = it } }
                    SwitchRow(stringResource(R.string.disable_long_press_menu), draft.alwaysUseFallbackContextMenu) { update { alwaysUseFallbackContextMenu = it } }
                }
                section(context.getString(R.string.section_advanced)) {
                    SwitchRow(stringResource(R.string.autoreload), draft.isAutoreload) { update { isAutoreload = it } }
                    if (draft.isAutoreload) {
                        var reloadText by remember(webAppId) { mutableStateOf(draft.timeAutoreload.takeIf { it > 0 }?.toString() ?: "") }
                        OutlinedTextField(
                            value = reloadText,
                            onValueChange = { v -> reloadText = v.filter { it.isDigit() }.take(6); update { timeAutoreload = reloadText.toIntOrNull() ?: 0 } },
                            label = { Text(stringResource(R.string.reload_interval_seconds)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp)
                        )
                    }
                    SwitchRow(stringResource(R.string.save_data_header), draft.isSendSavedataRequest) { update { isSendSavedataRequest = it } }
                    SwitchRow(stringResource(R.string.custom_user_agent), draft.isUseCustomUserAgent) { update { isUseCustomUserAgent = it } }
                    if (draft.isUseCustomUserAgent) {
                        OutlinedTextField(
                            value = draft.userAgent ?: "", onValueChange = { v -> update { userAgent = v } },
                            label = { Text(stringResource(R.string.user_agent)) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (addHostDialog) {
        var host by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addHostDialog = false },
            title = { Text(stringResource(R.string.add_allowed_domain)) },
            text = {
                OutlinedTextField(
                    value = host, onValueChange = { host = it }, singleLine = true,
                    placeholder = { Text("accounts.google.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = HostMatcher.normalize(host.removePrefix("https://").removePrefix("http://").substringBefore('/'))
                    if (h.isNotBlank()) update { allowedHosts = (allowedHostList() + h).distinct().toMutableList() }
                    addHostDialog = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { addHostDialog = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}
