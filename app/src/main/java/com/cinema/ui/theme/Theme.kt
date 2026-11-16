package com.cinema.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CinemaDarkColors = darkColorScheme(
    primary = CinemaPrimary,
    secondary = CinemaSecondary,
    background = CinemaBackground,
    surface = CinemaSurface,
    onPrimary = CinemaText,
    onSecondary = CinemaBackground,
    onBackground = CinemaText,
    onSurface = CinemaText,
    error = CinemaRed
)

@Composable
fun CinemaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CinemaDarkColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
