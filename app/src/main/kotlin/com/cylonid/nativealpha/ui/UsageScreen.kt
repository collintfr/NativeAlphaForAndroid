package com.cylonid.nativealpha.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cylonid.nativealpha.R
import com.cylonid.nativealpha.usage.UsageMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(vm: AppViewModel, onBack: () -> Unit) {
    var days by remember { mutableIntStateOf(1) }
    var summary by remember { mutableStateOf<UsageSummary?>(null) }
    LaunchedEffect(days) { vm.usageSummary(days) { summary = it } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.usage)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 48.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SegmentedRow(
                    listOf(1 to stringResource(R.string.today), 7 to stringResource(R.string.last_7_days), 30 to stringResource(R.string.last_30_days)),
                    days, Modifier.padding(horizontal = 0.dp)
                ) { days = it }
            }
            val s = summary
            if (s != null) {
                item {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(UsageMath.formatDuration(s.totalMs), style = MaterialTheme.typography.displaySmall)
                        Text(
                            stringResource(if (days == 1) R.string.total_today else R.string.total_period),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (s.daily.isNotEmpty()) item { DailyChart(s.daily) }
                if (s.apps.isEmpty()) item { EmptyState(stringResource(R.string.no_usage_title), stringResource(R.string.no_usage_body)) }
                val max = s.apps.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
                items(s.apps, key = { it.app.ID }) { u ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(u.icon, u.app.title, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(u.app.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(UsageMath.formatDuration(u.totalMs), style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { u.totalMs.toFloat() / max }, gapSize = 0.dp, drawStopIndicator = {}, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth())
                            if (days == 1 && u.app.dailyLimitMinutes > 0 && u.app.isActiveEntry) {
                                Text(
                                    stringResource(R.string.limit_label, UsageMath.formatDuration(u.app.dailyLimitMinutes * 60_000L)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyChart(daily: List<Pair<Long, Long>>) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = daily.maxOf { it.second }.coerceAtLeast(60_000L)
    val fmt = remember(daily.size) { SimpleDateFormat(if (daily.size <= 7) "EEE" else "d", Locale.getDefault()) }
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = daily.size
            val gap = if (n <= 7) 12.dp.toPx() else 3.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            daily.forEachIndexed { i, (_, total) ->
                val x = i * (w + gap)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 4))
                val h = size.height * (total.toFloat() / max)
                if (h > 0f) drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 4))
            }
        }
        if (daily.size <= 7) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                daily.forEach { (day, _) ->
                    Text(fmt.format(Date(day)), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
