package com.lagradost.cloudstream4.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.em

object AppFont {
    private val defaultTypography = Typography()
    val typography @Composable get(): Typography {
        val lineHeight = 1.3.em
        return Typography(
            displayLarge = defaultTypography.displayLarge.copy(lineHeight = lineHeight),
            displayMedium = defaultTypography.displayMedium.copy(lineHeight = lineHeight),
            displaySmall = defaultTypography.displaySmall.copy(lineHeight = lineHeight),
            headlineLarge = defaultTypography.headlineLarge.copy(lineHeight = lineHeight),
            headlineMedium = defaultTypography.headlineMedium.copy(lineHeight = lineHeight),
            headlineSmall = defaultTypography.headlineSmall.copy(lineHeight = lineHeight),
            titleLarge = defaultTypography.titleLarge.copy(lineHeight = lineHeight),
            titleMedium = defaultTypography.titleMedium.copy(lineHeight = lineHeight),
            titleSmall = defaultTypography.titleSmall.copy(lineHeight = lineHeight),
            bodyLarge = defaultTypography.bodyLarge.copy(lineHeight = lineHeight),
            bodyMedium = defaultTypography.bodyMedium.copy(lineHeight = lineHeight),
            bodySmall = defaultTypography.bodySmall.copy(lineHeight = lineHeight),
            labelLarge = defaultTypography.labelLarge.copy(lineHeight = lineHeight),
            labelMedium = defaultTypography.labelMedium.copy(lineHeight = lineHeight),
            labelSmall = defaultTypography.labelSmall.copy(lineHeight = lineHeight)
        )
    }
}

