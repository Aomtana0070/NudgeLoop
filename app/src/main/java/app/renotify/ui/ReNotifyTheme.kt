package app.renotify.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

@Composable
fun ReNotifyTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
            primary = Color(0xFFA88CFF),
            onPrimary = Color(0xFF21143D),
            primaryContainer = Color(0xFF382A5B),
            onPrimaryContainer = Color(0xFFE9DDFF),
            secondary = Color(0xFFD9FF72),
            onSecondary = Color(0xFF202900),
            secondaryContainer = Color(0xFF2D3815),
            onSecondaryContainer = Color(0xFFE6FFAA),
            tertiary = Color(0xFFE9B29D),
            background = Color(0xFF111014),
            onBackground = Color(0xFFE9E1EC),
            surface = Color(0xFF111014),
            onSurface = Color(0xFFE9E1EC),
            surfaceVariant = Color(0xFF29272F),
            onSurfaceVariant = Color(0xFFC9C0CE),
            outline = Color(0xFF77717D),
    )
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
