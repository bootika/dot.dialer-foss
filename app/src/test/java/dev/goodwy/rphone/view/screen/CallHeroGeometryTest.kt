package dev.goodwy.rphone.view.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallHeroGeometryTest {

    @Test
    fun `reference phone uses a balanced rhythm across the entire call screen`() {
        val spacing = callScreenSpacing(availableHeightDp = 780, hasOtherCall = false)

        assertEquals(
            CallScreenSpacing(
                topSpacingDp = 35,
                nameToStatusGapDp = 11,
                connectionToTimerGapDp = 11,
                timerSlotHeightDp = 35,
                avatarTopGapDp = 13,
                actionRowsGapDp = 16,
                hangupGapDp = 27,
                controlsBottomPaddingDp = 8,
            ),
            spacing,
        )
        assertEquals(92, spacing.reservedHeaderSpacingDp)
    }

    @Test
    fun `compact screens use minimum safe spacing`() {
        val spacing = callScreenSpacing(availableHeightDp = 560, hasOtherCall = false)

        assertEquals(CallScreenSpacing(20, 8, 8, 32, 8, 10, 18, 6), spacing)
    }

    @Test
    fun `tall screens stop growing after the comfortable maximum`() {
        val spacing = callScreenSpacing(availableHeightDp = 920, hasOtherCall = false)

        assertEquals(CallScreenSpacing(40, 12, 12, 36, 14, 18, 30, 8), spacing)
    }

    @Test
    fun `secondary call keeps its identity area compact without compressing controls`() {
        val spacing = callScreenSpacing(availableHeightDp = 780, hasOtherCall = true)

        assertEquals(
            CallScreenSpacing(20, 11, 11, 35, 9, 16, 27, 8),
            spacing,
        )
    }

    @Test
    fun `avatar is clamped to the measured middle zone with control clearance`() {
        assertEquals(154, callAvatarSizeForMiddleZone(252, availableHeightDp = 240))
        assertEquals(154, callAvatarSizeForMiddleZone(164, availableHeightDp = 240))
        assertEquals(0, callAvatarSizeForMiddleZone(164, availableHeightDp = 20))
        assertEquals(192, callAvatarSizeForMiddleZone(252, availableHeightDp = 300))
    }

    @Test fun `circular portrait uses measured height without a preferred size or screen width ratio`() {
        assertEquals(304, callCircularAvatarSizeDp(360, 400))
        assertEquals(304, callCircularAvatarSizeDp(412, 400))
        assertEquals(240, callCircularAvatarSizeDp(240, 400))
        assertEquals(91, callCircularAvatarSizeDp(360, 120))
        assertEquals(360, callCircularAvatarSizeDp(360, 520))
        assertEquals(0, callCircularAvatarSizeDp(0, 400))
        assertEquals(0, callCircularAvatarSizeDp(360, -1))
    }

    @Test fun `all circular call states derive diameter from one global clearance token`() {
        assertEquals(.12f, CALL_CIRCULAR_AVATAR_CLEARANCE_FRACTION, 0f)
        assertEquals(.76f, CALL_CIRCULAR_AVATAR_MIDDLE_ZONE_FRACTION, .000001f)
        assertEquals(1f, 2f * CALL_CIRCULAR_AVATAR_CLEARANCE_FRACTION + CALL_CIRCULAR_AVATAR_MIDDLE_ZONE_FRACTION, .000001f)
    }

    @Test fun `centered circular portrait leaves twelve percent unless width limited`() {
        for (width in listOf(0, 120, 240, 360, 600)) {
            for (height in listOf(0, 1, 20, 119, 240, 399, 400, 600, 1000)) {
                val diameter = callCircularAvatarSizeDp(width, height)
                val clearance = (height - diameter) / 2f
                org.junit.Assert.assertTrue(clearance + .001f >= height * .12f)
                if (width >= height * .76f) {
                    // At most half a dp extra per end from integer diameter rounding.
                    org.junit.Assert.assertTrue(clearance <= height * .12f + .5f)
                    assertEquals(height * .12f, clearance, .5f)
                }
                org.junit.Assert.assertTrue(diameter <= width)
            }
        }
    }

    @Test
    fun `reference phone keeps the avatar clear of metadata and controls`() {
        assertEquals(
            CallAvatarGeometry(fieldSizeDp = 332, avatarSizeDp = 200),
            callAvatarGeometry(availableHeightDp = 780, screenWidthDp = 360),
        )
    }

    @Test
    fun `company row is absorbed by moving the complete identity block upward`() {
        assertEquals(0, callIdentityVerticalCompensationDp(hasSupplementalIdentity = false))
        assertEquals(
            CALL_SCREEN_COMPANY_ROW_HEIGHT_DP,
            callIdentityVerticalCompensationDp(hasSupplementalIdentity = true),
        )
    }

    @Test
    fun `company row is removed from the header footprint without negative height`() {
        assertEquals(100, callIdentityReportedHeight(measuredHeight = 120, compensation = 20))
        assertEquals(0, callIdentityReportedHeight(measuredHeight = 12, compensation = 20))
    }

    @Test
    fun `middle visual zone is centered between independent top and bottom anchors`() {
        val center = requireNotNull(callMiddleZoneCenter(start = 180f, end = 660f))

        assertEquals(420f, center, 0f)
        assertEquals(240f, center - 180f, 0f)
        assertEquals(240f, 660f - center, 0f)
    }

    @Test
    fun `middle visual zone rejects incomplete layout landmarks`() {
        assertNull(callMiddleZoneCenter(start = Float.NaN, end = 660f))
        assertNull(callMiddleZoneCenter(start = 660f, end = 180f))
    }
}
