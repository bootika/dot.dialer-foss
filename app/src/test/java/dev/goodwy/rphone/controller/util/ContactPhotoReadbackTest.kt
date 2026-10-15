package dev.goodwy.rphone.controller.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactPhotoReadbackTest {
    @Test fun transparentNotificationAvatarMatchesWhiteAndBlackProviderJpeg() {
        for(size in listOf(192,512)) {
            val avatar=transparentAvatar(size)
            val source=encode(avatar,Bitmap.CompressFormat.PNG,100)
            for(background in listOf(Color.WHITE,Color.BLACK)) for(quality in listOf(80,95)) {
                val stored=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
                Canvas(stored).apply { drawColor(background);drawBitmap(avatar,0f,0f,null) }
                assertTrue("$size background=$background quality=$quality",
                    contactPhotoReadbackMatches(source,encode(stored,Bitmap.CompressFormat.JPEG,quality)))
                stored.recycle()
            }
            avatar.recycle()
        }
    }
    @Test fun transparentSourceDoesNotAuthorizeDifferentOpaqueContentCropOrUpscale() {
        val avatar=transparentAvatar(192)
        val source=encode(avatar,Bitmap.CompressFormat.PNG,100)
        assertFalse(contactPhotoReadbackMatches(source,image(192,192,Color.WHITE,Bitmap.CompressFormat.JPEG)))
        assertFalse(contactPhotoReadbackMatches(source,image(192,192,Color.MAGENTA,Bitmap.CompressFormat.JPEG)))
        assertFalse(contactPhotoReadbackMatches(source,image(192,144,Color.WHITE,Bitmap.CompressFormat.JPEG)))
        assertFalse(contactPhotoReadbackMatches(source,image(256,256,Color.WHITE,Bitmap.CompressFormat.JPEG)))
        avatar.recycle()
    }
    @Test fun semiTransparentContentIsCompositedRatherThanDiscarded() {
        val avatar=pattern(192,192).copy(Bitmap.Config.ARGB_8888,true)
        for(y in 0 until 192)for(x in 0 until 192)avatar.setPixel(x,y,(avatar.getPixel(x,y) and 0xffffff) or (128 shl 24))
        val source=encode(avatar,Bitmap.CompressFormat.PNG,100)
        val stored=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
        Canvas(stored).apply { drawColor(Color.WHITE);drawBitmap(avatar,0f,0f,null) }
        assertTrue(contactPhotoReadbackMatches(source,encode(stored,Bitmap.CompressFormat.JPEG,95)))
        assertFalse(contactPhotoReadbackMatches(source,image(192,192,Color.WHITE,Bitmap.CompressFormat.JPEG)))
        avatar.recycle();stored.recycle()
    }
    @Test fun differentImageCropAndUpscaleCannotBeConfirmed() {
        val approved = image(512,512,Color.BLUE)
        assertFalse(contactPhotoReadbackMatches(approved,image(320,320,Color.RED)))
        assertFalse(contactPhotoReadbackMatches(approved,image(320,240,Color.BLUE)))
        assertFalse(contactPhotoReadbackMatches(approved,image(640,640,Color.BLUE)))
        assertFalse(contactPhotoReadbackMatches(approved,byteArrayOf(1,2,3)))
    }
    @Test fun boundedWriterRejectsOversizeDecodedImagesAndExistingComparisonIsUnchanged() {
        val tooWide=image(4097,1,Color.BLUE)
        assertFalse(contactPhotoReadbackMatches(tooWide,image(720,1,Color.BLUE)))
        assertTrue(sameNullablePhoto(image(128,128,Color.BLUE),image(320,320,Color.BLUE)))
        assertFalse(sameNullablePhoto(image(128,128,Color.BLUE),image(128,128,Color.RED)))
    }
    @Test fun samePhotoAndProviderJpegKeepTheirContents() {
        val source=image(512,512,Color.BLUE)
        assertTrue(contactPhotoReadbackMatches(source,source))
        assertTrue(contactPhotoReadbackMatches(source,image(320,320,Color.BLUE,Bitmap.CompressFormat.JPEG)))
    }
    @Test fun highResolutionPortraitLandscapeAndSquareSurviveProviderResizeAndCompression() {
        for ((width,height) in listOf(1024 to 1024, 1536 to 1024, 1024 to 1536)) {
            val photo = pattern(width,height)
            val approved = encode(photo,Bitmap.CompressFormat.PNG,100)
            val storedBitmap = photo.scale(width/2,height/2,true)
            for (quality in listOf(80,90,95)) {
                assertTrue("${width}x${height} JPEG$quality",contactPhotoReadbackMatches(approved,
                    encode(storedBitmap,Bitmap.CompressFormat.JPEG,quality)))
            }
            storedBitmap.recycle(); photo.recycle()
        }
    }
    @Test fun equallySizedDetailedDifferentPhotosCannotPassReadback() {
        val approved = pattern(1024,1024)
        val other = Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        assertFalse(contactPhotoReadbackMatches(encode(approved,Bitmap.CompressFormat.PNG,100),
            encode(other,Bitmap.CompressFormat.JPEG,90)))
        approved.recycle(); other.recycle()
    }
    private fun pattern(width:Int,height:Int) = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) setPixel(x,y,
            Color.rgb(x*255/width,y*255/height,if ((x/(width/7)+y/(height/9))%2 == 0) 64 else 192))
    }
    private fun transparentAvatar(size:Int):Bitmap {
        val result=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val photo=pattern(size,size)
        Canvas(result).apply {
            drawCircle(size/2f,size/2f,size/2f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE })
            drawBitmap(photo,0f,0f,Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        }
        photo.recycle();return result
    }
    private fun encode(bitmap:Bitmap,format:Bitmap.CompressFormat,quality:Int) = ByteArrayOutputStream().use {
        assertTrue(bitmap.compress(format,quality,it)); it.toByteArray()
    }
    private fun image(width:Int,height:Int,color:Int,format:Bitmap.CompressFormat=Bitmap.CompressFormat.PNG):ByteArray {
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().use { bitmap.compress(format,90,it);bitmap.recycle();it.toByteArray() }
    }
}
