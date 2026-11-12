package com.cylonid.nativealpha.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Web app icon: stored adaptive icon cropped to a squircle, or a coloured letter tile. */
@Composable
fun AppIcon(icon: ImageBitmap?, title: String, size: Dp = 48.dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    if (icon != null) {
        // Stored icons are full adaptive canvases (108dp); scale up so the safe zone fills the tile.
        Box(Modifier.size(size).clip(shape)) {
            Image(
                bitmap = icon, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(size * 1.33f).align(Alignment.Center)
            )
        }
    } else {
        val palette = listOf(0xFF5B6DD6, 0xFF2E8B7A, 0xFFB65C3A, 0xFF8A4FB3, 0xFF3A7DB6, 0xFFB03A6B)
        val bg = Color(palette[abs(title.hashCode()) % palette.size])
        Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
            Text(
                title.trim().take(1).uppercase().ifEmpty { "?" },
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

fun LazyListScope.section(title: String, content: @Composable () -> Unit) {
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
        )
    }
    item(key = "card-$title") {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) { Column { content() } }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) }
    )
}

@Composable
fun ClickRow(title: String, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = leading,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun <T> SegmentedRow(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label, maxLines = 1) }
            )
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

val ScreenPadding = PaddingValues(bottom = 32.dp)

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RowSpacer() = Row(Modifier.padding(4.dp)) {}
