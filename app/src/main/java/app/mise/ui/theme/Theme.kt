package app.mise.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette (warm paprika) for devices without dynamic color. On a Pixel 8 Pro the
// wallpaper-derived dynamic scheme is used instead, so this rarely shows.
private val LightFallback = lightColorScheme(primary = Color(0xFFC2410C), secondary = Color(0xFF77574D), tertiary = Color(0xFF6B5E2F))
private val DarkFallback = darkColorScheme(primary = Color(0xFFFFB59A), secondary = Color(0xFFE7BDB0), tertiary = Color(0xFFD8C68D))

/** Material 3 Expressive: dynamic color + expressive motion. Typography/shapes use M3 defaults; override here later. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
