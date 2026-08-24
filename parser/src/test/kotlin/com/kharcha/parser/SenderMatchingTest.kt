package com.kharcha.parser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SenderMatchingTest {

    private val sbl = "SBL_Alert"

    @Test
    fun `the alias itself matches`() {
        assertTrue(SenderMatching.matches("SBL_Alert", sbl))
    }

    @Test
    fun `case does not matter`() {
        assertTrue(SenderMatching.matches("sbl_alert", sbl))
        assertTrue(SenderMatching.matches("SBL_ALERT", sbl))
    }

    @Test
    fun `a substituted separator matches`() {
        // What the Android emulator console actually delivers.
        assertTrue(SenderMatching.matches("SBL§Alert", sbl))
        assertTrue(SenderMatching.matches("SBL-Alert", sbl))
        assertTrue(SenderMatching.matches("SBL.Alert", sbl))
    }

    @Test
    fun `a dropped separator matches`() {
        assertTrue(SenderMatching.matches("SBLAlert", sbl))
    }

    @Test
    fun `a letter or digit in the separator's place is a different sender`() {
        // The bug this replaces: `ADDRESS LIKE 'SBL_Alert'` accepted all of these.
        assertFalse(SenderMatching.matches("SBLXAlert", sbl))
        assertFalse(SenderMatching.matches("SBL1Alert", sbl))
    }

    @Test
    fun `a longer address containing the alias is not the alias`() {
        assertFalse(SenderMatching.matches("NOTSBL_Alert", sbl))
        assertFalse(SenderMatching.matches("SBL_AlertX", sbl))
        assertFalse(SenderMatching.matches("AD-SBL_Alert", sbl))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertTrue(SenderMatching.matches("  SBL_Alert ", sbl))
    }

    @Test
    fun `a null or empty sender never matches`() {
        assertFalse(SenderMatching.matches(null, sbl))
        assertFalse(SenderMatching.matches("", sbl))
    }

    @Test
    fun `an unrelated sender never matches`() {
        assertFalse(SenderMatching.matches("9779801234567", sbl))
        assertFalse(SenderMatching.matches("NIC_Alert", sbl))
    }

    @Test
    fun `the sql prefilter covers both separator forms`() {
        val patterns = SenderMatching.sqlLikePatterns(sbl)
        assertTrue("SBL_Alert" in patterns)
        assertTrue("SBLAlert" in patterns)
    }
}
