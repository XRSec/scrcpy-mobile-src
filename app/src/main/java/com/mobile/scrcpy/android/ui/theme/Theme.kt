package com.mobile.scrcpy.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.common.AppColors
import com.mobile.scrcpy.android.core.data.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A90E2),
    secondary = Color(0xFFE85D75),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = AppColors.lightDropdownBackground,  // DropdownMenu 使用纯白
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE0E0E0)
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.darkButtonPrimary,
    secondary = Color(0xFFE85D75),
    background = AppColors.darkBackground,
    surface = AppColors.darkCard,
    surfaceVariant = AppColors.darkDropdownBackground,  // DropdownMenu 使用
    onSurface = AppColors.darkTextPrimary,
    onSurfaceVariant = AppColors.darkTextSecondary,
    outline = AppColors.darkDivider
)

// 统一字体大小规范
private val AppTypography = Typography(
    // title - 17sp (标题、按钮)
    titleLarge = TextStyle(fontSize = 17.sp),
    titleMedium = TextStyle(fontSize = 16.sp),
    titleSmall = TextStyle(fontSize = 15.sp),
    
    // body - 15sp (正文、描述)
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp), // caption - 13sp
    
    // label - 17sp (按钮标签)
    labelLarge = TextStyle(fontSize = 17.sp),
    labelMedium = TextStyle(fontSize = 15.sp),
    labelSmall = TextStyle(fontSize = 13.sp)
)

/**
 * Scrcpy Remote 主题
 * 
 * @param themeMode 主题模式（SYSTEM/DARK/LIGHT）
 * @param content 内容
 */
@Composable
fun ScreenRemoteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
