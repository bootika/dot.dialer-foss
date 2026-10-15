package dev.goodwy.rphone.view.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import android.net.Uri
import android.app.Activity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class ContactPhotoViewerTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()

    @Test fun `real photo dialog decodes source and launches photo chooser and save document`() {
        val source=File(rule.activity.cacheDir,"viewer-source.png")
        val bitmap=Bitmap.createBitmap(120,180,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(140,190,170)) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        var closed=false
        var document: Intent?=null
        val registry=object : ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode: Int,contract: ActivityResultContract<I,O>,input: I,options: ActivityOptionsCompat?) {
                document=contract.createIntent(rule.activity,input)
                dispatchResult(requestCode,Activity.RESULT_CANCELED,null)
            }
        }
        val owner=object : ActivityResultRegistryOwner { override val activityResultRegistry=registry }
        rule.setContent { CompositionLocalProvider(LocalContext provides rule.activity,LocalActivityResultRegistryOwner provides owner) {
            DotDialerTheme { ContactPhotoViewer(Uri.fromFile(source).toString(),"Test Alexandru Butica",{ closed=true }) }
        } }
        rule.waitUntil(10000) { !rule.onNodeWithTag("contactPhotoShare").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        rule.onNodeWithTag("contactPhotoShare").performClick()
        rule.waitUntil(10000) { !rule.onNodeWithTag("contactPhotoShare").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        if(File.separatorChar == '\\') {
            rule.waitUntil(10000) { rule.onAllNodesWithText("Could not load or export this photo").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Could not load or export this photo").assertExists()
            Assert.assertNull(shadowOf(rule.activity).nextStartedActivity)
        } else {
            val chooser=shadowOf(rule.activity).nextStartedActivity
            Assert.assertEquals(Intent.ACTION_CHOOSER,chooser.action)
            Assert.assertEquals(Intent.ACTION_SEND,chooser.getParcelableExtra(Intent.EXTRA_INTENT,Intent::class.java)?.action)
        }
        // The error snackbar overlays footer taps until its short timeout; then test the real Save callback.
        rule.mainClock.advanceTimeBy(6000)
        rule.onNodeWithTag("contactPhotoSave").assertIsEnabled().performClick()
        rule.waitForIdle()
        Assert.assertEquals(Intent.ACTION_CREATE_DOCUMENT,requireNotNull(document).action)
        Assert.assertEquals("image/png",requireNotNull(document).type)
        rule.onNodeWithContentDescription("Close").performClick()
        Assert.assertTrue(closed)
        Assert.assertTrue(source.length()>0)
    }
    @Test fun `shared viewer adapts themes and font scale with accessible share save close`() {
        val dark=mutableStateOf(false); val scale=mutableStateOf(1f); val ready=mutableStateOf(true)
        var shares=0; var saves=0; var closes=0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,scale.value)) {
                DotDialerTheme(systemDark=dark.value) {
                    ContactPhotoViewerContent("Test Alexandru Butica",ready.value,false,
                        { closes++ },{ shares++ },{ saves++ }) {
                        Image(ColorPainter(Color(0xFFB7D8CB)),"Contact photo",Modifier.fillMaxSize())
                    }
                }
            }
        }
        for(isDark in listOf(false,true)) for(font in listOf(1f,1.5f)) {
            rule.runOnIdle { dark.value=isDark; scale.value=font }
            rule.onNodeWithTag("contactPhotoShare").assertIsDisplayed().assertIsEnabled().performClick()
            rule.onNodeWithTag("contactPhotoSave").assertIsDisplayed().assertIsEnabled().performClick()
            rule.onNodeWithContentDescription("Close").assertIsDisplayed().performClick()
            val bitmap=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888)
            rule.runOnIdle { rule.activity.window.decorView.draw(Canvas(bitmap)) }
            val output=File(System.getProperty("user.dir"),"build/reports/visual/contact-photo-viewer").apply { mkdirs() }
            File(output,"viewer-${if(isDark) "dark" else "light"}-font$font.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
            }
        }
        Assert.assertEquals(4,shares); Assert.assertEquals(4,saves); Assert.assertEquals(4,closes)
        rule.runOnIdle { ready.value=false }
        rule.onNodeWithTag("contactPhotoShare").assertIsNotEnabled()
        rule.onNodeWithTag("contactPhotoSave").assertIsNotEnabled()
    }
}
