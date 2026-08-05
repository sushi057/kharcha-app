@file:OptIn(ExperimentalTextApi::class)

package com.kharcha.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kharcha.app.R

/**
 * Fraunces — a distinctive, high-contrast display serif with a "wonky" optical
 * axis, used only for large headings and hero amounts. Bundled offline as a
 * variable font (wght/opsz/SOFT/WONK axes). SIL Open Font License 1.1.
 * License: app/src/main/font-licenses/Fraunces-OFL.txt
 */
private val FrauncesDisplay = FontFamily(
    Font(
        resId = R.font.fraunces_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.opticalSizing(72.sp),
        ),
    ),
    Font(
        resId = R.font.fraunces_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.opticalSizing(72.sp),
        ),
    ),
)

/**
 * IBM Plex Sans — the readable text and numeral face. Its "tnum" OpenType
 * feature gives it true tabular figures, which [formatMoney]'s output relies
 * on for column alignment in lists. Bundled offline as a variable font
 * (wdth/wght axes). SIL Open Font License 1.1.
 * License: app/src/main/font-licenses/IBMPlexSans-OFL.txt
 */
private val PlexSansBody = FontFamily(
    Font(
        resId = R.font.ibm_plex_sans_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.ibm_plex_sans_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.ibm_plex_sans_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/** Tabular lining figures, so a column of amounts always lines up. */
const val KharchaTabularFigures = "tnum"

val KharchaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FrauncesDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FrauncesDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = PlexSansBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PlexSansBody,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = KharchaTabularFigures,
    ),
    bodyMedium = TextStyle(
        fontFamily = PlexSansBody,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = KharchaTabularFigures,
    ),
    labelMedium = TextStyle(
        fontFamily = PlexSansBody,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

/** Text style dedicated to money amounts: tabular figures, always. */
val KharchaMoneyTextStyle = TextStyle(
    fontFamily = PlexSansBody,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = KharchaTabularFigures,
)
