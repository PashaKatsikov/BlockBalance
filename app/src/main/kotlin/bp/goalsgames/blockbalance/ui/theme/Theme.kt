package bp.goalsgames.blockbalance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Yard palette: night blues for panels, hazard gold for anything actionable. */
object Yard {
    val night = Color(0xFF101A2C)
    val panel = Color(0xFF16233C)
    val panelHigh = Color(0xFF1E3050)
    val stroke = Color(0xFF2B3F66)
    val ink = Color(0xFFE8EEFC)
    val inkSoft = Color(0xFFA9BADA)
    val gold = Color(0xFFFFC945)
    val goldDeep = Color(0xFFE39B12)
    val sky = Color(0xFF7FC4FF)
    val steel = Color(0xFF2F86E0)
    val steelDeep = Color(0xFF1B5FB0)
    val mint = Color(0xFF4ED8A0)
    val ember = Color(0xFFFF6B57)
    val shadow = Color(0xCC060B16)
}

private val yardScheme = darkColorScheme(
    primary = Yard.gold,
    onPrimary = Color(0xFF2A1B00),
    secondary = Yard.sky,
    onSecondary = Color(0xFF04203A),
    tertiary = Yard.mint,
    background = Yard.night,
    onBackground = Yard.ink,
    surface = Yard.panel,
    onSurface = Yard.ink,
    surfaceVariant = Yard.panelHigh,
    onSurfaceVariant = Yard.inkSoft,
    error = Yard.ember,
    outline = Yard.stroke,
)

private val display = FontFamily.SansSerif

private val yardTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        letterSpacing = (-1).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Black,
        fontSize = 38.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp,
        letterSpacing = 1.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    ),
)

/** The art is painted for a dark shell, so the palette never follows the system. */
@Composable
fun BlockBalanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = yardScheme,
        typography = yardTypography,
        content = content,
    )
}
