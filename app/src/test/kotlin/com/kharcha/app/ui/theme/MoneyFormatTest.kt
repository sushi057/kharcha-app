package com.kharcha.app.ui.theme

import com.kharcha.parser.Currency
import com.kharcha.parser.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {
    @Test
    fun `formats NPR minor units with grouping`() {
        assertEquals("NPR 2,984.00", formatMoney(Money(298400L, Currency.NPR)))
    }

    @Test
    fun `formats USD minor units`() {
        assertEquals("USD 1.98", formatMoney(Money(198L, Currency.USD)))
    }

    @Test
    fun `formats larger NPR amounts with multiple grouping separators`() {
        assertEquals("NPR 1,015,625.00", formatMoney(Money(101562500L, Currency.NPR)))
    }
}
