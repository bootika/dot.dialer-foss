package dev.goodwy.rphone.view.screen

import org.junit.Assert.*
import org.junit.Test


class CustomCallBackgroundTest {
    @Test fun customImageIsExclusiveToVisibleCircularAvatar() {
        for(full in listOf(false,true)) for(visible in listOf(false,true)) for(conference in listOf(false,true)) {
            assertEquals(!full && visible && !conference,customCallBackgroundAllowed(full,visible,conference))
        }
    }
    @Test fun backgroundUsesFixedFiftyPercentGlassValue() {
        assertEquals(50, CUSTOM_CALL_BACKGROUND_GLASS_STRENGTH)
        assertEquals(.675f, customCallGlassTintAlpha(), .0001f)
    }
}