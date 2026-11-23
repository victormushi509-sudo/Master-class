package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VictorDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = InkPrimary,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = ElectricBlueBright,
    secondary = ElectricCyan,
    onSecondary = NavyVoid,
    secondaryContainer = NavyHigh,
    onSecondaryContainer = InkPrimary,
    tertiary = ElectricBlueBright,
    onTertiary = NavyVoid,
    background = NavyBase,
    onBackground = InkPrimary,
    surface = NavySurface,
    onSurface = InkPrimary,
    surfaceVariant = NavyElevated,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = NavyVoid,
    surfaceContainerLow = NavyBase,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavyElevated,
    surfaceContainerHighest = NavyHigh,
    outline = NavyBorder,
    outlineVariant = NavyElevated,
    error = StatusError,
    onError = InkPrimary,
    errorContainer = StatusErrorContainer,
    onErrorContainer = InkPrimary
)

val VictorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VictorVideoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VictorDarkColorScheme,
        typography = VictorTypography,
        shapes = VictorShapes,
        content = content
    )
}
