package dev.goodwy.rphone.view.screen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.view.theme.DotDialerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class CustomCallBackgroundRenderingTest {
    @Test fun transparentFullPhotoArtworkHasWhiteUnderlayAcrossThemes() {
        val photo=Bitmap.createBitmap(90,90,Bitmap.Config.ARGB_8888)
        Canvas(photo).drawRect(30f,40f,60f,50f,Paint().apply { color=AndroidColor.BLACK })
        val file=File(RuntimeEnvironment.getApplication().filesDir,"transparent-logo.png")
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG,100,it) };photo.recycle()
        var dark by mutableStateOf(false)
        rule.setContent { DotDialerTheme(systemDark=dark) { Box(Modifier.fillMaxSize()) { ExpressiveBackground(
            photoUri=Uri.fromFile(file).toString(),liquidGlassBackdrop=rememberLayerBackdrop(),hideGradient=false,
            fullPhoto=true,ambientDroplets=AmbientDropletRenderConfig(enabled=false)) } } }
        waitForColor(.425f) { AndroidColor.red(it)<90 }
        for(theme in listOf(false,true)) {
            rule.runOnIdle { dark=theme };rule.mainClock.advanceTimeBy(700);rule.waitForIdle()
            rule.runOnIdle {
                val image=screenshot()
                val bounds=rule.onNodeWithTag("callFullPhotoSharp").fetchSemanticsNode().boundsInRoot
                val y=(bounds.top+bounds.height*.5f).toInt()
                val background=image.getPixel((bounds.left+bounds.width*.8f).toInt(),y)
                val mark=image.getPixel(bounds.center.x.toInt(),y)
                assertTrue(AndroidColor.red(background)>190)
                assertTrue(AndroidColor.red(background)-AndroidColor.red(mark)>140)
                image.recycle()
            }
            capture("white-logo-${if(theme) "dark" else "light"}")
        }
    }
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun fullPhotoAlwaysUsesContactPhotoAndIgnoresRectangularImageWithoutChangingPixels() {
        val contact=fixture("contact",false)
        var custom by mutableStateOf<String?>(fixture("background",true))
        rule.setContent { DotDialerTheme(systemDark=false) { ExpressiveBackground(
            photoUri=contact,backgroundUri=custom,liquidGlassBackdrop=rememberLayerBackdrop(),hideGradient=false,
            fullPhoto=true,ambientDroplets=AmbientDropletRenderConfig(enabled=false),
        ) } }
        waitForColor(.5f) { AndroidColor.red(it)>AndroidColor.green(it)+30 }
        rule.onNodeWithTag("callFullPhotoSharp").assertExists()
        rule.onNodeWithTag("callCustomBackgroundGlass").assertDoesNotExist()
        val before=pixels()
        capture("full-photo-contact-only")
        rule.runOnIdle { custom=null };rule.mainClock.advanceTimeBy(600);rule.waitForIdle()
        assertArrayEquals(before,pixels())
    }
    @Test fun circularCustomImageSpansViewportWithFixedTransparencyAcrossThemes() {
        val contact=fixture("contact",false);val custom=fixture("background",true)
        var strength by mutableIntStateOf(30)
        var dark by mutableStateOf(false)
        var visible by mutableStateOf(true)
        val software=object:View(rule.activity) { override fun isHardwareAccelerated()=false }
        rule.setContent { CompositionLocalProvider(LocalView provides software) {
            DotDialerTheme(systemDark=dark) { Box(Modifier.fillMaxSize()) { ExpressiveBackground(
                photoUri=contact,backgroundUri=custom,liquidGlassBackdrop=rememberLayerBackdrop(),hideGradient=false,
                circularAvatarVisible=visible,customGlassState=DialpadEffectState(true,strength,20f),
                ambientDroplets=AmbientDropletRenderConfig(enabled=false),
            ) } }
        } }
        waitForColor(.5f) { AndroidColor.green(it)>AndroidColor.red(it)+20 }
        val bounds=rule.onNodeWithTag("callCustomBackgroundImage").fetchSemanticsNode().boundsInRoot
        assertEquals(360f,bounds.width,1f);assertEquals(800f,bounds.height,1f)
        val themePixels=mutableMapOf<Boolean,IntArray>()
        for(theme in listOf(false,true)) for(percent in listOf(0,30,100)) {
            rule.runOnIdle { dark=theme;strength=percent };rule.mainClock.advanceTimeBy(700);rule.waitForIdle()
rule.runOnIdle { org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2)) }
            rule.mainClock.advanceTimeBy(1000);rule.waitForIdle()
            if(percent==0) themePixels[theme]=pixels() else assertArrayEquals(themePixels.getValue(theme),pixels())
            capture("fixed50-${if(theme) "dark" else "light"}-input$percent")
            if(percent>0) {
                val image=screenshot();val middle=image.getPixel(180,400);val lower=image.getPixel(180,560)
                assertTrue(AndroidColor.green(middle)>AndroidColor.red(middle)+4)
                assertTrue(AndroidColor.blue(lower)>AndroidColor.red(lower)+4)
                image.recycle()
            }
        }
        rule.runOnIdle { visible=false };rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithTag("callCustomBackgroundGlass").assertDoesNotExist()
    }
    @Test fun fullPhotoWithoutPortraitAndFailedCustomImageKeepGeneratedFallback() {
        var full by mutableStateOf(true)
        rule.setContent { DotDialerTheme { ExpressiveBackground(
            photoUri=null,backgroundUri="file:///missing-background.jpg",liquidGlassBackdrop=rememberLayerBackdrop(),hideGradient=false,
            fullPhoto=full,ambientDroplets=AmbientDropletRenderConfig(enabled=false),
        ) } }
        rule.onNodeWithTag("callCustomBackgroundGlass").assertDoesNotExist()
        rule.onNodeWithTag("callFullPhotoSharp").assertDoesNotExist()
        rule.runOnIdle { full=false }
        rule.waitUntil(10000) {
            rule.mainClock.advanceTimeBy(40)
            rule.onAllNodesWithTag("callCustomBackgroundGlass").fetchSemanticsNodes().isEmpty()
        }
        capture("failed-custom-fallback")
    }
    private fun waitForColor(y:Float,condition:(Int)->Boolean) = rule.waitUntil(10000) {
        rule.mainClock.advanceTimeBy(40)
        var ready=false
        rule.runOnIdle { val image=screenshot();ready=condition(image.getPixel(image.width/2,(image.height*y).toInt()));image.recycle() }
        ready
    }
    private fun screenshot():Bitmap {
        val view=rule.activity.window.decorView
        return Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    private fun pixels():IntArray { var result=intArrayOf();rule.runOnIdle {
        val image=screenshot();result=IntArray(image.width*image.height)
        image.getPixels(result,0,image.width,0,0,image.width,image.height);image.recycle()
    };return result }
    private fun capture(name:String) = rule.runOnIdle {
        val image=screenshot();val file=File("build/reports/visual/custom-call-d102/$name.png")
        requireNotNull(file.parentFile).mkdirs();file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
    private fun fixture(name:String,tall:Boolean):String {
        val image=Bitmap.createBitmap(90,if(tall) 200 else 90,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(AndroidColor.RED)
        if(tall) {
            val paint=Paint().apply { color=AndroidColor.GREEN };canvas.drawRect(0f,66f,90f,134f,paint)
            paint.color=AndroidColor.BLUE;canvas.drawRect(0f,134f,90f,200f,paint)
        }
        val file=File(RuntimeEnvironment.getApplication().filesDir,"$name.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        return Uri.fromFile(file).toString()
    }
}
