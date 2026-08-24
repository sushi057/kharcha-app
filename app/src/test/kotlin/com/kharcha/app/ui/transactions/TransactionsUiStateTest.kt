package com.kharcha.app.ui.transactions

import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [TransactionsUiState.filteredTransactions] directly — a pure
 * function of state, so no ViewModel/DAO wiring is needed. Each test is
 * written so it fails if its predicate were dropped from the `filter {}` in
 * `filteredTransactions` (e.g. the category test includes a transaction that
 * matches the search query but not the category, and asserts it out).
 */
class TransactionsUiStateTest {

    private fun txn(
        id: Long,
        remark: String = "remark",
        merchant: String? = null,
        categoryId: Long? = null,
        occurredAtEpochMillis: Long = 1_000L,
        amountMinorUnits: Long = 100L,
        excludedFromSpending: Boolean = false,
    ) = TransactionEntity(
        id = id,
        rawMessageId = null,
        sourceAccount = "acct",
        amountMinorUnits = amountMinorUnits,
        currency = Currency.NPR,
        direction = Direction.DEBIT,
        occurredAtEpochMillis = occurredAtEpochMillis,
        remark = remark,
        merchant = merchant,
        balanceAfterMinorUnits = null,
        categoryId = categoryId,
        categoryIsManualOverride = false,
        excludedFromSpending = excludedFromSpending,
        isManualEntry = false,
    )

    @Test
    fun `no filters returns everything`() {
        val transactions = listOf(txn(1), txn(2), txn(3))
        val state = TransactionsUiState(transactions = transactions)
        assertEquals(transactions, state.filteredTransactions)
    }

    @Test
    fun `search query matches remark or merchant, case-insensitively`() {
        val hankook = txn(1, remark = "QR Payment to HANKOOK", merchant = "HANKOOK SARANG")
        val grocery = txn(2, remark = "Groceries at Bhatbhateni", merchant = "BHATBHATENI")
        val noMerchant = txn(3, remark = "Interest paid hankook-adjacent-in-remark-only")

        val state = TransactionsUiState(
            transactions = listOf(hankook, grocery, noMerchant),
            searchQuery = "hankook",
        )

        assertEquals(setOf(1L, 3L), state.filteredTransactions.map { it.id }.toSet())
    }

    @Test
    fun `category filter excludes transactions in other categories`() {
        val food = txn(1, categoryId = 1L)
        val shopping = txn(2, categoryId = 3L)
        val uncategorized = txn(3, categoryId = null)

        val state = TransactionsUiState(
            transactions = listOf(food, shopping, uncategorized),
            categoryFilter = 1L,
        )

        assertEquals(listOf(1L), state.filteredTransactions.map { it.id })
    }

    @Test
    fun `date range filter excludes transactions outside the range`() {
        val before = txn(1, occurredAtEpochMillis = 100L)
        val within = txn(2, occurredAtEpochMillis = 500L)
        val after = txn(3, occurredAtEpochMillis = 900L)

        val state = TransactionsUiState(
            transactions = listOf(before, within, after),
            dateRangeStartEpochMillis = 200L,
            dateRangeEndEpochMillis = 800L,
        )

        assertEquals(listOf(2L), state.filteredTransactions.map { it.id })
    }

    @Test
    fun `date range boundaries are inclusive`() {
        val startBoundary = txn(1, occurredAtEpochMillis = 200L)
        val endBoundary = txn(2, occurredAtEpochMillis = 800L)

        val state = TransactionsUiState(
            transactions = listOf(startBoundary, endBoundary),
            dateRangeStartEpochMillis = 200L,
            dateRangeEndEpochMillis = 800L,
        )

        assertEquals(setOf(1L, 2L), state.filteredTransactions.map { it.id }.toSet())
    }

