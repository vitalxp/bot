package app.taxicom.commandos.ui.theme

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

// Night-drive first: charcoal cockpit, amber rank stripe. Readable at a glance while moving.
val Amber = Color(0xFFFFB800)
val AmberDim = Color(0xFFC48A00)
val Take = Color(0xFF34D186)
val Maybe = Color(0xFFFFB800)
val Skip = Color(0xFFFF5C5C)
val BoltGreen = Color(0xFF34D186)
val UberWhite = Color(0xFFE8EAED)
val FreeNowPink = Color(0xFFE5004B)

private val Dark = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3D2E00),
    onPrimaryContainer = Amber,
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF0B1A33),
    tertiary = Take,
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF171B22),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF222831),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF3C4450),
    error = Skip,
    onError = Color.White,
)

private val Light = lightColorScheme(
    primary = AmberDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6A3),
    onPrimaryContainer = Color(0xFF3D2E00),
    secondary = Color(0xFF1A73E8),
    tertiary = Color(0xFF0B7A4B),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFCACDD2),
    error = Skip,
)

private val Type = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 48.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
)

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TaxiCommandosTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    // Driving UI is dark-first; light only when the caller opts out of the cockpit look.
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shapes, content = content)
}
