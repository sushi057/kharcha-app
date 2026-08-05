package com.kharcha.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategorizerTest {

    private val fees = 1L
    private val income = 2L
    private val dining = 3L

    private val categorizer = Categorizer(
        listOf(
            RuleEntity(id = 1, matchPattern = "WTax.Pd", matchesPrefix = true, categoryId = fees, priority = 100),
            RuleEntity(id = 2, matchPattern = "Charge", matchesPrefix = false, categoryId = fees, priority = 90),
            RuleEntity(id = 3, matchPattern = "Int.Pd", matchesPrefix = true, categoryId = income, priority = 100),
            RuleEntity(id = 4, matchPattern = "HANKOOK SARANG", matchesPrefix = false, categoryId = dining, priority = 50)
        )
    )

    @Test
    fun `withholding tax is a fee`() {
        assertEquals(fees, categorizer.categorize("WTax.Pd:14-04-2026to 16-07-2026", null))
    }

    @Test
    fun `interest paid is income`() {
        assertEquals(income, categorizer.categorize("Int.Pd:14-04-2026 to 16-07-2026", null))
    }

    @Test
    fun `transfer charge is a fee`() {
        assertEquals(fees, categorizer.categorize("cIPS Fund Trf Charge", null))
    }

    @Test
    fun `a truncated merchant name still matches`() {
        assertEquals(
            dining,
            categorizer.categorize(
                "QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU",
                "JAWALAKHEL HANKOOK SARANG RESTAU"
            )
        )
    }

    @Test
    fun `an unmatched remark yields no category`() {
        assertNull(categorizer.categorize("GLOBAL /Shambhu Nath/", null))
    }

    @Test
    fun `higher priority rule wins`() {
        val c = Categorizer(
            listOf(
                RuleEntity(id = 1, matchPattern = "Charge", matchesPrefix = false, categoryId = fees, priority = 10),
                RuleEntity(id = 2, matchPattern = "cIPS", matchesPrefix = true, categoryId = dining, priority = 99)
            )
        )
        assertEquals(dining, c.categorize("cIPS Fund Trf Charge", null))
    }

    @Test
    fun `equal priority ties break deterministically by id regardless of supplied order`() {
        val ruleA = RuleEntity(id = 100, matchPattern = "cIPS", matchesPrefix = true, categoryId = fees, priority = 50)
        val ruleB = RuleEntity(id = 200, matchPattern = "Charge", matchesPrefix = false, categoryId = dining, priority = 50)

        val abOrder = Categorizer(listOf(ruleA, ruleB))
        val baOrder = Categorizer(listOf(ruleB, ruleA))

        val remark = "cIPS Fund Trf Charge"
        assertEquals(abOrder.categorize(remark, null), baOrder.categorize(remark, null))
        assertEquals(fees, abOrder.categorize(remark, null))
        assertEquals(fees, baOrder.categorize(remark, null))
    }
}
