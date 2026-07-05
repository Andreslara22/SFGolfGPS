package mx.clubsanfrancisco.golfgps

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta "Fairway" — verdes profundos, alto contraste para luz solar directa.
private val DarkGreenScheme = darkColorScheme(
    primary = Color(0xFF7ADFA8),          // verde menta brillante
    onPrimary = Color(0xFF06281A),
    primaryContainer = Color(0xFF14503A),
    onPrimaryContainer = Color(0xFFC9F5DC),
    secondary = Color(0xFFE0A96D),        // arena / bunker
    onSecondary = Color(0xFF2B1A0A),
    secondaryContainer = Color(0xFF4A3320),
    onSecondaryContainer = Color(0xFFF5DFC2),
    background = Color(0xFF061E14),
    onBackground = Color(0xFFE8F5EC),
    surface = Color(0xFF0C2B1D),
    onSurface = Color(0xFFE8F5EC),
    surfaceVariant = Color(0xFF16382A),
    onSurfaceVariant = Color(0xFFB7D4C3),
    outline = Color(0xFF4E7A64),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightGreenScheme = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0A2E0D),
    secondary = Color(0xFF8D6E3B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E2C8),
    background = Color(0xFFF6F8F4),
    onBackground = Color(0xFF15201A),
    surface = Color.White,
    onSurface = Color(0xFF15201A),
    surfaceVariant = Color(0xFFE1EBE2),
    onSurfaceVariant = Color(0xFF3E5347),
    outline = Color(0xFF6F8578),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun SFGolfTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkGreenScheme else LightGreenScheme,
        content = content
    )
}
