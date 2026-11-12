package com.cylonid.nativealpha.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cylonid.nativealpha.BuildConfig
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.model.WebApp
import com.cylonid.nativealpha.usage.UsageMath
import com.cylonid.nativealpha.util.WebViewLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onOpenSettings: (WebApp) -> Unit,
    onGlobalSettings: () -> Unit,
    onUsage: () -> Unit,
    onAdblock: () -> Unit,
    onAbout: () -> Unit,
) {
    val items by vm.items.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var overflow by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WebApp?>(null) }
    val appName = stringResource(if (BuildConfig.FLAVOR.contains("extended")) R.string.app_name_plus else R.string.app_name)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(appName) },
                actions = {
                    IconButton(onClick = onUsage) { Icon(painterResource(R.drawable.ic_bar_chart_24), stringResource(R.string.usage)) }
                    IconButton(onClick = onGlobalSettings) { Icon(Icons.Default.Settings, stringResource(R.string.action_settings)) }
                    Box {
                        IconButton(onClick = { overflow = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.adblock_settings)) }, onClick = { overflow = false; onAdblock() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_about)) }, onClick = { overflow = false; onAbout() })
                        }
                    }
                },
                scrollBehavior = scroll
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_webapp)) }
            )
        }
    ) { padding ->
        if (loaded && items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.empty_title), stringResource(R.string.empty_body))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                    start = 12.dp, end = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.app.ID }) { item ->
                    WebAppCard(
                        item = item,
                        isFirst = item == items.first(),
                        isLast = item == items.last(),
                        onOpen = { WebViewLauncher.startWebView(item.app, context) },
                        onSettings = { onOpenSettings(item.app) },
                        onShortcut = { vm.addToHomeScreen(activity, item.app) },
                        onMove = { vm.move(item.app, it) },
                        onDelete = { pendingDelete = item.app },
                    )
                }
            }
        }
    }

    pendingDelete?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(app.title) },
            text = { Text(stringResource(R.string.delete_question)) },
            confirmButton = { TextButton(onClick = { vm.delete(app, activity); pendingDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun WebAppCard(
    item: WebAppItem,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onShortcut: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val app = item.app
    var menu by remember { mutableStateOf(false) }
    val limit = app.dailyLimitMinutes
    val host = remember(app.baseUrl) { Uri.parse(app.baseUrl).host?.removePrefix("www.") ?: app.baseUrl }
    val used = UsageMath.formatDuration(item.usedTodayMs)
    val state = UsageMath.limitState(item.usedTodayMs, limit)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(item.icon, app.title, 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(app.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val usage = if (limit > 0) stringResource(R.string.usage_of_limit, used, UsageMath.formatDuration(limit * 60_000L))
                else if (item.usedTodayMs > 0) stringResource(R.string.usage_today, used) else null
                Text(
                    listOfNotNull(host, usage).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state == UsageMath.LimitState.REACHED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (limit > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (item.usedTodayMs / (limit * 60_000f)).coerceIn(0f, 1f) },
                        color = when (state) {
                            UsageMath.LimitState.REACHED -> MaterialTheme.colorScheme.error
                            UsageMath.LimitState.NEAR -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_to_home_screen)) }, onClick = { menu = false; onShortcut() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_settings)) }, onClick = { menu = false; onSettings() })
                    if (!isFirst) DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = false; onMove(-1) })
                    if (!isLast) DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
