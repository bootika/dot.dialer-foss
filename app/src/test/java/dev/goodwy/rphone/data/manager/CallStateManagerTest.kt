package dev.goodwy.rphone.data.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateManagerTest {
    @Test
    fun `only calls with an address request caller metadata`() {
        assertFalse(shouldResolveCallerMetadata(""))
        assertFalse(shouldResolveCallerMetadata("   "))
        assertTrue(shouldResolveCallerMetadata("*222"))
        assertTrue(shouldResolveCallerMetadata("+40 723 000 000"))
    }
}
