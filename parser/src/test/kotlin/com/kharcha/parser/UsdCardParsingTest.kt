package com.kharcha.parser

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UsdCardParsingTest {
    @Test
    fun `parses a USD card purchase`() {
        val result = SblAlertRuleset.parse(
            "SBL Card ***5367 used at SPACESHIP.COM* NRXD3L, US for USD 1.98 on 02.08.26 23:28 " +
                "Authid 512208 Remaining Balance after txn USD 241.22. INFO 015970020"
        )
        assertIs<ParseResult.Parsed>(result)
        val txn = result.transaction
        assertEquals("***5367", txn.sourceAccount)
        assertEquals(Money(198L, Currency.USD), txn.amount)
        assertEquals(Direction.DEBIT, txn.direction)
        assertEquals(LocalDateTime(2026, 8, 2, 23, 28), txn.occurredAt)
        assertEquals("SPACESHIP.COM* NRXD3L, US", txn.merchant)
        assertEquals(Money(24122L, Currency.USD), txn.balanceAfter)
    }

    @Test
    fun `card transactions are always debits`() {
        val result = SblAlertRuleset.parse(
            "SBL Card ***5367 used at AMAZON, US for USD 10.00 on 02.08.26 23:28 " +
                "Authid 512208 Remaining Balance after txn USD 231.22. INFO 015970020"
        )
        assertIs<ParseResult.Parsed>(result)
        assertEquals(Direction.DEBIT, result.transaction.direction)
    }
}
