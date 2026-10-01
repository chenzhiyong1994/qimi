package com.localpasswordmanager.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 森林绿承担操作与状态，暖白承担阅读层次；金色仅用于小面积点缀。
private val LightColors = lightColorScheme(
    primary = Color(0xFF285847),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EDE3),
    onPrimaryContainer = Color(0xFF163A2B),
    inversePrimary = Color(0xFFA7D1B7),
    secondary = Color(0xFF71623D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0EAD9),
    onSecondaryContainer = Color(0xFF4E4328),
    tertiary = Color(0xFF617569),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFE8E0),
    onTertiaryContainer = Color(0xFF293F32),
    background = Color(0xFFF5F6F0),
    onBackground = Color(0xFF1C3027),
    surface = Color(0xFFFCFCF8),
    onSurface = Color(0xFF1C3027),
    surfaceVariant = Color(0xFFEBEEE5),
    onSurfaceVariant = Color(0xFF59665B),
    surfaceTint = Color(0xFF285847),
    surfaceDim = Color(0xFFE0E4DB),
    surfaceBright = Color(0xFFFCFCF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9F3),
    surfaceContainer = Color(0xFFF0F2EA),
    surfaceContainerHigh = Color(0xFFEBEEE5),
    surfaceContainerHighest = Color(0xFFE4E9DF),
    outline = Color(0xFF879287),
    outlineVariant = Color(0xFFD8DFD3),
    inverseSurface = Color(0xFF25392F),
    inverseOnSurface = Color(0xFFF1F4EC),
    error = Color(0xFFA23632),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE8E3),
    onErrorContainer = Color(0xFF782521),
    scrim = Color(0xFF0E2117),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7D1B7),
    onPrimary = Color(0xFF173C2C),
    primaryContainer = Color(0xFF2D513E),
    onPrimaryContainer = Color(0xFFD7ECDD),
    inversePrimary = Color(0xFF285847),
    secondary = Color(0xFFD4C59B),
    onSecondary = Color(0xFF3D3420),
    secondaryContainer = Color(0xFF4D452E),
    onSecondaryContainer = Color(0xFFF1E6C8),
    tertiary = Color(0xFFBBCBBE),
    onTertiary = Color(0xFF23392C),
    tertiaryContainer = Color(0xFF3B5142),
    onTertiaryContainer = Color(0xFFE0ECE1),
    background = Color(0xFF101D17),
    onBackground = Color(0xFFE4ECE0),
    surface = Color(0xFF16251D),
    onSurface = Color(0xFFE4ECE0),
    surfaceVariant = Color(0xFF2C3B30),
    onSurfaceVariant = Color(0xFFAFBBAE),
    surfaceTint = Color(0xFFA7D1B7),
    surfaceDim = Color(0xFF101D17),
    surfaceBright = Color(0xFF314235),
    surfaceContainerLowest = Color(0xFF0C1812),
    surfaceContainerLow = Color(0xFF16251D),
    surfaceContainer = Color(0xFF1B2B21),
    surfaceContainerHigh = Color(0xFF223329),
    surfaceContainerHighest = Color(0xFF2C3E32),
    outline = Color(0xFF7D8D7E),
    outlineVariant = Color(0xFF3C4D3E),
    inverseSurface = Color(0xFFE4ECE0),
    inverseOnSurface = Color(0xFF24372A),
    error = Color(0xFFFFB5AC),
    onError = Color(0xFF5F201C),
    errorContainer = Color(0xFF6B302A),
    onErrorContainer = Color(0xFFFFDAD3),
    scrim = Color(0xFF000000),
)

private fun type(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

// 中文沿用系统字体，较宽行高让表单、说明和大字体模式保持可读。
private val VaultTypography = Typography(
    displayLarge = type(48, 58, FontWeight.SemiBold),
    displayMedium = type(40, 50, FontWeight.SemiBold),
    displaySmall = type(34, 44, FontWeight.SemiBold),
    headlineLarge = type(30, 40, FontWeight.SemiBold),
    headlineMedium = type(27, 36, FontWeight.SemiBold),
    headlineSmall = type(23, 32, FontWeight.SemiBold),
    titleLarge = type(20, 29, FontWeight.SemiBold),
    titleMedium = type(16, 24, FontWeight.SemiBold),
    titleSmall = type(14, 21, FontWeight.Medium),
    bodyLarge = type(16, 25),
    bodyMedium = type(14, 22),
    bodySmall = type(12, 19),
    labelLarge = type(14, 21, FontWeight.SemiBold),
    labelMedium = type(12, 18, FontWeight.Medium),
    labelSmall = type(11, 16, FontWeight.Medium),
)

private val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = VaultTypography,
        shapes = VaultShapes,
        content = content,
    )
}
