package com.scannerkeyboard.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanResultProcessorTest {
    @Test
    fun regexReplacement_removesLeadingZeros() {
        val raw = "000123456"
        val result = Regex("^0+").replace(raw, "")
        assertEquals("123456", result)
    }
}
