package dev.goodwy.rphone.controller.util

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhatsAppAvatarShapeTest {
    @Test fun adaptiveSystemMaskIsNotBurnedIntoOriginalSquarePhoto() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val square=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        square.setPixel(0,0,Color.RED); square.setPixel(191,191,Color.BLUE)
        val adaptive=AdaptiveIconDrawable(ColorDrawable(Color.WHITE),BitmapDrawable(app.resources,square))
        val result=requireNotNull(WhatsAppNotificationAvatarExtractor.drawableToBitmap(adaptive))
        assertSame(square,result); assertEquals(192,result.width); assertEquals(192,result.height)
        assertEquals(Color.RED,result.getPixel(0,0)); assertEquals(Color.BLUE,result.getPixel(191,191))
        assertEquals(Color.GREEN,result.getPixel(0,96)); square.recycle()
    }
    @Test fun alreadyTransparentSourceIsPreservedWithoutInventingMissingCorners() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val source=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
        source.setPixel(96,96,Color.BLACK)
        val result=requireNotNull(WhatsAppNotificationAvatarExtractor.drawableToBitmap(BitmapDrawable(app.resources,source)))
        assertSame(source,result); assertEquals(0,Color.alpha(result.getPixel(0,0)))
        assertEquals(Color.BLACK,result.getPixel(96,96)); source.recycle()
    }
}
