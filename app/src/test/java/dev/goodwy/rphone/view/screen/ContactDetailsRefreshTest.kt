package dev.goodwy.rphone.view.screen

import android.Manifest
import android.accounts.Account
import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.*
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactPhoneDetail
import dev.goodwy.rphone.modal.repository.CachingContactsRepository
import dev.goodwy.rphone.view.theme.DotDialerTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
class ContactDetailsRefreshTest {
    @Suppress("DEPRECATION")
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private lateinit var memory: DetailsContacts
    private lateinit var repository: IContactsRepository
    private lateinit var prefs: PreferenceManager
    private val starts=AtomicInteger()
    private val successes=AtomicInteger()
    private val decodeRelease=CompletableDeferred<Unit>()
    private var navigations=0
    private val requested=mutableStateOf<Pair<String?,String?>>("101" to null)
    private val navigator=Proxy.newProxyInstance(DestinationsNavigator::class.java.classLoader,
        arrayOf(DestinationsNavigator::class.java)) { proxy,method,args -> when(method.name) {
            "navigateUp" -> { navigations++;true }; "toString" -> "Test navigation"
            "equals" -> proxy===args?.firstOrNull();"hashCode" -> System.identityHashCode(proxy);else -> null
        } } as DestinationsNavigator
    @Before fun prepare() {
        val app=RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS)
        prefs=PreferenceManager(app)
        val photo=File(app.cacheDir,"contact-details-photo.png")
        Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }.let { bitmap ->
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
        memory=DetailsContacts(Contact(id="101",givenName="Test Alexandru Butica",photoUri=android.net.Uri.fromFile(photo).toString(),
            phoneNumbers=listOf("0700000011"),phoneDetails=listOf(ContactPhoneDetail(number="0700000011")),
            accountName="first@example.test",accountType="com.google"))
        repository=object : IContactsRepository by CachingContactsRepository(app,memory) {}
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(app).components {
            add(object:coil.intercept.Interceptor {
                override suspend fun intercept(chain:coil.intercept.Interceptor.Chain):coil.request.ImageResult {
                    decodeRelease.await();return chain.proceed(chain.request)
                }
            })
        }.eventListener(object:coil.EventListener {
            override fun onStart(request:coil.request.ImageRequest) { starts.incrementAndGet() }
            override fun onSuccess(request:coil.request.ImageRequest,result:coil.request.SuccessResult) { successes.incrementAndGet() }
        }).build())
        val logs=Proxy.newProxyInstance(ICallLogRepository::class.java.classLoader,arrayOf(ICallLogRepository::class.java)) {
            _,method,_ -> if(method.name in listOf("getCallLogs","getCompleteCallLogs")) emptyList<Any>() else Unit
        } as ICallLogRepository
        startKoin { modules(module {
            single { prefs };single { ContactsViewModel(app,repository,prefs) }
            single { CallLogViewModel(app,logs,app.contentResolver,prefs,CallNotificationManager(app,prefs),repository) }
        }) }
    }
    @After fun close() {
        stopKoin();coil.Coil.setImageLoader(coil.ImageLoader.Builder(RuntimeEnvironment.getApplication()).build())
    }
    private fun show(dark:Boolean=false,fontScale:Float=1f) {
        val visual=object:ContextWrapper(rule.activity) {
            override fun getDisplay():Display=rule.activity.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        }
        rule.setContent { CompositionLocalProvider(LocalContext provides visual,LocalDensity provides Density(1f,fontScale)) { DotDialerTheme(systemDark=dark) {
            val target=requested.value
            ContactDetailsScreen(contactId=target.first,phoneNumber=target.second,navController=rememberNavController(),navigator=navigator)
        } } }
        rule.waitUntil(10000) { rule.waitForIdle();rule.onAllNodesWithText("Test Alexandru Butica").fetchSemanticsNodes().isNotEmpty() }
        decodeRelease.complete(Unit)
        rule.waitUntil(10000) { rule.waitForIdle();successes.get()>0 }
    }
    private fun refreshHeld():CompletableDeferred<Unit> {
        val release=CompletableDeferred<Unit>();val before=memory.reads.get()
        rule.runOnIdle { memory.release=release;repository.invalidateAndNotify() }
        rule.waitUntil(10000) { rule.waitForIdle();memory.reads.get()>before }
        return release
    }
    private fun capture(id:String) {
        val root=File(requireNotNull(System.getProperty("user.dir")),"build/reports/visual/contact-details-refresh").apply { mkdirs() }
        rule.runOnIdle {
            val view=rule.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(root,"$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        File(root,"$id-semantics.txt").writeText(rule.onRoot(useUnmergedTree=true).printToString())
    }
    @Test fun unchangedProviderEventsRetainVisibleContactAndItsDecodedAvatar() = checkUnchanged()
    @Test fun darkLargeFontRefreshKeepsItsBody() = checkUnchanged(dark=true,fontScale=1.5f)
    @Test @Config(qualifiers="w800dp-h360dp-land-mdpi")
    fun landscapeRefreshKeepsItsBody() = checkUnchanged()
    private fun checkUnchanged(dark:Boolean=false,fontScale:Float=1f) {
        show(dark,fontScale)
        val nameIds=rule.onAllNodesWithText("Test Alexandru Butica").fetchSemanticsNodes().map { it.id }.toSet()
        val photoId=rule.onNodeWithContentDescription("Test Alexandru Butica").fetchSemanticsNode().id
        val initialStarts=starts.get()
        capture("initial-$dark-$fontScale-${rule.activity.resources.configuration.orientation}")
        repeat(3) { index ->
            val release=refreshHeld()
            capture("refresh-pending-$dark-$fontScale-${rule.activity.resources.configuration.orientation}-$index")
            rule.onNodeWithContentDescription("Test Alexandru Butica").assertIsDisplayed()
            assertEquals(nameIds,rule.onAllNodesWithText("Test Alexandru Butica").fetchSemanticsNodes().map { it.id }.toSet())
            assertEquals(photoId,rule.onNodeWithContentDescription("Test Alexandru Butica").fetchSemanticsNode().id)
            assertEquals(initialStarts,starts.get())
            release.complete(Unit);rule.waitForIdle();memory.release=null
            rule.mainClock.advanceTimeBy(2000);rule.waitForIdle()
        }
        capture("after-repeated-refresh-$dark-$fontScale-${rule.activity.resources.configuration.orientation}")
        assertEquals(0,navigations)
    }
    @Test fun realNameNumberPhotoChangesPublishAndDeletedContactNavigatesBack() {
        show()
        val release=refreshHeld()
        rule.onNode(hasText("Test Alexandru Butica") and SemanticsMatcher("Detail body") { it.positionInRoot.y>100f }).assertIsDisplayed()
        val newPhoto=File(rule.activity.cacheDir,"contact-details-new-photo.png")
        Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }.let { bitmap ->
            newPhoto.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
        memory.entries["101"]=memory.entries.getValue("101").copy(givenName="Test Studio",photoUri=android.net.Uri.fromFile(newPhoto).toString(),
            phoneNumbers=listOf("0700000022"),phoneDetails=listOf(ContactPhoneDetail(number="0700000022")),isFavorite=true)
        release.complete(Unit);memory.release=null
        rule.waitUntil(10000) { rule.waitForIdle();rule.onAllNodesWithText("Test Studio").fetchSemanticsNodes().isNotEmpty() && successes.get()>=2 }
        rule.onNodeWithText("0700000022",substring=true).assertExists()
        rule.onAllNodesWithText("Test Alexandru Butica").assertCountEquals(0)
        capture("updated-fields-and-photo")
        rule.runOnIdle { memory.entries.remove("101");repository.invalidateAndNotify() }
        rule.waitUntil(10000) { rule.waitForIdle();navigations==1 }
        rule.onAllNodesWithText("Test Studio").assertCountEquals(0)
    }
    @Test fun newRouteClearsOldIdentityAndUnsavedFallbackStaysVisibleOnRefreshAndResume() {
        show()
        val release=CompletableDeferred<Unit>();val before=memory.reads.get()
        memory.entries["202"]=Contact(id="202",givenName="Test Studio")
        rule.runOnIdle { memory.release=release;requested.value="202" to null }
        rule.waitUntil(10000) { rule.waitForIdle();memory.reads.get()>before }
        rule.onAllNodesWithText("Test Alexandru Butica").assertCountEquals(0)
        rule.onAllNodesWithText("Test Studio").assertCountEquals(0)
        val unknown="+40700000033";val second=memory.reads.get()
        rule.runOnIdle { requested.value=null to unknown }
        rule.waitUntil(10000) { rule.waitForIdle();memory.reads.get()>second }
        release.complete(Unit);memory.release=null
        rule.waitUntil(10000) { rule.waitForIdle();rule.onAllNodes(hasText(unknown,substring=true) and SemanticsMatcher("Detail body") { it.positionInRoot.y>100f }).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Test Studio").assertCountEquals(0)
        val unknownId=rule.onNode(hasText(unknown,substring=true) and SemanticsMatcher("Detail header") { it.positionInRoot.y in 100f..400f }).fetchSemanticsNode().id
        val again=refreshHeld()
        assertEquals(unknownId,rule.onNode(hasText(unknown,substring=true) and SemanticsMatcher("Detail header") { it.positionInRoot.y in 100f..400f }).fetchSemanticsNode().id)
        again.complete(Unit);memory.release=null;rule.waitForIdle()
        val resumed=CompletableDeferred<Unit>();val resumeBefore=memory.reads.get();memory.release=resumed
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.waitUntil(10000) { rule.waitForIdle();memory.reads.get()>resumeBefore }
        assertEquals(unknownId,rule.onNode(hasText(unknown,substring=true) and SemanticsMatcher("Detail header") { it.positionInRoot.y in 100f..400f }).fetchSemanticsNode().id)
        resumed.complete(Unit);memory.release=null;rule.waitForIdle();capture("unsaved-after-resume")
        assertEquals(0,navigations)
    }
    @Test fun scrollAnchorSurvivesAProviderRefresh() {
        show()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Call screen background"))
        rule.onNodeWithText("Call screen background").assertIsDisplayed()
        val before=rule.onNodeWithText("Call screen background").fetchSemanticsNode()
        val release=refreshHeld()
        val after=rule.onNodeWithText("Call screen background").fetchSemanticsNode()
        assertEquals(before.id,after.id);assertEquals(before.boundsInRoot,after.boundsInRoot)
        release.complete(Unit);memory.release=null;rule.waitForIdle()
        rule.onNodeWithText("Call screen background").assertIsDisplayed();capture("scrolled-refresh")
    }
    private class DetailsContacts(initial:Contact):IContactsRepository by emptyRepository() {
        val entries=mutableMapOf(initial.id to initial)
        val reads=AtomicInteger()
        @Volatile var release:CompletableDeferred<Unit>?=null
        override suspend fun getContactById(contactId:String):Contact? {
            reads.incrementAndGet();release?.await();return entries[contactId]?.copy()
        }
        override suspend fun getContactByNumber(number:String):Contact? {
            reads.incrementAndGet();release?.await();return entries.values.firstOrNull { number in it.phoneNumbers }?.copy()
        }
        override suspend fun getContacts(includePrivate:Boolean,includeHidden:Boolean)=entries.values.toList()
        companion object {
            private fun emptyRepository()=Proxy.newProxyInstance(IContactsRepository::class.java.classLoader,
                arrayOf(IContactsRepository::class.java)) { _,method,_ -> when(method.name) {
                "getChanges" -> kotlinx.coroutines.flow.emptyFlow<Unit>()

                    "getAvailableAccounts","getAvailableAccountsForMoving" -> listOf(Account("first@example.test","com.google"))
                    "getContactsFull","getContactGroups","getRawContactsForContact","getRawContactsForAccount","getWritableRawContacts" -> emptyList<Any>()
                    "getRawContactData","getPhotoBytes" -> null
                    "toString" -> "Test contacts repository"
                    else -> Unit
                } } as IContactsRepository
        }
    }
}
