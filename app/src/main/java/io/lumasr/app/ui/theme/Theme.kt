package io.lumasr.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Colors = lightColorScheme(
    primary = Color(0xFFB4552F),
    onPrimary = Color(0xFFFFF8F0),
    primaryContainer = Color(0xFFF3E2D5),
    onPrimaryContainer = Color(0xFF5A2A18),
    secondary = Color(0xFF4F6B57),
    onSecondary = Color(0xFFF7FBF5),
    tertiary = Color(0xFF7A5C3E),
    background = Color(0xFFF5F0E6),
    onBackground = Color(0xFF24211C),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF24211C),
    surfaceVariant = Color(0xFFEDE4D6),
    onSurfaceVariant = Color(0xFF7C7569),
    outline = Color(0xFFD9CEBC),
    error = Color(0xFFB3261E),
)

private val LumaTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp),
)

@Composable
fun LumaSRTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = LumaTypography,
        content = content,
    )
}
