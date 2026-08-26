package com.niloy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.niloy.data.preferences.AppThemeSetting

private val DeveloperDarkColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    onPrimary = IndigoOnPrimary,
    primaryContainer = IndigoPrimaryContainer,
    onPrimaryContainer = IndigoOnPrimaryContainer,
    secondary = CyanSecondary,
    secondaryContainer = CyanSecondaryContainer,
    onSecondaryContainer = CyanOnSecondaryContainer,
    tertiary = EmeraldTertiary,
    tertiaryContainer = EmeraldTertiaryContainer,
    onTertiaryContainer = EmeraldOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFF7F1D1D),
    onError = Color.White,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val MinimalLightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF0891B2),
    secondaryContainer = Color(0xFFECFEFF),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFF059669),
    tertiaryContainer = Color(0xFFECFDF5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onError = Color.White,
    onErrorContainer = Color(0xFF991B1B)
)

private val MonokaiColorScheme = darkColorScheme(
    primary = Color(0xFFF92672),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF491124),
    onPrimaryContainer = Color(0xFFFFD1DC),
    secondary = Color(0xFF66D9EF),
    secondaryContainer = Color(0xFF1A3840),
    onSecondaryContainer = Color(0xFFB5F2FF),
    tertiary = Color(0xFFA6E22E),
    tertiaryContainer = Color(0xFF283A09),
    onTertiaryContainer = Color(0xFFE2FF9E),
    background = Color(0xFF272822),
    surface = Color(0xFF1E1F1C),
    surfaceVariant = Color(0xFF3E3D32),
    onBackground = Color(0xFFF8F8F2),
    onSurface = Color(0xFFF8F8F2),
    onSurfaceVariant = Color(0xFFCFD0C2)
)

private val SolarizedDarkColorScheme = darkColorScheme(
    primary = Color(0xFF268BD2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF073642),
    onPrimaryContainer = Color(0xFF93A1A1),
    secondary = Color(0xFF2AA198),
    secondaryContainer = Color(0xFF073642),
    onSecondaryContainer = Color(0xFF93A1A1),
    tertiary = Color(0xFF859900),
    tertiaryContainer = Color(0xFF073642),
    onTertiaryContainer = Color(0xFF93A1A1),
    background = Color(0xFF002B36),
    surface = Color(0xFF073642),
    surfaceVariant = Color(0xFF586E75).copy(alpha = 0.3f),
    onBackground = Color(0xFF839496),
    onSurface = Color(0xFF93A1A1),
    onSurfaceVariant = Color(0xFFEEE8D5)
)

@Composable
fun CCompilerTheme(
    themeSetting: AppThemeSetting = AppThemeSetting.DARK_DEVELOPER,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val colorScheme = when (themeSetting) {
        AppThemeSetting.SYSTEM -> if (isSystemDark) DeveloperDarkColorScheme else MinimalLightColorScheme
        AppThemeSetting.DARK_DEVELOPER -> DeveloperDarkColorScheme
        AppThemeSetting.LIGHT_MINIMAL -> MinimalLightColorScheme
        AppThemeSetting.MONOKAI -> MonokaiColorScheme
        AppThemeSetting.SOLARIZED_DARK -> SolarizedDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
