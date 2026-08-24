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
 * Three faces, three jobs — the v2 design system's type contract:
 *
 *  - [KharchaDisplay] (Calistoga) for the numbers and titles you read first,
 *  - [KharchaBody] (Inter) for everything you read as a sentence,
 *  - [KharchaMono] (JetBrains Mono) for anything that has to line up in a column
 *    (amounts) or read as a machine label (the small uppercase card labels).
 *
 * Nothing else. A composable that wants a fourth voice is a composable that has
 * drifted from the design.
 */

/**
 * Calistoga — a single-weight display serif with heavy, warm slab-ish stems.
 * Used only for hero amounts and screen/app-bar titles, never for body copy:
 * it has one weight and no italic, so it cannot carry a hierarchy on its own.
 * SIL Open Font License 1.1 — app/src/main/font-licenses/Calistoga-OFL.txt
 */
val KharchaDisplay = FontFamily(
    Font(resId = R.font.calistoga_regular, weight = FontWeight.Normal),
)

/**
 * Inter — the body and UI face. Bundled as a variable font (opsz/wght), so the
 * 400 / 500 / 600 the design calls for are three instances of one file rather
 * than three files. SIL Open Font License 1.1 —
 * app/src/main/font-licenses/Inter-OFL.txt
 */
val KharchaBody = FontFamily(
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * JetBrains Mono — the data face. Every glyph is the same width, which is the
 * whole point: a column of amounts lines up on the decimal point no matter how
 * many digits each one has, without any tabular-figure feature to opt into.
 * SIL Open Font License 1.1 — app/src/main/font-licenses/JetBrainsMono-OFL.txt
 */
val KharchaMono = FontFamily(
    Font(
        resId = R.font.jetbrains_mono_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.jetbrains_mono_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.jetbrains_mono_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** Tabular lining figures, so a column of amounts always lines up. */
const val KharchaTabularFigures = "tnum"

val KharchaTypography = Typography(
    // Hero amount: the month's number, the one thing on the dashboard that is
    // read from across the room.
    displayLarge = TextStyle(
        fontFamily = KharchaDisplay,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = KharchaTabularFigures,
    ),
    // Secondary display: the donut's centre figure, a budget's headline number.
    displayMedium = TextStyle(
        fontFamily = KharchaDisplay,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = KharchaTabularFigures,
    ),
    // App bar / screen title.
    headlineMedium = TextStyle(
        fontFamily = KharchaDisplay,
        fontSize = 21.sp,
        lineHeight = 24.sp,
    ),
    // Card and section headings that are prose rather than display.
    titleMedium = TextStyle(
        fontFamily = KharchaBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
    ),
    // Transaction / list-row name.
    titleSmall = TextStyle(
        fontFamily = KharchaBody,
        fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.07).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = KharchaBody,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    // The workhorse: chip labels, cash-flow row labels, legend entries.
    bodyMedium = TextStyle(
        fontFamily = KharchaBody,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    ),
    // "mini" in the design: secondary detail under a name, right-hand captions.
    bodySmall = TextStyle(
        fontFamily = KharchaBody,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    ),
    // Pill buttons.
    labelLarge = TextStyle(
        fontFamily = KharchaBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    // The mono uppercase card label — "SPENT THIS MONTH", "CASH FLOW".
    // 0.15em at 10sp is 1.5sp of tracking; this is what makes it read as a
    // label rather than as small body text.
    labelMedium = TextStyle(
        fontFamily = KharchaMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
    ),
    // Nav bar item labels.
    labelSmall = TextStyle(
        fontFamily = KharchaBody,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
    ),
)

/** Money in a list: mono, medium, 13.5sp — the design's `.money`. */
val KharchaMoneyTextStyle = TextStyle(
    fontFamily = KharchaMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.5.sp,
    lineHeight = 18.sp,
    fontFeatureSettings = KharchaTabularFigures,
)

/** Money that ends a summary — a net total, a day subtotal. The design's `.money.big`. */
val KharchaMoneyBigTextStyle = KharchaMoneyTextStyle.copy(
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
)

/** The parsed amount at the top of an inbox card: mono, bold, 16sp. */
val KharchaMoneyDisplayTextStyle = KharchaMoneyTextStyle.copy(
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 21.sp,
)

/**
 * Small mono for data that is not money: a day-header date, a raw SMS excerpt,
 * an "ignored because" reason tag.
 */
val KharchaMonoSmallTextStyle = TextStyle(
    fontFamily = KharchaMono,
    fontWeight = FontWeight.Medium,
    fontSize = 10.5.sp,
    lineHeight = 15.sp,
    letterSpacing = 1.3.sp,
)