    @Test
    fun `search, category and date range filters combine with AND semantics`() {
        // Matches search + category but not date range.
        val outsideDateRange = txn(
            1, remark = "QR Payment to HANKOOK", categoryId = 1L, occurredAtEpochMillis = 50L
        )
        // Matches search + date range but not category.
        val wrongCategory = txn(
            2, remark = "QR Payment to HANKOOK", categoryId = 3L, occurredAtEpochMillis = 500L
        )
        // Matches category + date range but not search.
        val wrongSearch = txn(
            3, remark = "Groceries", categoryId = 1L, occurredAtEpochMillis = 500L
        )
        // Matches all three.
        val matchesAll = txn(
            4, remark = "QR Payment to HANKOOK", categoryId = 1L, occurredAtEpochMillis = 500L
        )

        val state = TransactionsUiState(
            transactions = listOf(outsideDateRange, wrongCategory, wrongSearch, matchesAll),
            searchQuery = "hankook",
            categoryFilter = 1L,
            dateRangeStartEpochMillis = 200L,
            dateRangeEndEpochMillis = 800L,
        )

        assertEquals(listOf(4L), state.filteredTransactions.map { it.id })
    }

    @Test
    fun `sorting by highest and lowest orders by amount, not by date`() {
        val small = txn(1, amountMinorUnits = 100L, occurredAtEpochMillis = 900L)
        val large = txn(2, amountMinorUnits = 90_000L, occurredAtEpochMillis = 100L)
        val middle = txn(3, amountMinorUnits = 5_000L, occurredAtEpochMillis = 500L)
        val transactions = listOf(small, large, middle)

        assertEquals(
            listOf(2L, 3L, 1L),
            TransactionsUiState(transactions = transactions, sort = TransactionSort.Highest)
                .filteredTransactions.map { it.id },
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            TransactionsUiState(transactions = transactions, sort = TransactionSort.Lowest)
                .filteredTransactions.map { it.id },
        )
    }

    @Test
    fun `chronological sorts do not depend on the order the DAO returned`() {
        val oldest = txn(1, occurredAtEpochMillis = 100L)
        val newest = txn(2, occurredAtEpochMillis = 900L)
        val transactions = listOf(oldest, newest)

        assertEquals(
            listOf(2L, 1L),
            TransactionsUiState(transactions = transactions, sort = TransactionSort.Newest)
                .filteredTransactions.map { it.id },
        )
        assertEquals(
            listOf(1L, 2L),
            TransactionsUiState(transactions = transactions, sort = TransactionSort.Oldest)
                .filteredTransactions.map { it.id },
        )
    }

    @Test
    fun `only the chronological sorts keep day headers`() {
        assertEquals(true, TransactionSort.Newest.isChronological)
        assertEquals(true, TransactionSort.Oldest.isChronological)
        assertEquals(false, TransactionSort.Highest.isChronological)
        assertEquals(false, TransactionSort.Lowest.isChronological)
    }

    @Test
    fun `excluded-only shows just the rows waved out of spending`() {
        val counted = txn(1)
        val excluded = txn(2, excludedFromSpending = true)

        val state = TransactionsUiState(
            transactions = listOf(counted, excluded),
            excludedOnly = true,
        )

        assertEquals(listOf(2L), state.filteredTransactions.map { it.id })
    }

    @Test
    fun `excluded rows are still shown when the filter is off`() {
        val state = TransactionsUiState(
            transactions = listOf(txn(1), txn(2, excludedFromSpending = true)),
        )
        assertEquals(setOf(1L, 2L), state.filteredTransactions.map { it.id }.toSet())
    }

    @Test
    fun `searching a number matches the amount as typed into the app`() {
        val twoNinetyEight = txn(1, remark = "no words here", amountMinorUnits = 298_400L)
        val other = txn(2, remark = "no words here", amountMinorUnits = 1_200L)

        val state = TransactionsUiState(
            transactions = listOf(twoNinetyEight, other),
            searchQuery = "2984",
        )

        assertEquals(listOf(1L), state.filteredTransactions.map { it.id })
    }

    @Test
    fun `hasActiveFilters reports only what actually narrows the list`() {
        assertEquals(false, TransactionsUiState().hasActiveFilters)
        // A search query is visible in its own pill, so it does not light the filter icon.
        assertEquals(false, TransactionsUiState(searchQuery = "hankook").hasActiveFilters)
        assertEquals(true, TransactionsUiState(categoryFilter = 1L).hasActiveFilters)
        assertEquals(true, TransactionsUiState(excludedOnly = true).hasActiveFilters)
        assertEquals(true, TransactionsUiState(sort = TransactionSort.Highest).hasActiveFilters)
        assertEquals(true, TransactionsUiState(dateRangeStartEpochMillis = 1L).hasActiveFilters)
    }
}
