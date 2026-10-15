package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanyLogoDiscoveryTest {
    private lateinit var context:Context
    private lateinit var provider:OptimizationContactsProvider
    private lateinit var contacts:WhatsAppAvatarContactStore
    private lateinit var profiles:ContactOptimizationProfiles
    private val account="first@example.test"
    private var searchCalls=0
    private var matches=CompanyNameSearchResult()
    private var verifies=true
    private var afterLoad: (() -> Unit)?=null
    private val urls=mutableListOf<String>()
    private val names=CompanyNameSearch { company, selected ->
        assertEquals("Test Industries",company);assertEquals(account,selected);searchCalls++;matches
    }
    private val source=object:CompanyLogoSource {
        override suspend fun load(website:String):ByteArray { urls+=website;afterLoad?.invoke();return image(Color.BLUE) }
        override suspend fun matchesCompany(website:String,company:String)=verifies
    }
    @Before fun setup() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS)
        val folder=File(app.cacheDir,"company-test-"+UUID.randomUUID()).apply { mkdirs() }
        context=object:ContextWrapper(app) { override fun getNoBackupFilesDir()=File(folder,"private").apply { mkdirs() } }
        provider=OptimizationContactsProvider(folder).also { it.install(context) };provider.emails.clear()
        contacts=WhatsAppAvatarContactStore(context);profiles=ContactOptimizationProfiles(context)
        profiles.update(account) { it.copy(business=true,businessDisclosed=true,businessDiscoveryDisclosed=true) }
    }
    private fun target()=requireNotNull(contacts.chooseContact(10,account))
    private fun discovery()=CompanyLogoDiscovery(context,contacts,source,names)
    private fun image(color:Int):ByteArray {
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).apply { eraseColor(color);setPixel(0,0,Color.BLACK) }
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray() }
    }
    private fun engine(reviews:WhatsAppAvatarReviewStore=WhatsAppAvatarReviewStore(context))=ContactsOptimizerEngine(context,profiles,contacts,reviews,
        { _,s -> s in setOf("Company logo","WhatsApp") },{ null },
        object:ContactAvatarSource { override val sourceName="Gravatar";override suspend fun load(email:String):ByteArray?=null },source,
        { selected,photo,smaller,valid -> contacts.writePhoto(selected,photo.bytes,smaller,valid) },
        { _,_ -> },companyNames=names)
    private fun captured(bytes:ByteArray=image(Color.BLUE),source:String="Company logo")=CapturedWhatsAppAvatar(bytes,512,512,null,"Test Industries",source=source)
    @Test fun domainRulesRejectSharedPrivateMalformedAndConflictingEvidence() {
        for(email in listOf("person@gmail.com","person@mail.gmail.com","person@outlook.com","person@icloud.com","person@127.0.0.1","invalid","a@b@site.example"))
            assertNull(email,CompanyLogoDomains.email(email))
        assertEquals("testindustries.example",CompanyLogoDomains.website("http://www.testindustries.example/contact"))
        assertEquals("testindustries.example",CompanyLogoDomains.email("person@testindustries.example"))
        assertNull(CompanyLogoDomains.website("https://linkedin.com/company/test"))
        val identity=CompanyLogoIdentity("Test Industries",listOf("https://first.example"),listOf("person@second.example"),true)
        assertEquals(listOf("first.example","second.example"),CompanyLogoDomains.direct(identity,true))
        assertEquals(listOf("first.example"),CompanyLogoDomains.direct(identity,false))
    }
    @Test fun directEmailWorksWithoutOrganizationAndNeverSendsAnEmailToSearch() = runBlocking {
        provider.emails[20]=listOf("person@testindustries.example")
        val found=discovery().find(target(),true)
        assertEquals("testindustries.example",found.candidates.single().domain);assertTrue(found.candidates.single().clearMatch)
        assertTrue(found.candidates.single().requiresDiscovery);assertEquals(0,searchCalls)
        assertEquals(listOf("https://testindustries.example/"),urls)
        assertTrue(discovery().find(target(),false).candidates.isEmpty())
    }
    @Test fun organizationNameSearchNeedsUniqueFullNameDomainAndIndependentPageBranding() = runBlocking {
        provider.companies[20]="Test Industries" to null
        matches=CompanyNameSearchResult(listOf(CompanyNameMatch("Test Industries","testindustries.example")))
        assertTrue(discovery().find(target(),true).candidates.single().clearMatch)
        verifies=false;assertFalse(discovery().find(target(),true).candidates.single().clearMatch)
        verifies=true;matches=CompanyNameSearchResult(listOf(CompanyNameMatch("Test Industries","different.example")))
        assertFalse(discovery().find(target(),true).candidates.single().clearMatch)
        matches=CompanyNameSearchResult(listOf(CompanyNameMatch("Test Industries","testindustries.example"),CompanyNameMatch("Test Industries","testindustries.other")))
        val found=discovery().find(target(),true);assertEquals(2,found.candidates.size);assertTrue(found.candidates.none { it.clearMatch })
    }
    @Test fun unavailableIsDistinctFromNoMatchAndPersonalDisplayNameIsNeverSearched() = runBlocking {
        assertTrue(discovery().find(target(),true).candidates.isEmpty());assertEquals(0,searchCalls)
        provider.companies[20]="Test Industries" to null;matches=CompanyNameSearchResult(unavailable=true)
        assertTrue(discovery().find(target(),true).searchUnavailable)
        matches=CompanyNameSearchResult();assertFalse(discovery().find(target(),true).searchUnavailable)
        assertEquals(2,searchCalls)
    }
    @Test fun personPhotoAndUnknownExistingCompanyPhotoStayEvenWithLatestPolicy() = runBlocking {
        provider.companies[20]="Test Industries" to "https://testindustries.example/"
        provider.pictures[20]=image(Color.RED);profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Latest) }
        assertFalse(engine().consider(target(),listOf(captured())));assertEquals(0,provider.writes)
        provider.names[10]="Test Industries"
        assertFalse(engine().consider(target(),listOf(captured())));assertEquals(0,provider.writes)
        assertArrayEquals(image(Color.RED),provider.photo(20))
    }
    @Test fun onlyAnUnchangedOptimizerLogoOnACompanyContactCanUpdate() = runBlocking {
        provider.companies[20]="Test Industries" to "https://testindustries.example/";provider.names[10]="Test Industries"
        assertTrue(engine().consider(target(),listOf(captured())))
        val state=ContactsOptimizerState(context,account);assertTrue(state.logoMatches(20,requireNotNull(provider.photo(20))))
        assertTrue(engine().consider(target(),listOf(captured(image(Color.GREEN)))))
        provider.outputs.remove(20);provider.pictures[20]=image(Color.RED)
        assertFalse(engine().consider(target(),listOf(captured())));assertEquals(2,provider.writes)
        assertArrayEquals(image(Color.RED),provider.photo(20))
    }
    @Test fun nonCompanyCandidateWinsMissingPhotoAndLateDiscoveryRevocationBlocksWrites() = runBlocking {
        assertTrue(engine().consider(target(),listOf(captured(),captured(image(Color.RED),"WhatsApp"))))
        assertArrayEquals(image(Color.RED),provider.photo(20))
        provider.outputs.clear();provider.pictures.clear();profiles.update(account) { it.copy(businessDiscoveryDisclosed=false) }
        assertFalse(engine().consider(target(),listOf(captured().copy(companyDiscovery=true))))
        assertEquals(1,provider.writes)
    }
    @Test fun ambiguousCompanyVariantsSurviveQueueReloadAndIdentityChangesRejectReview() = runBlocking {
        provider.companies[20]="Test Industries" to null
        matches=CompanyNameSearchResult(listOf(CompanyNameMatch("Test Industries","first.example"),CompanyNameMatch("Test Industries","second.example")))
        val store=WhatsAppAvatarReviewStore(context)
        for((index,logo) in discovery().find(target(),true).candidates.withIndex()) {
            val id=UUID.randomUUID().toString()
            val item=WhatsAppAvatarReviewItem(id,10,20,null,target().displayName,null,account,WhatsAppAvatarReviewStore.newFileName(id,"candidate"),null,512,512,null,null,
                "Company logo",WhatsAppAvatarMatchStrength.UserSelected,System.currentTimeMillis(),source="Company logo",companyDomain=logo.domain,
                companyName=logo.company,companyDiscovery=true)
            store.enqueue(item,logo.bytes,null)
        }
        val restored=WhatsAppAvatarReviewStore(context).forAccount(account);assertEquals(setOf("first.example","second.example"),restored.map { it.companyDomain }.toSet())
        assertTrue(restored.all { discovery().reviewStillMatches(target(),it,true) })
        provider.companies[20]="Different Industries" to null
        assertFalse(discovery().reviewStillMatches(target(),restored.first(),true))
    }
    @Test fun searchCacheIsAccountScopedExpiresAndUnavailableIsNotAnEmptyMatch() = runBlocking {
        var now=1000L;var calls=0
        val search=HttpCompanyNameSearch(context,{now}) { calls++;CompanyNameSearchResult(unavailable=true) }
        assertTrue(search.search("Test Industries",account).unavailable);search.search("Test Industries",account);assertEquals(1,calls)
        search.search("Test Industries","second@example.test");assertEquals(2,calls)
        now+=24*60*60*1000L;search.search("Test Industries",account);assertEquals(3,calls)
        search.refreshUnavailable(account);search.search("Test Industries",account);assertEquals(4,calls)
        search.search("Test Industries","second@example.test");assertEquals(5,calls)
        assertFalse(parseCompanyMatches(JSONObject("{\"status\":\"ok\",\"data\":[]}")).unavailable)
        assertTrue(parseCompanyMatches(JSONObject("{\"status\":\"ok\",\"data\":[{\"name\":\"Test Industries\",\"domain\":\"127.0.0.1\"}]}")).unavailable)
    }
    @Test fun oldConsentDoesNotAuthorizeDiscoveryAndNewConsentIsPerAccount() {
        profiles.update(account) { it.copy(businessDiscoveryDisclosed=false,businessDisclosed=true) }
        assertTrue(ContactOptimizationProfiles(context).businessAllowed(account))
        assertFalse(ContactOptimizationProfiles(context).settings(account).businessDiscoveryDisclosed)
        profiles.update(account) { it.copy(businessDiscoveryDisclosed=true) }
        assertTrue(ContactOptimizationProfiles(context).settings(account).businessDiscoveryDisclosed)
        assertFalse(ContactOptimizationProfiles(context).settings("second@example.test").businessDiscoveryDisclosed)
    }
}
