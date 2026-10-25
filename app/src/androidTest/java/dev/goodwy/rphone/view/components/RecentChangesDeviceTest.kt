package dev.goodwy.rphone.view.components

import android.content.ComponentName
import android.os.SystemClock
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.goodwy.rphone.view.screen.*
import dev.goodwy.rphone.view.screen.settings.*
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.github.bootika.dotdialer.core.license.AppPlan
import dev.goodwy.rphone.view.theme.DotDialerTheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real GPU and touchscreen; isolated callbacks, no call/contact/settings writes. */
class RecentChangesDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private lateinit var screen: ComponentActivity
    private fun view(root: View): View?=when {
        root.javaClass.name.endsWith("AndroidComposeView")->root
        root is ViewGroup->(0 until root.childCount).firstNotNullOfOrNull {view(root.getChildAt(it))}
        else->null
    }
    private fun node(value: String,tag: Boolean=false): androidx.compose.ui.geometry.Rect? {
        var result: androidx.compose.ui.geometry.Rect?=null
        instrumentation.runOnMainSync {
            val v=checkNotNull(view(screen.window.decorView))
            val owner=v.javaClass.methods.first {it.name.startsWith("getSemanticsOwner")}.invoke(v) as SemanticsOwner
            val root=owner.rootSemanticsNode
            fun find(n: SemanticsNode): SemanticsNode? {
                val matches=if(tag)n.config.getOrElse(SemanticsProperties.TestTag){""}==value
                    else n.config.getOrElse(SemanticsProperties.Text){emptyList()}.any {it.text==value}
                return if(matches)n else n.children.firstNotNullOfOrNull(::find)
            }
            val origin=IntArray(2).also {v.getLocationOnScreen(it)}
            result=(find(root) ?: find(owner.unmergedRootSemanticsNode))?.boundsInRoot?.translate(Offset(origin[0].toFloat(),origin[1].toFloat()))
        }
        return result
    }
    private var downTime=0L
    private fun touch(action: Int,p: Offset) {
        if(action==MotionEvent.ACTION_DOWN)downTime=SystemClock.uptimeMillis()
        val event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,p.x,p.y,0)
        try {event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;assertTrue(instrumentation.uiAutomation.injectInputEvent(event,false))}
        finally {event.recycle()}
        SystemClock.sleep(70)
    }
    private fun capture(name: String): android.graphics.Bitmap {
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir=File(instrumentation.targetContext.cacheDir,"recent-device").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        return bitmap
    }
    @Test fun guideWelcomeNextBackFinishAndMutedFreeUpgradeCallback() {
        var finished=0;var upgrades=0
        var free by mutableStateOf(false)
        val prefs=PreferenceManager(instrumentation.targetContext,featureAllowed={false},onPlanRequired={upgrades++})
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario->
            scenario.onActivity {activity->screen=activity;activity.setContent {DotDialerTheme {
                if(free) Surface(Modifier.fillMaxSize()) {Column(Modifier.padding(top=100.dp)) {CallMessageSettings(prefs)}}
                else PlanGuideSequenceContent(listOf(AppPlan.PLUS,AppPlan.PREMIUM),{finished++},{},{},welcomePlan=AppPlan.PREMIUM)
            }}}
            SystemClock.sleep(700)
            fun tapTag(tag: String) {val p=checkNotNull(node(tag,true)).center;touch(MotionEvent.ACTION_DOWN,p);touch(MotionEvent.ACTION_UP,p);SystemClock.sleep(300)}
            assertNotNull(node("plan-activation-welcome",true));capture("activation-welcome").recycle()
            tapTag("plan-guide-next");assertNotNull(node("plan-guide-PLUS",true))
            tapTag("plan-guide-next");assertNotNull(node("plan-guide-PREMIUM",true))
            tapTag("plan-guide-back");assertNotNull(node("plan-guide-PLUS",true))
            tapTag("plan-guide-next");capture("complete-guide").recycle()
            tapTag("plan-guide-next");assertEquals(1,finished)
            instrumentation.runOnMainSync {free=true};SystemClock.sleep(500)
            val label=instrumentation.targetContext.getString(dev.goodwy.rphone.R.string.call_message_channel)
            val p=checkNotNull(node(label)).center;capture("free-muted-message-setting").recycle()
            touch(MotionEvent.ACTION_DOWN,p);touch(MotionEvent.ACTION_UP,p);SystemClock.sleep(200)
            assertEquals(1,upgrades)
        }
    }
    @Test fun quickTrayReleaseCancelsSlideSelectsAndTapToggles() {
        var actions=0;var calls=0;var menus=0
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario->
            scenario.onActivity {activity->screen=activity;activity.setContent {DotDialerTheme {Surface {
                Column(Modifier.fillMaxSize().padding(top=100.dp)) {
                    HomeResultRow("Test Alexandru Butica","0700000101",null,"0700000101",
                        swipeEnabled=false,swipeRightAction=0,swipeLeftAction=0,haptics=false,
                        hapticsStrength="light",hapticsIntensity=.5f,onAction={error("Unexpected swipe")},
                        onAvatarClick={},onClick={calls++},onLongClick={menus++},isLastInSection=true,
                        contactNameSize=18,avatarSize=46,quickActionsEnabled=true,quickActionsOnTap=true,
                        onQuickAction={assertEquals(3,it);actions++})
                }
            }}}}
            SystemClock.sleep(800)
            val p=checkNotNull(node("Test Alexandru Butica")).center
            touch(MotionEvent.ACTION_DOWN,p);SystemClock.sleep(750)
            assertNotNull(node("Copy"));capture("quick-hold").recycle()
            touch(MotionEvent.ACTION_UP,p);SystemClock.sleep(200)
            assertNull(node("Copy"));assertEquals(0,actions)
            touch(MotionEvent.ACTION_DOWN,p);SystemClock.sleep(750)
            val copy=checkNotNull(node("Copy")).center
            touch(MotionEvent.ACTION_MOVE,copy);SystemClock.sleep(200);capture("quick-hover").recycle()
            touch(MotionEvent.ACTION_UP,copy);SystemClock.sleep(200)
            assertEquals(1,actions);assertNull(node("Copy"))
            repeat(2) {index->
                touch(MotionEvent.ACTION_DOWN,p);touch(MotionEvent.ACTION_UP,p);SystemClock.sleep(250)
                assertEquals(index==0,node("Copy")!=null)
            }
            touch(MotionEvent.ACTION_DOWN,p);touch(MotionEvent.ACTION_UP,p);SystemClock.sleep(200)
            val click=checkNotNull(node("Copy")).center
            touch(MotionEvent.ACTION_DOWN,click);touch(MotionEvent.ACTION_UP,click);SystemClock.sleep(200)
            assertEquals(2,actions);assertEquals(0,calls);assertEquals(0,menus)
        }
    }
    @Test fun shieldWhiteOpacityAndBoundsAcrossBothThemesAndCallPhases() {
        var dark by mutableStateOf(false);var connecting by mutableStateOf(false);var clicks=0
        val account=PhoneAccount.builder(PhoneAccountHandle(ComponentName("dot.dialer.test","FixtureAccount"),"fixture"),"SIM 1").build()
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario->
            scenario.onActivity {activity->screen=activity;activity.setContent {DotDialerTheme(systemDark=dark) {
                Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center) {
                    CallConnectionStatusRow(CallConnectionInfo(CallTransportKind.Cellular,VoiceQualityTier.HdPlus,null,null,null),
                        "00:05",{clicks++},Modifier.width(312.dp),account=account,connecting=connecting)
                }
            }}}
            for(theme in listOf(false,true))for(phase in listOf(false,true)) {
                instrumentation.runOnMainSync {dark=theme;connecting=phase}
                SystemClock.sleep(700)
                val target=checkNotNull(node("callConnectionTouchTarget",true))
                val pill=checkNotNull(node("callConnectionShield",true))
                check(target.width>0 && target.height>0) {"Invalid touch bounds $target, pill $pill"}
                val x=pill.center.x.toInt();val y=pill.top.toInt()+3
                val idle=capture("shield-idle-$theme-$phase")
                touch(MotionEvent.ACTION_DOWN,target.center);SystemClock.sleep(300)
                val pressed=capture("shield-pressed-$theme-$phase")
                assertEquals(pill,node("callConnectionShield",true))
                assertEquals((if(theme)51 else 128).toDouble(),android.graphics.Color.red(idle.getPixel(x,y)).toDouble(),3.0)
                assertEquals((if(theme)77 else 230).toDouble(),android.graphics.Color.red(pressed.getPixel(x,y)).toDouble(),3.0)
                touch(MotionEvent.ACTION_CANCEL,target.center);SystemClock.sleep(350)
                val restored=capture("shield-restored-$theme-$phase")
                assertEquals(idle.getPixel(x,y),restored.getPixel(x,y));assertEquals(0,clicks)
                touch(MotionEvent.ACTION_DOWN,target.center);touch(MotionEvent.ACTION_UP,target.center)
                SystemClock.sleep(250);assertEquals(1,clicks);clicks=0
                idle.recycle();pressed.recycle();restored.recycle()
            }
        }
    }
}
