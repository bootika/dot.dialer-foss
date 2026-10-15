package dev.goodwy.rphone.view.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneSearchQuickActionsTest {
    @Test
    fun `accepts numbers once they contain at least three digits`() {
        assertEquals("123", actionableDialQuery("123"))
        assertEquals("+401", actionableDialQuery("+401"))
        assertEquals("0723123456", actionableDialQuery("0723123456"))
    }

    @Test
    fun `accepts star hash pause and wait dial strings`() {
        assertEquals("*222", actionableDialQuery("*222"))
        assertEquals("#123#", actionableDialQuery("#123#"))
        assertEquals("12,3", actionableDialQuery("12,3"))
        assertEquals("12;3", actionableDialQuery("12;3"))
    }

    @Test
    fun `requires three actual digits regardless of dial symbols`() {
        assertNull(actionableDialQuery("12"))
        assertNull(actionableDialQuery("*12#"))
        assertNull(actionableDialQuery("+#*"))
    }

    @Test
    fun `rejects letters misplaced pluses and formatting characters`() {
        assertNull(actionableDialQuery("12a3"))
        assertNull(actionableDialQuery("12+3"))
        assertNull(actionableDialQuery("++123"))
        assertNull(actionableDialQuery("12 3"))
        assertNull(actionableDialQuery("12-3"))
    }
}
