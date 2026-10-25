package dev.goodwy.rphone.view.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.theme.DotDialerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w400dp-h800dp-mdpi")
class FavoritesEditGestureTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private var editing by mutableStateOf(false)
    private var clicks=0
    private var menus=0
    private val orders=mutableListOf<List<String>>()
    private val dragging=mutableListOf<Boolean>()
    private fun render(contacts: List<Contact> = listOf(Contact("first",givenName="Alexandru"),Contact("second",givenName="Bogdan"))) {
        rule.setContent {CompositionLocalProvider(LocalRillAvatarAppearance provides RillAvatarAppearance(false,true,false,true,false,false)) {
            DotDialerTheme {IPhoneFavoritesRow(contacts,editing,{}, {}, {orders.add(it)}, {clicks++}, {menus++},0,
                onDraggingChange={dragging.add(it)},avatarSize=56.dp)}
        }}
    }
    private fun holdAvatar(letter: String="A") {
        val point=rule.onNodeWithText(letter,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.center
        rule.mainClock.autoAdvance=false
        rule.onRoot().performTouchInput {down(point)}
        rule.mainClock.advanceTimeBy(800)
    }
    @Test fun avatarLongPressInEditModeDragsAndSavesWithoutOpeningMenu() {
        render()
        rule.runOnIdle {editing=true}
        holdAvatar()
        rule.onRoot().performTouchInput {moveBy(Offset(90f,0f),delayMillis=100);up()}
        rule.runOnIdle {
            assertEquals(listOf(listOf("second","first")),orders)
            assertEquals(listOf(true,false),dragging)
            assertEquals(0,menus);assertEquals(0,clicks)
        }
    }
    @Test fun cancelledEditDragClearsDraggingWithoutSavingOrOpeningMenu() {
        editing=true;render();holdAvatar()
        rule.onRoot().performTouchInput {cancel()}
        rule.runOnIdle {
            assertTrue(orders.isEmpty());assertEquals(listOf(true,false),dragging)
            assertEquals(0,menus);assertEquals(0,clicks)
        }
    }
    @Test fun leavingEditModeRestoresNormalAvatarTapAndContextMenu() {
        editing=true;render()
        rule.runOnIdle {editing=false}
        rule.onNodeWithText("A",useUnmergedTree=true).performTouchInput {click()}
        rule.runOnIdle {assertEquals(1,clicks)}
        holdAvatar()
        rule.onRoot().performTouchInput {up()}
        rule.runOnIdle {assertEquals(1,menus);assertEquals(1,clicks);assertTrue(orders.isEmpty());assertTrue(dragging.isEmpty())}
    }
    private fun avatarCenterX(letter: String): Float {
        val node=rule.onNodeWithText(letter,useUnmergedTree=true).fetchSemanticsNode()
        // Keep samples inside the clipped viewport, including the active edge-scroll zone.
        return node.boundsInRoot.center.x
    }
    private val longRail get()=listOf("Alexandru","Bogdan","Cristian","Dan","Emil","Florin","George","Horia","Ion","Lucian").mapIndexed {index,name->Contact("item$index",givenName=name)}
    @Test fun middleOfLongRailMovesOneNeighborAndStaysUnderStationaryFinger() {
        editing=true;render(longRail)
        val before=avatarCenterX("C")
        holdAvatar("C")
        rule.onRoot().performTouchInput {moveBy(Offset(80f,0f),delayMillis=100)}
        rule.mainClock.advanceTimeBy(600)
        val after=avatarCenterX("C")
        assertEquals(before+80f,after,4f)
        rule.onRoot().performTouchInput {up()}
        rule.runOnIdle {assertEquals(listOf("item0","item1","item3","item2","item4","item5","item6","item7","item8","item9"),orders.single());assertEquals(0,menus)}
    }
    @Test fun edgeScrollIsGradualAndCompensatesTheDraggedAvatar() {
        editing=true;render(longRail)
        val point=rule.onNodeWithText("C",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.center
        holdAvatar("C")
        rule.onRoot().performTouchInput {moveTo(Offset(360f,point.y),delayMillis=100)}
        rule.mainClock.advanceTimeBy(250)
        assertEquals(360f,avatarCenterX("C"),5f)
        rule.onRoot().performTouchInput {moveTo(Offset(250f,point.y),delayMillis=100)}
        rule.mainClock.advanceTimeBy(600)
        assertEquals(250f,avatarCenterX("C"),5f)
        rule.onRoot().performTouchInput {cancel()}
        rule.runOnIdle {assertTrue(orders.isEmpty());assertEquals(0,menus);assertEquals(false,dragging.last())}
    }}
