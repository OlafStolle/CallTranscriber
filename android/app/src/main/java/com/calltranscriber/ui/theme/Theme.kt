package com.calltranscriber.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Blue100,
    secondary = Green600,
    error = Red600,
    background = Gray50,
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = Gray200,
    onSurfaceVariant = Gray600,
)

@Composable
fun CallTranscriberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else LightColors,
        content = content,
    )
}
