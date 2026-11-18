package com.example.czurozvrh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.czurozvrh.R

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

/** Číslice stejné šířky – časy pod sebou lícují. */
const val TabularNums = "tnum"

private fun style(size: Int, line: Int, weight: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = spacing.em,
)

val Typography = Typography(
    displaySmall = style(34, 40, 800, -0.02),
    headlineMedium = style(28, 34, 800, -0.02),
    headlineSmall = style(24, 30, 800, -0.015),
    titleLarge = style(20, 26, 700, -0.01),
    titleMedium = style(16, 22, 700, -0.005),
    titleSmall = style(14, 19, 700),
    bodyLarge = style(16, 23, 500),
    bodyMedium = style(14, 20, 500),
    bodySmall = style(12, 16, 500),
    labelLarge = style(14, 18, 700),
    labelMedium = style(12, 16, 600),
    labelSmall = style(11, 14, 700, 0.02),
)
