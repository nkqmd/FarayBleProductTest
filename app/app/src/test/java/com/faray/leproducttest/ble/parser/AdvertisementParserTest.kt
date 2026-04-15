package com.faray.leproducttest.ble.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvertisementParserTest {

    private val parser = AdvertisementParser()

    @Test
    fun parsesUppercaseMacWithHyphenatedPrefix() {
        assertEquals(0x001122AABBCCL, parser.parseMacFromName("DUT-001122AABBCC", "DUT-"))
    }

    @Test
    fun parsesUppercaseMacWithUnderscorePrefix() {
        assertEquals(0x001122AABBCCL, parser.parseMacFromName("DUT_001122AABBCC", "DUT_"))
    }

    @Test
    fun rejectsLowercaseMacSuffix() {
        assertNull(parser.parseMacFromName("DUT_001122aabbcc", "DUT_"))
    }

    @Test
    fun rejectsSuffixWithUnexpectedLength() {
        assertNull(parser.parseMacFromName("DUT_001122AABBCCDD", "DUT_"))
    }
}
