package dev.goodwy.rphone.view.components

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.theme.DotDialerTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File

@RunWith(AndroidJUnit4::class)
class FavoritesDragDeviceTest {
    private fun find(node: SemanticsNode, text: String): SemanticsNode? =
        if(node.config.getOrElse(SemanticsProperties.Text) {emptyList()}.any {it.text==text})node
        else node.children.firstNotNullOfOrNull {find(it,text)}
    private fun composeView(view: View): View? = when {
        view.javaClass.name.endsWith("AndroidComposeView")->view
        view is ViewGroup->(0 until view.childCount).firstNotNullOfOrNull {composeView(view.getChildAt(it))}
        else->null
    }
    @Test fun longRailStaysUnderFingerAtMiddleAndEdgeWithoutWritingUserFavorites() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName=="dot.dialer.test")
        val contacts=listOf("Alexandru","Bogdan","Cristian","Dan","Emil","Florin","George","Horia","Ion","Lucian").mapIndexed {i,name->Contact("fixture$i",givenName=name)}
        val orders=mutableListOf<List<String>>()
        var menus=0
        val metrics=JSONObject()
        lateinit var screen: ComponentActivity
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario->
            scenario.onActivity {activity->screen=activity;activity.setContent {
                DotDialerTheme {CompositionLocalProvider(LocalRillAvatarAppearance provides RillAvatarAppearance(false,true,false,true,false,false)) {
                    Column(Modifier.fillMaxSize().padding(top=100.dp)) {
                        IPhoneFavoritesRow(contacts,true,{}, {}, {orders.add(it)}, {error("Unexpected call")}, {menus++},0,avatarSize=56.dp)
                    }
                }}
            }}
            fun center(text: String): Offset {
                var result=Offset.Zero
                instrumentation.runOnMainSync {
                    val activity=screen
                    val view=checkNotNull(composeView(activity.window.decorView))
                    val root=(view.javaClass.methods.first {it.name.startsWith("getSemanticsOwner")}.invoke(view) as SemanticsOwner).rootSemanticsNode
                    val node=checkNotNull(find(root,text))
                    val origin=IntArray(2).also {view.getLocationOnScreen(it)}
                    result=node.boundsInRoot.center+Offset(origin[0].toFloat(),origin[1].toFloat())
                }
                return result
            }
            SystemClock.sleep(800)
            val density=context.resources.displayMetrics.density
            val start=center("C")
            var downTime=SystemClock.uptimeMillis()
            fun send(action: Int, point: Offset) {
                val event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,point.x,point.y,0)
                try {
                    event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                    val accepted=instrumentation.uiAutomation.injectInputEvent(event,false)
                    if(action!=MotionEvent.ACTION_CANCEL)assertTrue(accepted)
                }finally {event.recycle()}
                SystemClock.sleep(60)
            }
            send(MotionEvent.ACTION_DOWN,start)
            try {
                SystemClock.sleep(750)
                val middle=start+Offset(70*density,0f)
                send(MotionEvent.ACTION_MOVE,middle)
                SystemClock.sleep(650)
                val atMiddle=center("C")
                metrics.put("middleErrorPx",atMiddle.x-middle.x)
                assertEquals(middle.x,atMiddle.x,5*density)
                val edge=Offset(context.resources.displayMetrics.widthPixels-30*density,start.y)
                send(MotionEvent.ACTION_MOVE,edge)
                SystemClock.sleep(300)
                val atEdge=center("C")
                metrics.put("edgeErrorPx",atEdge.x-edge.x)
                assertEquals(edge.x,atEdge.x,5*density)
                send(MotionEvent.ACTION_MOVE,middle)
                SystemClock.sleep(650)
                assertEquals(middle.x,center("C").x,5*density)
            } finally {send(MotionEvent.ACTION_CANCEL,start)}
            SystemClock.sleep(300)
            assertTrue(orders.isEmpty());assertEquals(0,menus)
            metrics.put("noSave",true).put("noContextMenu",true)
            val output=File(context.cacheDir,"favorites-drag-device").apply {mkdirs()}
            instrumentation.uiAutomation.takeScreenshot()?.let {bitmap->File(output,"cancelled-restored.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
            val releaseStart=center("C")
            downTime=SystemClock.uptimeMillis()
            send(MotionEvent.ACTION_DOWN,releaseStart)
            SystemClock.sleep(750)
            val destination=releaseStart+Offset(70*density,0f)
            send(MotionEvent.ACTION_MOVE,destination)
            send(MotionEvent.ACTION_UP,destination)
            SystemClock.sleep(250)
            assertEquals(1,orders.size)
            assertEquals(10,orders.single().distinct().size)
            assertNotEquals(contacts.map {it.id},orders.single())
            assertEquals(0,menus)
            metrics.put("releaseSavesOnce",true).put("savedIdsUnique",true)
            File(output,"result.json").writeText(metrics.toString(2))
        }
    }
}
