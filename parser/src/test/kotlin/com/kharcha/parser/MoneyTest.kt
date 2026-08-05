package com.kharcha.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test
    fun `parses amounts with thousands separators`() {
        assertEquals(Money(298400L, Currency.NPR), parseAmount("2,984.00", Currency.NPR))
        assertEquals(Money(101562500L, Currency.NPR), parseAmount("1,015,625.00", Currency.NPR))
        assertEquals(Money(2492044L, Currency.NPR), parseAmount("24,920.44", Currency.NPR))
        assertEquals(Money(800L, Currency.NPR), parseAmount("8.00", Currency.NPR))
        assertEquals(Money(198L, Currency.USD), parseAmount("1.98", Currency.USD))
    }

    @Test
    fun `parses amounts without decimals`() {
        assertEquals(Money(50000L, Currency.NPR), parseAmount("500", Currency.NPR))
    }

    @Test
    fun `rejects malformed amounts`() {
        assertNull(parseAmount("", Currency.NPR))
        assertNull(parseAmount("abc", Currency.NPR))
        assertNull(parseAmount("1.2.3", Currency.NPR))
        assertNull(parseAmount("1,00.000", Currency.NPR))
        assertNull(parseAmount("-5.00", Currency.NPR))
    }
}
