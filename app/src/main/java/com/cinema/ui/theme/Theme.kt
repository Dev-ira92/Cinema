package com.cinema.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

private val CinemaDarkColors = darkColorScheme(
    primary = CinemaPrimary,
    onPrimary = CinemaOnPrimary,

    secondary = CinemaSecondary,
    onSecondary = CinemaBackground,

    background = CinemaBackground,
    onBackground = CinemaOnBackground,

    surface = CinemaSurface,
    onSurface = CinemaOnSurface,

    error = CinemaRed,

    surfaceVariant = CinemaCard,
    onSurfaceVariant = CinemaMuted,

    outline = CinemaBorder
)

@Composable
fun CinemaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CinemaDarkColors,
        typography = Typography(),
        content = content
    )
}
