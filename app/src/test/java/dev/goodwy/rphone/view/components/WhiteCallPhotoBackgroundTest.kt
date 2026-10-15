package dev.goodwy.rphone.view.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhiteCallPhotoBackgroundTest {
    @Test fun transparentAndSemitransparentPixelsAreCompositedWithoutChangingSource() = runBlocking {
        val source=Bitmap.createBitmap(3,1,Bitmap.Config.ARGB_8888)
        source.setPixel(1,0,Color.BLACK);source.setPixel(2,0,Color.argb(128,0,0,0))
        val result=WhiteCallPhotoBackground.transform(source,Size.ORIGINAL)
        assertEquals(Color.WHITE,result.getPixel(0,0));assertEquals(Color.BLACK,result.getPixel(1,0))
        assertEquals(255,Color.alpha(result.getPixel(2,0)))
        assertTrue(Color.red(result.getPixel(2,0)) in 126..128)
        assertEquals(0,Color.alpha(source.getPixel(0,0)));assertFalse(result.hasAlpha())
        result.recycle();source.recycle()
    }
    @Test fun opaquePhotographyKeepsExactPixels() = runBlocking {
        val image=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED);setHasAlpha(false) }
        assertSame(image,WhiteCallPhotoBackground.transform(image,Size.ORIGINAL))
        assertEquals(Color.RED,image.getPixel(0,0));image.recycle()
    }
}
