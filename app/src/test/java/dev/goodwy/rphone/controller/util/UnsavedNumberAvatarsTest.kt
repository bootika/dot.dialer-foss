package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnsavedNumberAvatarsTest {
    private fun context():Context {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val root=File(app.cacheDir,"unsaved-test-"+UUID.randomUUID()).apply { mkdirs() }
        return object:ContextWrapper(app) { override fun getNoBackupFilesDir()=root }
    }
    private fun image(size:Int,color:Int):ByteArray {
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).apply { eraseColor(color);setPixel(0,0,Color.BLACK) }
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray() }
    }
    @Test fun exactPhonesRemainIsolatedAndRawSquareBytesArePreserved() {
        val ctx=context();val store=UnsavedNumberAvatars(ctx,"first@example.test");val bytes=image(192,Color.RED)
        assertTrue(store.store("+40720033833",bytes,ContactPhotoAutomaticPolicy.Newest) { true })
        val uri=requireNotNull(store.resolve("+40720033833"));val file=File(android.net.Uri.parse(uri).path!!)
        assertArrayEquals(bytes,file.readBytes());assertNull(store.resolve("+40721111111"))
        assertNull(UnsavedNumberAvatars(ctx,"second@example.test").resolve("+40720033833"))
        val json=ctx.noBackupFilesDir.walk().filter { it.extension=="json" }.joinToString { it.readText() }
        assertFalse(json.contains("+40720033833"));assertFalse(json.contains("first@example.test"))
    }
    @Test fun manualNeverWritesNoLossProtectsCurrentAndExplicitLatestAllowsSmaller() {
        val store=UnsavedNumberAvatars(context(),"first@example.test")
        assertFalse(store.store("+40720033833",image(512,Color.RED),ContactPhotoAutomaticPolicy.Manual) { true })
        assertTrue(store.store("+40720033833",image(512,Color.RED),ContactPhotoAutomaticPolicy.Newest) { true })
        assertFalse(store.store("+40720033833",image(192,Color.BLUE),ContactPhotoAutomaticPolicy.Newest) { true })
        assertTrue(store.store("+40720033833",image(192,Color.BLUE),ContactPhotoAutomaticPolicy.Latest) { true })
        val file=File(android.net.Uri.parse(requireNotNull(store.resolve("+40720033833"))).path!!)
        assertEquals(192,requireNotNull(file.readBytes().toContactPhotoImageInfo()).width)
    }
    @Test fun explicitManualSelectionCanShrinkUnderManualPolicyAndBlocksAutomaticReplacement() {
        val store=UnsavedNumberAvatars(context(),"first@example.test")
        assertTrue(store.store("+40720033833",image(512,Color.RED),ContactPhotoAutomaticPolicy.Newest) { true })
        val small=image(192,Color.BLUE)
        assertTrue(store.store("+40720033833",small,ContactPhotoAutomaticPolicy.Manual,manual=true) { true })
        assertFalse(store.store("+40720033833",image(1024,Color.RED),ContactPhotoAutomaticPolicy.Latest) { true })
        val file=File(android.net.Uri.parse(store.resolve("+40720033833")!!).path!!)
        assertArrayEquals(small,file.readBytes())
    }
    @Test fun lateAccessChangeBlocksReplacementButAssignedAvatarSurvivesTrialDuration() {
        var now=1000L;val ctx=context();val store=UnsavedNumberAvatars(ctx,"first@example.test") { now }
        assertFalse(store.store("0720033833",image(192,Color.RED),ContactPhotoAutomaticPolicy.Newest) { true })
        assertFalse(store.store("*222",image(192,Color.RED),ContactPhotoAutomaticPolicy.Newest) { true })
        var checks=0
        assertFalse(store.store("+40720033833",image(192,Color.RED),ContactPhotoAutomaticPolicy.Newest) { ++checks==1 })
        assertNull(store.resolve("+40720033833"))
        assertTrue(store.store("+40720033833",image(192,Color.RED),ContactPhotoAutomaticPolicy.Newest) { true })
        now+=UnsavedNumberAvatars.RETENTION+1
        store.prune()
        assertNotNull(store.resolve("+40720033833"))
        UnsavedNumberAvatars.pruneExpired(ctx)
        assertNotNull(store.resolve("+40720033833"))
    }
}
