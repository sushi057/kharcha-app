package com.kharcha.app.ui.theme

import com.kharcha.parser.Currency
import com.kharcha.parser.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards that every text style used to display money carries the `tnum` OpenType
 * feature for tabular figures, so amounts line up vertically in a list even when
 * the digit count, the sign, or the currency prefix varies. `formatMoney` and
 * `formatSignedAmount` are meaningless without it.
 *
 * The list below is the set of styles money is actually drawn in. In v2 that is
 * the display faces (hero amount, budget headline) and the mono money styles;
 * `bodyLarge` / `bodyMedium` are prose styles that no longer render an amount,
 * and requiring `tnum` of them would be enforcing the feature on text that has
 * no figures to align. If you draw money in a new style, add it here.
 */
class TypographyTest {

    @Test
    fun `all money-bearing TextStyles carry tabular figures feature`() {
        // Styles explicitly used for money display
        val moneyStyles = listOf(
            "displayLarge (hero amount)" to KharchaTypography.displayLarge,
            "displayMedium (budget headline)" to KharchaTypography.displayMedium,
            "KharchaMoneyTextStyle (list amount)" to KharchaMoneyTextStyle,
            "KharchaMoneyBigTextStyle (summary total)" to KharchaMoneyBigTextStyle,
            "KharchaMoneyDisplayTextStyle (inbox amount)" to KharchaMoneyDisplayTextStyle,
        )

        moneyStyles.forEach { (name, style) ->
            assertTrue(
                style.fontFeatureSettings == KharchaTabularFigures,
                "$name must carry fontFeatureSettings=\"$KharchaTabularFigures\" " +
                    "so currency amounts line up vertically in lists, " +
                    "but got \"${style.fontFeatureSettings}\""
            )
        }
    }

    @Test
    fun `zero carries no sign, even where a plus is asked for`() {
        val zero = Money(0L, Currency.NPR)
        assertEquals("0.00", formatSignedAmount(zero, explicitPlus = true))
    }
}
