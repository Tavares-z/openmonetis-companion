package br.com.openmonetis.companion.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand palette converted from the project's OKLCH tokens.
val Primary = Color(0xFFFF7733)
val PrimaryVariant = Color(0xFFF2EDE8)
val Secondary = Color(0xFFF5F2EF)
val SecondaryVariant = Color(0xFFE6DDD6)

val Success = Color(0xFF0E9D6E)
val Warning = Color(0xFFF7A439)
val Error = Color(0xFFD40C1A)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF0F0D0C),
    primaryContainer = Color(0xFFF2EDE8),
    onPrimaryContainer = Color(0xFF2A2523),
    secondary = Secondary,
    onSecondary = Color(0xFF322C2A),
    secondaryContainer = Color(0xFFF0EEEC),
    onSecondaryContainer = Color(0xFF2A2523),
    tertiary = Warning,
    onTertiary = Color(0xFF1E1400),
    error = Error,
    onError = Color(0xFFFCF7F6),
    errorContainer = Color(0xFFF2EDE8),
    onErrorContainer = Error,
    background = Color(0xFFF8F6F4),
    onBackground = Color(0xFF2A2523),
    surface = Color(0xFFFDFBFA),
    onSurface = Color(0xFF2A2523),
    surfaceVariant = Color(0xFFF0EEEC),
    onSurfaceVariant = Color(0xFF676260),
    outline = Color(0xFFE6DDD6),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF7733),
    onPrimary = Color(0xFF0F0D0C),
    primaryContainer = Color(0xFF373533),
    onPrimaryContainer = Color(0xFFEBE7E2),
    secondary = Color(0xFF2D2B29),
    onSecondary = Color(0xFFEBE7E2),
    secondaryContainer = Color(0xFF343231),
    onSecondaryContainer = Color(0xFFEBE7E2),
    tertiary = Color(0xFFF37515),
    onTertiary = Color(0xFF130900),
    error = Color(0xFFE6443A),
    onError = Color(0xFFFCF7F6),
    errorContainer = Color(0xFF373533),
    onErrorContainer = Color(0xFFFCF7F6),
    background = Color(0xFF1C1A19),
    onBackground = Color(0xFFEBE7E2),
    surface = Color(0xFF242221),
    onSurface = Color(0xFFEBE7E2),
    surfaceVariant = Color(0xFF343231),
    onSurfaceVariant = Color(0xFFAAA7A4),
    outline = Color(0xFF3C3A39),
)

@Composable
fun OpenMonetisCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
