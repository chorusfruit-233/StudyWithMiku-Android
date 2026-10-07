@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.shshouse.studywithmiku

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF006B5E), onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F5D7), onPrimaryContainer = Color(0xFF004F43),
    secondary = Color(0xFF52644E), secondaryContainer = Color(0xFFDBE9CE),
    onSecondaryContainer = Color(0xFF253521), tertiary = Color(0xFF80516A),
    tertiaryContainer = Color(0xFFFFD9E9), onTertiaryContainer = Color(0xFF633951),
    background = Color(0xFFF4F8F3), onBackground = Color(0xFF182D27),
    surface = Color(0xFFF4F8F3), onSurface = Color(0xFF182D27),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEDF2EB),
    surfaceContainer = Color(0xFFE7EEE5), surfaceContainerHigh = Color(0xFFDFE8DC),
    surfaceContainerHighest = Color(0xFFD8E3D4), onSurfaceVariant = Color(0xFF52665C),
    outline = Color(0xFF73867B), outlineVariant = Color(0xFFCDD8CE)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8EDABF), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005044), onPrimaryContainer = Color(0xFFB7F5D7),
    secondaryContainer = Color(0xFF394C35), onSecondaryContainer = Color(0xFFDCE9D1),
    tertiary = Color(0xFFF1B7D3), tertiaryContainer = Color(0xFF60394F),
    onTertiaryContainer = Color(0xFFFFD9E9), background = Color(0xFF0E1814),
    onBackground = Color(0xFFE0E9DF), surface = Color(0xFF0E1814), onSurface = Color(0xFFE0E9DF),
    surfaceContainerLowest = Color(0xFF0A130F), surfaceContainerLow = Color(0xFF17221C),
    surfaceContainer = Color(0xFF1D2922), surfaceContainerHigh = Color(0xFF27342C),
    surfaceContainerHighest = Color(0xFF323F36), onSurfaceVariant = Color(0xFFB7C8BC),
    outline = Color(0xFF84988B), outlineVariant = Color(0xFF3C4C41)
)
@Composable fun MikuTheme(prefs: Preferences, content: @Composable () -> Unit) {
    val dark = prefs.theme == 2 || prefs.theme == 0 && isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = if (prefs.dynamicColor && Build.VERSION.SDK_INT >= 31)
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else if (dark) Dark else Light
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(),
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp),small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp),large = RoundedCornerShape(32.dp),extraLarge = RoundedCornerShape(40.dp)),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = FontFamily.SansSerif,fontWeight = FontWeight.Bold,fontSize = 76.sp,letterSpacing = (-3).sp),
            headlineLarge = TextStyle(fontWeight = FontWeight.Bold,fontSize = 30.sp,lineHeight = 42.sp,letterSpacing = (-0.8).sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold,fontSize = 25.sp,lineHeight = 34.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold,fontSize = 21.sp,lineHeight = 28.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold,fontSize = 16.sp,lineHeight = 24.sp),
            bodyLarge = TextStyle(fontSize = 16.sp,lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp,lineHeight = 22.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold,fontSize = 14.sp,lineHeight = 20.sp)
        ),content = content)
}
