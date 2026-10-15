package dev.goodwy.rphone.view.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RillAvatarGeometryTest {
    @Test
    fun `initials keep the approved amount of breathing room`() {
        assertEquals(0.48f, AVATAR_LETTER_SCALE)
    }

    @Test
    fun `avatar size preference keeps current geometry as medium`() {
        assertEquals(40, contactAvatarSizeDp("small"))
        assertEquals(46, contactAvatarSizeDp("medium"))
        assertEquals(52, contactAvatarSizeDp("large"))
        assertEquals(46, contactAvatarSizeDp(null))
    }

    @Test
    fun `unknown callers use the one hollow modern glyph`() {
        assertEquals("DotUnknownCaller", DotUnknownCallerIcon.name)
        assertSame(DotUnknownCallerIcon, DotUnknownCallerIcon)
    }
}
