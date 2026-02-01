package rs.smobile.speak2act.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandBlueSoft,
    onSecondary = Color.Black,
    background = BackgroundDarkTop,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    error = Error,
    onError = Color.White
)

val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandBlueSoft,
    onSecondary = Color.White,
    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLightElevated,
    onSurface = TextPrimaryLight,
    error = Error,
    onError = Color.White
)

@Composable
fun Speak2ActTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            val dynamicScheme =
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamicScheme.copy(
                primary = dynamicScheme.primary,
                secondary = dynamicScheme.secondary,
                tertiary = dynamicScheme.tertiary,
                error = dynamicScheme.error,
                // 🔒 LOCK THESE
                background = if (darkTheme) DarkColors.background else LightColors.background,
                surface = if (darkTheme) DarkColors.surface else LightColors.surface,
                surfaceVariant = if (darkTheme) DarkColors.surfaceVariant else LightColors.surfaceVariant
            )
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}