/*
 * Copyright (c) 2026 VNC Android Free contributors.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.vncandroid.free.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardTest {

    @Test
    fun testShiftedSpecialCharactersNeedShift() {
        val shiftedSymbols = "~!@#$%^&*()_+{}|:\"<>?"
        for (c in shiftedSymbols) {
            assertTrue("Expected character '$c' to need shift", isShiftNeeded(c.code))
        }
    }

    @Test
    fun testNumbersDoNotNeedShift() {
        for (c in '0'..'9') {
            assertFalse("Expected digit '$c' not to need shift", isShiftNeeded(c.code))
        }
    }

    @Test
    fun testUnshiftedPunctuationDoesNotNeedShift() {
        val unshiftedSymbols = "`=[]\\;',./ "
        for (c in unshiftedSymbols) {
            assertFalse("Expected character '$c' not to need shift", isShiftNeeded(c.code))
        }
    }

    @Test
    fun testUppercaseLettersNeedShift() {
        for (c in 'A'..'Z') {
            assertTrue("Expected uppercase '$c' to need shift", isShiftNeeded(c.code))
        }
    }

    @Test
    fun testLowercaseLettersDoNotNeedShift() {
        for (c in 'a'..'z') {
            assertFalse("Expected lowercase '$c' not to need shift", isShiftNeeded(c.code))
        }
    }

    @Test
    fun testWhitespaceAndControlDoNotNeedShift() {
        assertFalse(isShiftNeeded(' '.code))
        assertFalse(isShiftNeeded('\t'.code))
        assertFalse(isShiftNeeded('\n'.code))
        assertFalse(isShiftNeeded('\r'.code))
    }
}
