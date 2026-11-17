package io.lumasr.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF7AA2FF),
    onPrimary = Color(0xFF08101F),
    primaryContainer = Color(0xFF24365C),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFF9BE7C4),
    onSecondary = Color(0xFF042016),
    tertiary = Color(0xFFFFC98B),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF141922),
    onSurface = Color(0xFFE8ECF4),
    surfaceVariant = Color(0xFF1E2530),
    onSurfaceVariant = Color(0xFFB6C0D0),
    outline = Color(0xFF39424F),
    error = Color(0xFFFF6B6B),
)

@Composable
fun LumaSRTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content,
    )
}
