package com.jobsight.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JobSightBrandColorScheme =
  darkColorScheme(
    primary = JobSightBlue,
    onPrimary = JobSightWhite,
    primaryContainer = JobSightNavyCard,
    onPrimaryContainer = JobSightWhite,
    secondary = JobSightCoral,
    onSecondary = JobSightWhite,
    secondaryContainer = JobSightNavyElevated,
    onSecondaryContainer = JobSightWhite,
    tertiary = JobSightBlueLight,
    background = JobSightNavyBackground,
    onBackground = JobSightWhite,
    surface = JobSightNavyBackground,
    onSurface = JobSightWhite,
    surfaceVariant = JobSightNavyCard,
    onSurfaceVariant = JobSightSilver,
  )

@Composable
fun JobSightTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = JobSightBrandColorScheme,
    typography = Typography,
    content = content,
  )
}
