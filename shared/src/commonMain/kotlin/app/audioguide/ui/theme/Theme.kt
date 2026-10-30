package app.audioguide.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// Papier chaud et encre, avec un bordeaux d'affiche de musée.
private val LightColors = lightColorScheme(
    primary = Color(0xFF7B2D3B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DCDE),
    onPrimaryContainer = Color(0xFF3A0A15),
    secondary = Color(0xFF7A5F22),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4E8C8),
    onSecondaryContainer = Color(0xFF281E00),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEFE8DD),
    onSurfaceVariant = Color(0xFF4D463C),
    outline = Color(0xFF857B70),
    outlineVariant = Color(0xFFD8CFC2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEBB3BC),
    onPrimary = Color(0xFF481523),
    primaryContainer = Color(0xFF612233),
    onPrimaryContainer = Color(0xFFF6DCDE),
    secondary = Color(0xFFD9C27E),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5A4500),
    onSecondaryContainer = Color(0xFFF4E8C8),
    background = Color(0xFF16130F),
    onBackground = Color(0xFFEDE6DB),
    surface = Color(0xFF16130F),
    onSurface = Color(0xFFEDE6DB),
    surfaceVariant = Color(0xFF2B261F),
    onSurfaceVariant = Color(0xFFD0C5B7),
    outline = Color(0xFF9A8F82),
    outlineVariant = Color(0xFF4D463C),
)

private val base = Typography()

// Titres en empattement (Noto Serif sur Android, New York sur iOS) : le côté « cartel de musée ».
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun AudioguideTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
