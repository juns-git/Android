package io.github.juns_git.android.familystockgate.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppTheme { MODERN, DARK, BEAR_BLUE, BUNNY_PINK }

val LocalAppTheme = compositionLocalOf { AppTheme.MODERN }

// ── Modern: Deep Navy & Slate Gray ───────────────────────────────────────────
private val ModernColorScheme = lightColorScheme(
    primary              = NavyPrimary,
    onPrimary            = Color.White,
    primaryContainer     = NavyContainer,
    onPrimaryContainer   = Color(0xFFE2E8F0),
    secondary            = NavySecondary,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFCBD5E1),
    onSecondaryContainer = NavyPrimary,
    tertiary             = Color(0xFF0F766E),
    onTertiary           = Color.White,
    background           = SlateBackground,
    onBackground         = NavyPrimary,
    surface              = Color.White,
    onSurface            = NavyPrimary,
    surfaceVariant       = Color(0xFFE2E8F0),
    onSurfaceVariant     = Color(0xFF475569),
    outline              = Color(0xFFCBD5E1),
    error                = Color(0xFFDC2626),
    onError              = Color.White
)

private val ModernShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

// ── Bear Blue: Pastel Sky Blue & Warm Cream ──────────────────────────────────
private val BearBlueColorScheme = lightColorScheme(
    primary              = BearPrimary,
    onPrimary            = Color.White,
    primaryContainer     = BearContainer,
    onPrimaryContainer   = Color(0xFF1E40AF),
    secondary            = Color(0xFFFBBF24),
    onSecondary          = Color(0xFF78350F),
    secondaryContainer   = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary             = Color(0xFF34D399),
    onTertiary           = Color.White,
    background           = BearBackground,
    onBackground         = Color(0xFF1E3A5F),
    surface              = Color.White,
    onSurface            = Color(0xFF1E3A5F),
    surfaceVariant       = Color(0xFFDBEAFE),
    onSurfaceVariant     = Color(0xFF3B5A8A),
    outline              = Color(0xFFBAD8FF),
    error                = Color(0xFFEF4444),
    onError              = Color.White
)

// ── Bunny Pink: Soft Rose & Lavender ─────────────────────────────────────────
private val BunnyPinkColorScheme = lightColorScheme(
    primary              = BunnyPrimary,
    onPrimary            = Color.White,
    primaryContainer     = BunnyContainer,
    onPrimaryContainer   = Color(0xFF9D174D),
    secondary            = Color(0xFFC084FC),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF4A0080),
    tertiary             = Color(0xFFFDE047),
    onTertiary           = Color(0xFF78350F),
    background           = BunnyBackground,
    onBackground         = Color(0xFF1E0818),  // near-black warm → 17:1 contrast on pink bg
    surface              = Color.White,
    onSurface            = Color(0xFF1E0818),  // near-black → sharp text on white cards
    surfaceVariant       = Color(0xFFFFE4EF),
    onSurfaceVariant     = Color(0xFF6D2B4A),  // deeper rose for secondary labels
    outline              = Color(0xFFF9A8D4),  // slightly more visible outline
    error                = Color(0xFFDC2626),
    onError              = Color.White
)

// ── Dark: Deep Navy & Bright Blue ────────────────────────────────────────────
private val DarkModeColorScheme = darkColorScheme(
    primary              = Color(0xFF60A5FA),
    onPrimary            = Color(0xFF0F172A),
    primaryContainer     = Color(0xFF1E3A5F),
    onPrimaryContainer   = Color(0xFFBFDBFE),
    secondary            = Color(0xFF94A3B8),
    onSecondary          = Color(0xFF0F172A),
    secondaryContainer   = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFCBD5E1),
    tertiary             = Color(0xFF34D399),
    onTertiary           = Color(0xFF0F172A),
    background           = Color(0xFF0F172A),
    onBackground         = Color(0xFFF1F5F9),
    surface              = Color(0xFF1E293B),
    onSurface            = Color(0xFFF1F5F9),
    surfaceVariant       = Color(0xFF334155),
    onSurfaceVariant     = Color(0xFF94A3B8),
    outline              = Color(0xFF475569),
    error                = Color(0xFFF87171),
    onError              = Color(0xFF0F172A)
)

private val KidShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small      = RoundedCornerShape(20.dp),
    medium     = RoundedCornerShape(24.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FamilyStockGateTheme(
    appTheme: AppTheme = AppTheme.MODERN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.MODERN     -> ModernColorScheme
        AppTheme.DARK       -> DarkModeColorScheme
        AppTheme.BEAR_BLUE  -> BearBlueColorScheme
        AppTheme.BUNNY_PINK -> BunnyPinkColorScheme
    }
    val shapes = when (appTheme) {
        AppTheme.MODERN, AppTheme.DARK -> ModernShapes
        else                           -> KidShapes
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes      = shapes,
            typography  = Typography
        ) {
            // LocalContentColor를 명시적으로 설정해야 Scaffold(containerColor=Transparent) 내부에서
            // 텍스트가 색상 스킴의 onBackground를 올바르게 상속받는다.
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ThemedBackground(modifier = Modifier.fillMaxSize())
                    content()
                }
            }
        }
    }
}
