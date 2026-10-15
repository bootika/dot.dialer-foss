package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import androidx.core.graphics.scale
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactOptimizationTest {
    private lateinit var context: Context
    private lateinit var provider: OptimizationContactsProvider
    private lateinit var prefs: PreferenceManager
    private lateinit var reviews: WhatsAppAvatarReviewStore
    private lateinit var contacts: WhatsAppAvatarContactStore
    private lateinit var processor: WhatsAppAvatarSyncProcessor
    private val account = "first@example.test"
    private var notificationAllowed = true
    private var gravatarResponse: ByteArray? = null
    private var companyLogoAvailable = true
    private var companyRequests = 0
    private var companySearchResponse=CompanyNameSearchResult(unavailable=true)
    private var companyBrandMatches=false
    private var companyLoadHook:(() -> Unit)?=null
    private var gravatarWait: CompletableDeferred<Unit>? = null
    private var gravatarEntered: CompletableDeferred<Unit>? = null
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS)
        val directory = File(app.cacheDir, "contact-test-" + UUID.randomUUID()).apply { mkdirs() }
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun createDeviceProtectedStorageContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "private").apply { mkdirs() }
        }
        Shadows.shadowOf(context.getSystemService(TelephonyManager::class.java)).setSimCountryIso("ro")
        provider = OptimizationContactsProvider(directory).also { it.install(context) }
        prefs = PreferenceManager(context)
        runBlocking { prefs.restoreAllPreferences(mapOf(GoogleContactPhotoPreferences.ACCOUNT to account,
            GoogleContactPhotoPreferences.ENABLED to false)) }
        contacts = WhatsAppAvatarContactStore(context)
        reviews = WhatsAppAvatarReviewStore(context).also { it.selectAccount(account) }
        val auth = object : GoogleContactsAuthorization {
            override val isAvailable = false
            override suspend fun authorize(accountName: String?) = GoogleContactsAuthorizationResult.Unavailable
            override fun completeAuthorization(data: Intent?, requestedAccountName: String?) = GoogleContactsAuthorizationResult.Unavailable
            override suspend fun revoke(accountName: String) = true
        }
        val google = CallPhotoHydrationManager(context, prefs, auth, object : GooglePeoplePhotoSource {
            override suspend fun loadPhoto(accessToken: String, phoneNumber: String): GooglePeoplePhotoResult = error("No network")
        })
        processor = WhatsAppAvatarSyncProcessor(context, prefs, reviews, contacts, google,
            gravatar = object : ContactAvatarSource {
                override val sourceName = "Gravatar"
                override suspend fun load(email: String): ByteArray {
                    gravatarEntered?.complete(Unit)
                    gravatarWait?.await()
                    return gravatarResponse ?: imageBytes(256)
                }
            }, companies = object : CompanyLogoSource {
                override suspend fun load(website: String): ByteArray? { companyRequests++;companyLoadHook?.invoke();return if (companyLogoAvailable) imageBytes(256) else null }
                override suspend fun matchesCompany(website:String,company:String)=companyBrandMatches
            }, notificationAccess = { notificationAllowed },companyNames=CompanyNameSearch { _,_ -> companySearchResponse })
        processor.profiles.update(account) { it.copy(enabled = true, disclosed = true, autoUpdateExisting = false,
            normalize = false, gravatar = false, business = false, google = false) }
        provider.pictures[20] = imageBytes(96, Color.RED)
    }
    @Test fun scanAndBulkApplyAllEightHundredNumbersWithoutTruncation() = runBlocking {
        provider.names.clear();provider.raws.clear();provider.phones.clear();provider.pictures.clear();provider.emails.clear()
        for(n in 0 until 800) {
            provider.names[n+100L]="Test Alexandru Butica $n"
            provider.raws+=OptimizationContactsProvider.Raw(n+1000L,n+100L,account)
            provider.phones+=OptimizationContactsProvider.Phone(n+2000L,n+1000L,(721000000L+n).toString().padStart(10,'0'))
        }
        processor.profiles.update(account) { it.copy(normalize=true) }
        processor.scanAccount("numbers")
        assertEquals(800,reviews.items.value.size);assertEquals(800,processor.scanProgress.value!!.checked)
        assertEquals(0,provider.writes)
        val selected=contactReviewSelectAll(reviews.items.value,emptySet()) { false }
        val result=processor.resolveSelection(selected.toList())
        assertEquals(800,result.attempted);assertEquals(0,result.failed);assertFalse(processor.applying.value)
        assertEquals(800,provider.writes);assertTrue(reviews.forAccount(account).isEmpty())
        provider.phones.forEachIndexed { n,row -> assertEquals("+40"+(721000000L+n),row.number);assertEquals("Mobile",row.label) }
    }
    @Test fun manualUnsavedPhotoAppliesWithoutCreatingContactAndRemainsProtected() = runBlocking {
        provider.names.clear();provider.raws.clear();provider.phones.clear()
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Manual) }
        val id=UUID.randomUUID().toString();val bytes=imageBytes(192,Color.BLUE)
        val item=WhatsAppAvatarReviewItem(id,0,0,null,"Test Alexandru Butica","+40720033833",account,
            WhatsAppAvatarReviewStore.newFileName(id,"candidate"),null,192,192,null,null,"com.whatsapp",
            WhatsAppAvatarMatchStrength.Ambiguous,System.currentTimeMillis(),source="WhatsApp")
        reviews.enqueue(item,bytes,null);assertTrue(reviews.isAvailablePhoto(item))
        if(ContactOptimizationAccess.supported(context)) {
            assertTrue(processor.resolveReview(id,true,false));assertEquals(0,provider.writes)
            val cache=UnsavedNumberAvatars(context,account)
            val path=File(android.net.Uri.parse(cache.resolve("+40720033833")!!).path!!)
            assertArrayEquals(bytes,path.readBytes())
            assertFalse(cache.store("+40720033833",imageBytes(512),ContactPhotoAutomaticPolicy.Latest) { true })
        } else assertFalse(processor.resolveReview(id,true,false))
        assertEquals(0,provider.writes);assertTrue(provider.raws.isEmpty())
    }
    @Test fun normalizationAcceptsOnlyValidatedSameNumber() {
        assertEquals("+40720033833", safeE164Replacement(context, "0720 033 833", "+40720033833", "RO"))
        assertEquals("+40212123456", safeE164Replacement(context, "0212123456", "+40212123456", "RO"))
        assertEquals("+40720033833", safeE164Replacement(context, "+40720033833", "+40720033833", "RO"))
        assertEquals("+33123456789", safeE164Replacement(context, "+33 1 23 45 67 89", "+33123456789", "RO"))
        for (value in listOf("*222", "0720033833 ext 2", "0720033833,2", "1234", "0720ABC833", "999999999999999")) {
            assertNull(value, safeE164Replacement(context, value, "+40720033833", "RO"))
        }
        assertNull(safeE164Replacement(context, "0720033833", "+40721111111", "RO"))
    }
    @Test fun unknownCountryDoesNotUsePhoneLocale() {
        Shadows.shadowOf(context.getSystemService(TelephonyManager::class.java)).setSimCountryIso("")
        assertNull(normalizedContactNumber("0720033833", null))
        assertNull(safeE164Replacement(context, "0720033833", "+40720033833"))
    }
    @Test fun exactTargetRequiresOneContactAndOneGoogleRaw() {
        assertEquals(20L, contacts.resolveByPhone("+40720033833", account)?.rawContactId)
        provider.raws.add(OptimizationContactsProvider.Raw(21, 10, account))
        assertNull(contacts.resolveByPhone("+40720033833", account))
    }
    @Test fun ambiguousPhoneNeverFallsBackToUniqueName() = runBlocking {
        provider.names[11] = "Other test contact"
        provider.raws.add(OptimizationContactsProvider.Raw(21, 11, account))
        provider.phones.add(OptimizationContactsProvider.Phone(31, 21, "0720033833"))
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
        assertEquals(WhatsAppAvatarMatchStrength.Ambiguous, reviews.items.value.single().matchStrength)
    }
    @Test fun samsungAndLocalRawsAreNeverEligible() {
        provider.raws.clear(); provider.raws.add(OptimizationContactsProvider.Raw(20, 10, account, "com.samsung"))
        assertNull(contacts.resolveByPhone("+40720033833", account))
        provider.raws.clear(); provider.raws.add(OptimizationContactsProvider.Raw(20, 10, "", ""))
        assertNull(contacts.resolveByUniqueName("Test Alexandru Butica", account))
    }
    @Test fun disabledAutomaticUpdateKeepsPreviewRegardlessOfLegacyPolicy() = runBlocking {
        for (policy in ExistingPhotoPolicy.entries) {
            processor.profiles.update(account) { it.copy(policy = policy) }
            processor.process("com.whatsapp", notification(), null)
            assertEquals(policy.name, 0, provider.writes)
        }
        assertEquals(1, reviews.items.value.size)
        assertTrue(processor.resolveReview(reviews.items.value.single().id, true, false))
        assertEquals(1, provider.writes)
        assertTrue(sameNullablePhoto(imageBytes(128), provider.photo(20)))
        assertEquals("0720 033 833", provider.phones.single().number)
    }
    @Test fun existingPhotoHasBothVersionsAndIsNotWrittenInPreview() = runBlocking {
        provider.pictures[20] = imageBytes(96, Color.RED)
        processor.process("com.whatsapp", notification(), null)
        val item = reviews.items.value.single()
        assertNotNull(reviews.previousBytes(item)); assertNotNull(reviews.candidateBytes(item))
        assertEquals(0, provider.writes)
        assertTrue(processor.resolveReview(item.id, false, false))
        assertEquals(0, provider.writes)
    }
    @Test fun perceptuallyEqualPhotoHasNoPromptOrWrite() = runBlocking {
        provider.pictures[20] = imageBytes(128)
        processor.process("com.whatsapp", notification(), null)
        assertTrue(reviews.items.value.isEmpty())
        assertEquals(0, provider.writes)
        assertTrue(sameNullablePhoto(imageBytes(96), imageBytes(640)))
        assertFalse(sameNullablePhoto(imageBytes(96, Color.RED), imageBytes(96, Color.GREEN)))
    }
    @Test fun missingPhotoIsFilledWithoutApplyEvenWhenExistingUpdatesAreOff() = runBlocking {
        provider.pictures.clear()
        processor.process("com.whatsapp", notification(), null)
        assertEquals(1, provider.writes)
        assertTrue(reviews.items.value.isEmpty())
        assertTrue(sameNullablePhoto(imageBytes(128), provider.photo(20)))
        assertEquals("0720 033 833", provider.phones.single().number)
    }
    @Test fun bothAutomaticPoliciesFillMissingPhotosWithExistingUpdatesOffAndNoDuplicateWrite() = runBlocking {
        for(policy in listOf(ContactPhotoAutomaticPolicy.Newest, ContactPhotoAutomaticPolicy.Optimal)) {
            processor.profiles.update(account) { it.copy(automaticPhotoPolicy=policy,autoUpdateExisting=false) }
            provider.outputs.clear(); provider.pictures.clear()
            val before=provider.writes
            processor.process("com.whatsapp",notification(),null)
            assertEquals(before+1,provider.writes)
            assertTrue(reviews.items.value.isEmpty())
            processor.process("com.whatsapp",notification(),null)
            assertEquals(before+1,provider.writes)
            assertEquals(128,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        }
    }
    @Test fun optimalImprovesSmallPhotosButKeepsEqualSizeAndAdequatePhotos() = runBlocking {
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Optimal,autoUpdateExisting=true) }
        provider.pictures[20]=imageBytes(96,Color.RED)
        processor.process("com.whatsapp",notification(),null)
        assertEquals(1,provider.writes)
        provider.outputs.remove(20); provider.pictures[20]=imageBytes(128,Color.RED)
        processor.process("com.whatsapp",notification(),null)
        assertEquals(1,provider.writes)
        provider.outputs.remove(20); provider.pictures[20]=imageBytes(512,Color.RED)
        processor.process("com.whatsapp",notification(size=1024),null)
        assertEquals(1,provider.writes)
        assertEquals(512,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
    }
    @Test fun newestReplacesEqualOrLargerPhotosAndPolicyIsRecheckedBeforeWrite() = runBlocking {
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Newest,autoUpdateExisting=true) }
        provider.outputs.remove(20); provider.pictures[20]=imageBytes(128,Color.RED)
        processor.process("com.whatsapp",notification(),null)
        assertEquals(1,provider.writes)
        provider.outputs.remove(20); provider.pictures[20]=imageBytes(512,Color.RED)
        provider.beforePhotoOpen={ processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Optimal) } }
        processor.process("com.whatsapp",notification(size=1024),null)
        assertEquals(1,provider.writes)
        assertEquals(512,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
    }
    @Test fun automaticUpdatesRespectProviderCapacityAndUnknownCapacityButManualStillWorks() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting=true) }
        provider.maximumPhotoDimension=512
        provider.pictures[20]=imageBytes(640,Color.RED)
        processor.process("com.whatsapp",notification(size=1024),null)
        assertEquals(0,provider.writes)
        assertEquals(640,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        provider.maximumPhotoDimension=null
        provider.pictures[20]=imageBytes(96,Color.RED)
        processor.process("com.whatsapp",notification(color=Color.BLUE),null)
        assertEquals(0,provider.writes)
        val candidate=reviews.items.value.first { it.candidateWidth==128 }
        assertTrue(processor.resolveReview(candidate.id,true,false))
        assertEquals(1,provider.writes)
    }
    @Test fun providerAspectScalingCannotReduceAnAxisAutomatically() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting=true) }
        provider.maximumPhotoDimension=512
        val old=Bitmap.createBitmap(512,400,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        provider.pictures[20]=ByteArrayOutputStream().use { old.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray() }
        val received=Bitmap.createBitmap(1024,600,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val bytes=ByteArrayOutputStream().use { received.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray() }
        val event=requireNotNull(processor.capture("com.whatsapp",notification(),null))
        processor.processCaptured(event.copy(avatar=requireNotNull(event.avatar).copy(bytes=bytes,width=1024,height=600)))
        assertEquals(0,provider.writes)
        assertEquals(400,provider.photo(20)!!.toContactPhotoImageInfo()!!.height)
        old.recycle();received.recycle()
    }
    @Test fun automaticRulePersistsPerAccountAndMissingOrUnknownLegacyRuleKeepsNewest() {
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Optimal) }
        assertEquals(ContactPhotoAutomaticPolicy.Optimal,ContactOptimizationProfiles(context).settings(account).automaticPhotoPolicy)
        assertEquals(ContactPhotoAutomaticPolicy.Newest,processor.profiles.settings("second@example.test").automaticPhotoPolicy)
        val file=File(processor.profiles.directory(account),"settings.json")
        file.writeText(org.json.JSONObject(file.readText()).apply { remove("automaticPhotoPolicy") }.toString())
        assertEquals(ContactPhotoAutomaticPolicy.Newest,processor.profiles.settings(account).automaticPhotoPolicy)
    }
    @Test fun unavailablePhotoLookupIsNotTreatedAsAMissingPhoto() = runBlocking {
        provider.pictures.clear(); provider.photoLookupUnavailable = true
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
        assertTrue(reviews.items.value.isEmpty())
        assertEquals("processing_failed", processor.profiles.result(account).outcome)
    }
    @Test fun visibleAvatarOwnedByAnotherRawCannotBeSilentlyReplaced() = runBlocking {
        provider.pictures.clear(); provider.selectedPhotoRaw = 21
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
        assertEquals(1, reviews.items.value.size)
    }
    @Test fun selectedPhotoOnTheTargetRawStillAllowsEligibleAutomaticUpdates() = runBlocking {
        provider.selectedPhotoRaw = 20
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true) }
        processor.process("com.whatsapp", notification(), null)
        assertEquals(1, provider.writes)
        assertTrue(reviews.items.value.isEmpty())
    }
    @Test fun largerAndEqualSizedNewPhotosAreAppliedAutomatically() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true) }
        for (oldSize in listOf(96, 128)) {
            provider.outputs.clear(); provider.pictures[20] = imageBytes(oldSize, Color.RED)
            processor.process("com.whatsapp.w4b", notification(color=if(oldSize==96) Color.GREEN else Color.BLUE), null)
        }
        assertEquals(2, provider.writes)
        assertTrue(reviews.items.value.isEmpty())
    }
    @Test fun smallerPhotoNeedsReviewAndOnlyExplicitApplyMayDegradeIt() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true) }
        provider.pictures[20] = imageBytes(256, Color.RED)
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
        assertEquals(1, reviews.items.value.size)
        assertTrue(processor.resolveReview(reviews.items.value.single().id, true, false))
        assertEquals(1,provider.writes)
        assertEquals(128,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
    }
    @Test fun sizeLimitDefaultsOnAndOptOutIsPerAccountPersistedAndMigratesSafely() {
        assertTrue(ContactOptimizationSettings().onlySameOrLarger)
        assertTrue(processor.profiles.settings(account).onlySameOrLarger)
        processor.profiles.update(account) { it.copy(onlySameOrLarger = false) }
        assertFalse(ContactOptimizationProfiles(context).settings(account).onlySameOrLarger)
        assertTrue(ContactOptimizationProfiles(context).settings("second@example.test").onlySameOrLarger)
        val file = File(processor.profiles.directory(account), "settings.json")
        val old = org.json.JSONObject(file.readText()).apply { remove("onlySameOrLarger") }
        file.writeText(old.toString())
        assertTrue(ContactOptimizationProfiles(context).settings(account).onlySameOrLarger)
        assertFalse(processor.profiles.settings(account).autoUpdateExisting)
    }
    @Test fun legacySizeOptOutCannotShrinkAutomaticWhatsAppPhotos() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true, onlySameOrLarger = false) }
        provider.pictures[20] = imageBytes(640, Color.RED)
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
        assertEquals(1,reviews.items.value.size)
        assertEquals(640, provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        assertTrue(sameNullablePhoto(imageBytes(640,Color.RED), provider.photo(20)))
        assertEquals("0720 033 833", provider.phones.single().number)
        assertEquals("Mobile", provider.phones.single().label)
    }
    @Test fun automaticBusinessUpdatesCannotReduceEitherAxisDespiteLegacyOptOut() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true, onlySameOrLarger = false) }
        val tall = Bitmap.createBitmap(64, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        provider.pictures[20] = ByteArrayOutputStream().use { tall.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        tall.recycle()
        processor.process("com.whatsapp.w4b", notification(), null)
        assertEquals(0, provider.writes)
        assertEquals(1,reviews.items.value.size)
        assertEquals(200,provider.photo(20)!!.toContactPhotoImageInfo()!!.height)
    }
    @Test fun explicitManualReviewMayShrinkWithoutEnablingAutomaticUpdates() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = false, onlySameOrLarger = false) }
        provider.pictures[20] = imageBytes(640, Color.RED)
        val before = provider.photo(20)
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
        val item = reviews.items.value.single()
        assertTrue(processor.resolveReview(item.id, true, false))
        assertFalse(sameNullablePhoto(before, provider.photo(20)))
        assertEquals(128,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        assertEquals(1, provider.writes)
    }
    @Test fun legacyOptOutNeverStartsASmallerAutomaticWrite() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true, onlySameOrLarger = false) }
        provider.pictures[20] = imageBytes(640, Color.RED)
        val before = provider.photo(20)
        provider.beforePhotoOpen = { processor.profiles.update(account) { it.copy(onlySameOrLarger = true) } }
        processor.process("com.whatsapp", notification(), null)
        assertTrue(sameNullablePhoto(before, provider.photo(20)))
        assertTrue(provider.outputs.values.all { it.length() == 0L })
        assertNotEquals("updated", processor.profiles.result(account).outcome)
        assertEquals(1, reviews.items.value.size)
    }
    @Test fun sizeOptOutDoesNotOverrideRawOwnershipOrUnreadablePhotoGuards() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true, onlySameOrLarger = false) }
        provider.pictures[20] = imageBytes(640, Color.RED)
        provider.selectedPhotoRaw = 21
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
        provider.selectedPhotoRaw = null
        provider.pictures[20] = byteArrayOf(0, 1, 2)
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
    }
    @Test fun unknownPhotoSourceCannotUseRestoredWhatsAppOptIn() = runBlocking {
        processor.process("com.whatsapp", notification(), null)
        val item = reviews.items.value.single()
        assertFalse(processor.canApply(item.copy(source = "Unknown")))
    }
    @Test fun automaticUpdateDefaultAndOptOutArePerAccountAndPersist() {
        assertTrue(ContactOptimizationSettings().autoUpdateExisting)
        assertTrue(processor.profiles.settings("second@example.test").autoUpdateExisting)
        assertFalse(ContactOptimizationProfiles(context).settings(account).autoUpdateExisting)
        assertTrue(ContactOptimizationProfiles(context).settings("second@example.test").autoUpdateExisting)
    }
    @Test fun unreadableExistingPhotoDoesNotAuthorizeAutomaticOverwrite() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true) }
        provider.pictures[20] = byteArrayOf(1, 2, 3)
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
        assertEquals(1, reviews.items.value.size)
    }
    @Test fun smallerSingleDimensionCannotBeHiddenByALargerPixelArea() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true) }
        val tall = Bitmap.createBitmap(64, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        provider.pictures[20] = ByteArrayOutputStream().use { tall.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        tall.recycle()
        processor.process("com.whatsapp", notification(), null)
        assertEquals(0, provider.writes)
        assertEquals(1, reviews.items.value.size)
    }
    @Test fun revokedNotificationAccessBetweenChecksAndWritingKeepsTheExistingPhoto() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting = true) }
        val before = provider.photo(20)!!
        provider.beforePhotoOpen = { notificationAllowed = false }
        processor.process("com.whatsapp", notification(), null)
        assertArrayEquals(before, provider.photo(20))
        assertTrue(provider.outputs.values.all { it.length() == 0L })
        assertNotEquals("updated", processor.profiles.result(account).outcome)
    }
    @Test fun accountChangedJustBeforeWritingCannotReceiveTheCandidate() = runBlocking {
        provider.pictures.clear()
        provider.beforePhotoOpen = { processor.selectAccount("second@example.test") }
        processor.process("com.whatsapp", notification(), null)
        assertNull(provider.photo(20))
        assertTrue(provider.outputs.values.all { it.length() == 0L })
        assertNotEquals("updated", processor.profiles.result(account).outcome)
    }
    @Test fun automaticFillStillUsesSenderAvatarInsteadOfChatMedia() = runBlocking {
        provider.pictures.clear()
        val event = notification().apply { extras.putParcelable(Notification.EXTRA_PICTURE, bitmap(1024, Color.RED)) }
        processor.process("com.whatsapp", event, null)
        assertEquals(1, provider.writes)
        assertTrue(sameNullablePhoto(imageBytes(128), provider.photo(20)))
        assertFalse(sameNullablePhoto(imageBytes(1024, Color.RED), provider.photo(20)))
    }
    @Test fun exactUniqueSenderNameAppliesPolicyWithoutStructuredNumberOrNumberFormatting() = runBlocking {
        provider.pictures.clear()
        processor.profiles.update(account) { it.copy(normalize=true) }
        processor.process("com.whatsapp", nameNotification(), null)
        assertEquals(1, provider.writes)
        assertEquals(128, provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        assertTrue(reviews.items.value.isEmpty())
        assertEquals("0720 033 833", provider.phones.single().number)
    }
    @Test fun transparentNameAvatarConfirmsWhiteProviderConversionAndCompletesTheQueue() = runBlocking {
        provider.pictures.clear();provider.photoReadTransform=::whiteProviderPhoto
        val image=transparentImageBytes()
        val event=requireNotNull(processor.capture("com.whatsapp",nameNotification(),null))
        processor.processCaptured(event.copy(avatar=CapturedWhatsAppAvatar(image,128,128,null,"Test Alexandru Butica")))
        assertEquals(1,provider.writes)
        assertEquals("updated",processor.profiles.result(account).outcome)
        assertTrue(reviews.items.value.isEmpty())
        assertTrue(ContactsOptimizerState(context,account).consumed(20)>0)
        assertTrue(contactPhotoReadbackMatches(image,provider.photo(20)!!))
    }
    @Test fun reconciliationRemovesOnlyStoredPhotoWithoutWritingOrAddingAPin() = runBlocking {
        val image=queueTransparentNamePhoto()
        val item=reviews.items.value.single()
        val otherId=UUID.randomUUID().toString()
        reviews.enqueue(item.copy(id=otherId,source="WhatsApp Business",sourcePackage="com.whatsapp.w4b",
            candidateFileName=WhatsAppAvatarReviewStore.newFileName(otherId,"candidate")),imageBytes(128,Color.RED),null)
        val numberId=UUID.randomUUID().toString()
        reviews.enqueue(item.copy(id=numberId,kind="number",source="Phone number",
            candidateFileName=WhatsAppAvatarReviewStore.newFileName(numberId,"candidate")),null,null)
        provider.pictures[20]=whiteProviderPhoto(image)
        assertEquals(1,processor.reconcileAppliedPhotos())
        assertEquals(0,provider.writes)
        assertEquals(setOf(otherId,numberId),reviews.forAccount(account).map { it.id }.toSet())
        assertFalse(processor.manualPhotoPinned(account,20))
        assertEquals("unchanged",processor.profiles.result(account).outcome)
        assertEquals(0,processor.reconcileAppliedPhotos())
    }
    @Test fun unchangedNameNotificationReconcilesAnOlderAppliedProposal() = runBlocking {
        val image=queueTransparentNamePhoto()
        provider.pictures[20]=whiteProviderPhoto(image)
        val event=requireNotNull(processor.capture("com.whatsapp",nameNotification(),null))
        processor.processCaptured(event.copy(avatar=CapturedWhatsAppAvatar(image,128,128,null,"Test Alexandru Butica")))
        assertTrue(reviews.items.value.isEmpty());assertEquals(0,provider.writes)
    }
    @Test fun automaticPassReconcilesStoredPhotoEvenWhenPolicyOrPinStopsNewWrites() = runBlocking {
        val image=queueTransparentNamePhoto()
        provider.pictures[20]=whiteProviderPhoto(image);processor.protectPhoto(account,20)
        processor.runAutomaticPass()
        assertTrue(reviews.items.value.isEmpty());assertEquals(0,provider.writes)
        assertTrue(processor.manualPhotoPinned(account,20))
    }
    @Test fun reconciliationRetainsDifferentPhotoAmbiguousTargetAndRevokedAccess() = runBlocking {
        val image=queueTransparentNamePhoto()
        provider.pictures[20]=imageBytes(128,Color.RED)
        assertEquals(0,processor.reconcileAppliedPhotos())
        provider.pictures[20]=whiteProviderPhoto(image)
        provider.names[11]="Test Alexandru Butica"
        assertEquals(0,processor.reconcileAppliedPhotos())
        provider.names.remove(11);notificationAllowed=false
        assertEquals(0,processor.reconcileAppliedPhotos())
        notificationAllowed=true;processor.selectAccount("second@example.test")
        assertEquals(0,processor.reconcileAppliedPhotos())
        assertEquals(1,reviews.forAccount(account).size)
        assertEquals(0,provider.writes)
    }
    @Test fun duplicateSenderNameAcrossAccountsNeverWritesAutomatically() = runBlocking {
        provider.pictures.clear()
        provider.names[11]="Test Alexandru Butica"
        provider.raws+=OptimizationContactsProvider.Raw(21,11,"second@example.test")
        processor.process("com.whatsapp",nameNotification(),null)
        assertEquals(0,provider.writes)
        assertEquals(WhatsAppAvatarMatchStrength.Ambiguous,reviews.items.value.single().matchStrength)
    }
    @Test fun nameMatchingDoesNotStripPrefixesOrAcceptProviderCaseFolding() = runBlocking {
        provider.pictures.clear()
        for(name in listOf("~Test Alexandru Butica","test alexandru butica","Test Alexandru")) {
            provider.nameQueryIgnoresCase=true
            processor.process("com.whatsapp",nameNotification(name),null)
            assertEquals(0,provider.writes)
        }
        assertNull(contacts.resolveByUniqueName("~Test Alexandru Butica",account))
        assertNull(contacts.resolveByUniqueName("test alexandru butica",account))
    }
    @Test fun namePolicyAndManualProtectionRemainAuthoritative() = runBlocking {
        val cases=listOf(
            Triple(ContactPhotoAutomaticPolicy.Manual,0,false),
            Triple(ContactPhotoAutomaticPolicy.FillMissing,0,true),
            Triple(ContactPhotoAutomaticPolicy.FillMissing,64,false),
            Triple(ContactPhotoAutomaticPolicy.Newest,64,true),
            Triple(ContactPhotoAutomaticPolicy.Newest,256,false),
            Triple(ContactPhotoAutomaticPolicy.Optimal,512,false),
            Triple(ContactPhotoAutomaticPolicy.Latest,256,true),
        )
        for((index,case) in cases.withIndex()) {
            val (policy,oldSize,writes)=case
            provider.outputs.clear();provider.pictures.clear()
            if(oldSize>0)provider.pictures[20]=imageBytes(oldSize,Color.RED)
            processor.profiles.update(account) { it.copy(automaticPhotoPolicy=policy,autoUpdateExisting=true) }
            val before=provider.writes
            processor.process("com.whatsapp.w4b",nameNotification(color=Color.rgb(0,index*30,255)),null)
            assertEquals("$policy/$oldSize",before+if(writes)1 else 0,provider.writes)
        }
        provider.outputs.clear();provider.pictures.clear()
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Newest) }
        processor.protectPhoto(account,20)
        val before=provider.writes
        processor.process("com.whatsapp",nameNotification(),null)
        assertEquals(before,provider.writes)
    }
    @Test fun aDuplicateIntroducedAfterCaptureOrBeforeCommitBlocksTheNameWrite() = runBlocking {
        provider.pictures.clear()
        val captured=requireNotNull(processor.capture("com.whatsapp",nameNotification(),null))
        provider.names[11]="Test Alexandru Butica"
        processor.processCaptured(captured)
        assertEquals(0,provider.writes)
        provider.names.remove(11)
        provider.beforePhotoOpen={ provider.names[11]="Test Alexandru Butica" }
        processor.process("com.whatsapp",nameNotification(),null)
        assertEquals("No candidate bytes committed after a duplicate appears",0,provider.outputs[20]!!.length())
    }
    @Test fun aRenamedTargetCannotReceiveTheCapturedNamePhoto() = runBlocking {
        provider.pictures.clear()
        provider.beforePhotoOpen={ provider.names[10]="Test another contact" }
        processor.process("com.whatsapp",nameNotification(),null)
        assertEquals(0,provider.outputs[20]!!.length())
    }
    @Test fun queuedUniqueNamePhotosAreRevalidatedAndAppliedByTheAutomaticPass() = runBlocking {
        provider.pictures.clear()
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Manual) }
        processor.process("com.whatsapp",nameNotification(),null)
        assertEquals(0,provider.writes)
        assertEquals(WhatsAppAvatarMatchStrength.UniqueName,reviews.items.value.single().matchStrength)
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Newest,autoUpdateExisting=true) }
        processor.runAutomaticPass()
        assertEquals(1,provider.writes)
        assertTrue(reviews.items.value.isEmpty())
    }
    @Test fun queuedNamePhotoIsNotUsedAfterItsNameBecomesAmbiguous() = runBlocking {
        provider.pictures.clear()
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Manual) }
        processor.process("com.whatsapp",nameNotification(),null)
        provider.names[11]="Test Alexandru Butica"
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Newest,autoUpdateExisting=true) }
        processor.runAutomaticPass()
        assertEquals(0,provider.writes)
    }
    @Test fun accountChangeCannotApplyAnOldQueueItem() = runBlocking {
        processor.process("com.whatsapp", notification(), null)
        val id = reviews.items.value.single().id
        processor.selectAccount("second@example.test")
        processor.profiles.update("second@example.test") { it.copy(enabled = true, disclosed = true) }
        assertTrue(reviews.items.value.isEmpty())
        assertFalse(processor.resolveReview(id, true, false))
        assertEquals(0, provider.writes)
        assertEquals(1, reviews.forAccount(account).size)
    }
    @Test fun profilesUseStableSeparateDirectoriesAndSettings() {
        val second = "second@example.test"
        processor.profiles.update(second) { it.copy(normalize = true, gravatar = true) }
        assertFalse(processor.profiles.settings(account).normalize)
        assertTrue(processor.profiles.settings(second).normalize)
        assertNotEquals(processor.profiles.directory(account), processor.profiles.directory(second))
        assertFalse(processor.profiles.directory(second).name.contains("@"))
        assertEquals(ContactOptimizationProfiles.accountKey(" SECOND@example.test "),
            ContactOptimizationProfiles.accountKey(second))
    }
    @Test fun changedOrDeletedRawIsRevalidatedAtApply() = runBlocking {
        processor.process("com.whatsapp", notification(), null)
        val id = reviews.items.value.single().id
        provider.raws.clear()
        assertFalse(processor.resolveReview(id, true, false))
        assertEquals(0, provider.writes)
    }
    @Test fun phonePreviewAndApplyPreserveTypeLabelAndOtherNumbers() = runBlocking {
        provider.phones.add(OptimizationContactsProvider.Phone(31, 20, "*222", 0, "Service"))
        processor.profiles.update(account) { it.copy(enabled = false, normalize = true) }
        processor.scanAccount("numbers")
        assertEquals(0, provider.writes)
        val item = reviews.items.value.single()
        assertEquals("number", item.kind)
        assertTrue(processor.resolveReview(item.id, true, false))
        assertEquals("+40720033833", provider.phones.first().number)
        assertEquals(2, provider.phones.first().type)
        assertEquals("Mobile", provider.phones.first().label)
        assertEquals("*222", provider.phones.last().number)
    }
    @Test fun phoneChangedAfterPreviewIsNeverOverwritten() = runBlocking {
        processor.profiles.update(account) { it.copy(normalize = true) }
        processor.scanAccount("numbers")
        val id = reviews.items.value.single().id
        provider.phones.first().number = "+40721111111"
        assertFalse(processor.resolveReview(id, true, false))
        assertEquals(0, provider.writes)
    }
    @Test fun delayedGravatarApplyWaitsForProviderWithoutWritingTwice() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        val item = reviews.items.value.single()
        provider.pendingPhotoReads = 4
        assertTrue(processor.resolveReview(item.id, true, false))
        assertEquals(1, provider.writes)
        assertTrue(reviews.items.value.isEmpty())
        assertEquals("updated", processor.profiles.result(account).outcome)
        assertTrue(sameNullablePhoto(imageBytes(256), provider.photo(20)))
        assertEquals("0720 033 833", provider.phones.single().number)
    }
    @Test fun delayedMissingPhotoIsConfirmedAfterProviderCommits() = runBlocking {
        provider.pictures.clear()
        provider.pendingPhotoReads = 4
        processor.process("com.whatsapp", notification(), null)
        assertEquals(1, provider.writes)
        assertTrue(reviews.items.value.isEmpty())
        assertEquals("updated", processor.profiles.result(account).outcome)
    }
    @Test fun changedPhotoKeepsCandidateWithRefreshedPreviewForAnotherExplicitApply() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        val item = reviews.items.value.single()
        provider.pictures[20] = imageBytes(128, Color.BLUE)
        var photoChanged = false
        assertFalse(processor.resolveReview(item.id, true, false, onPhotoChanged = { photoChanged = true }))
        assertTrue(photoChanged)
        assertEquals(0, provider.writes)
        val refreshed = reviews.items.value.single()
        assertNotEquals(item.id, refreshed.id)
        assertTrue(sameNullablePhoto(provider.photo(20), reviews.previousBytes(refreshed)))
        assertTrue(processor.resolveReview(refreshed.id, true, false))
        assertEquals(1, provider.writes)
    }
    @Test fun providerTimeoutDoesNotClaimSuccessOrRewriteThePhoto() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        val item = reviews.items.value.single()
        provider.pendingPhotoReads = Int.MAX_VALUE
        assertFalse(processor.resolveReview(item.id, true, false))
        assertEquals(1, provider.writes)
        assertEquals(item.id, reviews.items.value.single().id)
        assertEquals("contact_changed", processor.profiles.result(account).outcome)
    }
    @Test fun unrelatedFieldChangeDuringCommitIsNotReportedAsVerifiedSuccess() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        val item = reviews.items.value.single()
        provider.pendingPhotoReads = 4
        provider.afterWrittenPhotoRead = {
            provider.afterWrittenPhotoRead = null
            provider.phones.single().number = "+40721111111"
        }
        assertFalse(processor.resolveReview(item.id, true, false))
        assertEquals(1, provider.writes)
        assertEquals("+40721111111", provider.phones.single().number)
        assertEquals(item.id, reviews.items.value.single().id)
    }
    @Test fun deletedRawDuringCommitCannotBecomeVerifiedSuccess() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        val item = reviews.items.value.single()
        provider.pendingPhotoReads = 4
        provider.afterWrittenPhotoRead = { provider.raws.clear() }
        assertFalse(processor.resolveReview(item.id, true, false))
        assertEquals(1, provider.writes)
        assertEquals(item.id, reviews.items.value.single().id)
    }
    @Test fun revokedReadPermissionDuringCommitStopsConfirmation() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        val item = reviews.items.value.single()
        provider.pendingPhotoReads = 4
        provider.afterWrittenPhotoRead = {
            provider.afterWrittenPhotoRead = null
            Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.READ_CONTACTS)
        }
        val outcome = runCatching { processor.resolveReview(item.id, true, false) }
        assertTrue(outcome.exceptionOrNull() is SecurityException)
        assertEquals(1, provider.writes)
        assertEquals(item.id, reviews.items.value.single().id)
    }
    @Test fun photoReadbackMismatchIsNotReportedAsSuccess() = runBlocking {
        val target = contacts.resolveByPhone("+40720033833", account)!!
        provider.badReadback = imageBytes(128, Color.RED)
        assertFalse(contacts.writePhoto(target, imageBytes(128)))
        assertEquals(1, provider.writes)
    }
    @Test fun providerResizeAndJpegOfApprovedPhotoCanBeConfirmed() = runBlocking {
        val image = Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888)
        val random = java.util.Random(42)
        for (y in 0 until 1024) for (x in 0 until 1024) {
            val noise = random.nextInt(129)-64
            image.setPixel(x,y,Color.rgb((100+35*kotlin.math.sin(x/31.0)+noise).toInt().coerceIn(0,255),
                (120+35*kotlin.math.sin(y/27.0)+noise).toInt().coerceIn(0,255),
                (140+25*kotlin.math.sin((x+y)/45.0)+noise).toInt().coerceIn(0,255)))
        }
        val candidate = ByteArrayOutputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray() }
        val resized = image.scale(720,720,true)
        val stored = ByteArrayOutputStream().use { resized.compress(Bitmap.CompressFormat.JPEG,90,it);it.toByteArray() }
        resized.recycle();image.recycle()
        val target = contacts.chooseContact(10, account)!!
        provider.badReadback = stored
        assertTrue("Approved source hash distance=" + perceptualHashDistance(candidate.toContactPhotoImageInfo()!!.perceptualHash,
            stored.toContactPhotoImageInfo()!!.perceptualHash), contacts.writePhoto(target,candidate))
        assertEquals(1,provider.writes)
        assertEquals(720,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        gravatarResponse = candidate
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        assertTrue(reviews.items.value.isEmpty())
        assertEquals(1,provider.writes)
        assertEquals("unchanged",processor.profiles.result(account).outcome)
    }
    @Test fun alreadyCommittedSmallerRepresentationClearsOldProposalWithoutAnotherWrite() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        val item = reviews.items.value.single()
        provider.pictures[20] = imageBytes(128)
        assertTrue(processor.resolveReview(item.id,true,false))
        assertEquals(0,provider.writes)
        assertTrue(reviews.items.value.isEmpty())
        assertEquals("unchanged",processor.profiles.result(account).outcome)
    }
    @Test fun scanProgressCountsRealTargetsAndDoesNotWriteContacts() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        val progress = processor.scanProgress.value!!
        assertEquals(account,progress.account)
        assertEquals("gravatar",progress.source)
        assertEquals(1,progress.total)
        assertEquals(1,progress.checked)
        assertEquals(ContactPhotoScanPhase.Complete,progress.phase)
        assertFalse(progress.running)
        assertEquals(1,reviews.items.value.size)
        assertEquals(0,provider.writes)
    }
    @Test fun resetRemovesOnlySelectedAccountProposalsAndKeepsSettingsAndPhotos() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        val item=reviews.items.value.single()
        val second="second@example.test"
        provider.raws.add(OptimizationContactsProvider.Raw(21,11,second))
        provider.emails[21]=listOf("second-contact@example.test")
        provider.names[11]="Other test contact"
        processor.selectAccount(second)
        processor.profiles.update(second) { it.copy(gravatar = true, gravatarDisclosed = true) }
        processor.scanAccount("gravatar")
        assertEquals(1,reviews.forAccount(second).size)
        processor.selectAccount(account)
        val settings=processor.profiles.settings(account)
        val photo=provider.photo(20)!!
        assertTrue(processor.resetResults(account))
        assertTrue(reviews.items.value.isEmpty())
        assertNull(reviews.candidateBytes(item))
        assertNull(reviews.previousBytes(item))
        assertEquals(1,reviews.forAccount(second).size)
        assertEquals(settings,processor.profiles.settings(account))
        assertArrayEquals(photo,provider.photo(20))
        assertEquals(second,processor.scanProgress.value?.account)
        assertEquals(0,processor.profiles.result(account).detectedAt)
        assertEquals(0,provider.writes)
        processor.scanAccount("gravatar")
        assertEquals(1,reviews.items.value.size)
        assertEquals(1,reviews.forAccount(second).size)
        assertEquals(settings,processor.profiles.settings(account))
        assertEquals(ContactPhotoScanPhase.Complete,processor.scanProgress.value?.phase)
        assertEquals(0,provider.writes)
    }
    @Test fun resetCancelsActiveSearchBeforeClearingWithoutLateResults() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, gravatarDisclosed = true) }
        gravatarWait=CompletableDeferred()
        val task=launch { processor.scanAccount("gravatar") }
        withTimeout(10_000) { processor.scanProgress.first { it?.phase == ContactPhotoScanPhase.Searching } }
        assertTrue(processor.resetResults(account))
        task.join()
        gravatarWait!!.complete(Unit)
        assertTrue(reviews.items.value.isEmpty())
        assertNull(processor.scanProgress.value)
        assertEquals(0,provider.writes)
    }
    @Test fun betterExistingPhotoCannotBeDegraded() = runBlocking {
        provider.pictures[20] = imageBytes(640, Color.RED)
        val target = contacts.resolveByPhone("+40720033833", account)!!
        assertFalse(contacts.writePhoto(target, imageBytes(96)))
        assertEquals(0, provider.writes)
    }
    @Test fun gravatarAndCompanyAreProposalsNotSilentWrites() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar = true, business = true, gravatarDisclosed = true, businessDisclosed = true) }
        provider.companies[20] = "Test company" to "https://example.com/"
        processor.scanAccount("gravatar"); processor.scanAccount("business")
        assertEquals(setOf("Company logo"), reviews.items.value.map { it.source }.toSet())
        assertEquals(setOf("Gravatar", "Company logo"), reviews.forAccount(account).map { it.source }.toSet())
        reviews.selectSource(account,"gravatar")
        assertEquals(setOf("Gravatar"),reviews.items.value.map { it.source }.toSet())
        assertEquals(0, provider.writes)
    }
    @Test fun companyWithoutSiteIsCheckedWithoutAnEmptyProposalOrNetworkRequest() = runBlocking {
        processor.profiles.update(account) { it.copy(business = true, businessDisclosed = true) }
        provider.companies[20] = "Test company" to null
        processor.scanAccount("business")
        assertTrue(reviews.items.value.isEmpty())
        assertEquals(0, companyRequests)
        assertEquals(ContactPhotoScanPhase.Complete, processor.scanProgress.value?.phase)
        assertEquals(1, processor.scanProgress.value?.checked)
        assertEquals(0, provider.writes)
    }
    @Test fun companySiteWithoutUsableLogoDoesNotQueueAnEmptyProposal() = runBlocking {
        processor.profiles.update(account) { it.copy(business = true, businessDisclosed = true) }
        provider.companies[20] = "Test company" to "https://example.com/"
        companyLogoAvailable = false
        processor.scanAccount("business")
        assertTrue(reviews.items.value.isEmpty())
        assertEquals(1, companyRequests)
        assertEquals(ContactPhotoScanPhase.Complete, processor.scanProgress.value?.phase)
        assertEquals(0, provider.writes)
    }
    @Test fun senderAvatarWinsOverBiggerIconAndChatMedia() {
        val n = notification().apply {
            extras.putParcelable(Notification.EXTRA_PICTURE, bitmap(1024, Color.RED))
        }
        val captured = WhatsAppNotificationAvatarExtractor.extract(context, "com.whatsapp", n, null)!!
        assertEquals(128, captured.originalWidth)
        assertTrue(sameNullablePhoto(imageBytes(128), captured.bytes))
    }
    @Test fun businessNotificationWorksButGroupsAndMediaOnlyDoNot() {
        assertNotNull(WhatsAppNotificationAvatarExtractor.extract(context, "com.whatsapp.w4b", notification(), null))
        assertNull(WhatsAppNotificationAvatarExtractor.extract(context, "com.whatsapp", notification(group = true), null))
        assertNull(WhatsAppNotificationAvatarExtractor.extract(context, "com.whatsapp", notification(), "123456789@g.us"))
        val onlyMedia = Notification.Builder(context, "test-messages").setSmallIcon(android.R.drawable.sym_action_call)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setStyle(Notification.BigPictureStyle().bigPicture(bitmap(1024, Color.RED))).build()
        assertNull(WhatsAppNotificationAvatarExtractor.extract(context, "com.whatsapp", onlyMedia, null))
    }
    @Test fun genericLargeIconNeedsStructuredIdentityOrManualReview() {
        val n = Notification.Builder(context, "test-messages").setSmallIcon(android.R.drawable.sym_action_call)
            .setCategory(Notification.CATEGORY_MESSAGE).setLargeIcon(bitmap(96))
            .setContentTitle("Test Alexandru Butica").build()
        assertNull(WhatsAppNotificationAvatarExtractor.extract(context, "com.whatsapp", n, null))
        val structured = Notification.Builder.recoverBuilder(context, n).setShortcutId("+40720033833").build()
        val captured = WhatsAppNotificationAvatarExtractor.extract(context, "com.whatsapp", structured, null)!!
        assertEquals("+40720033833", captured.phoneNumber); assertFalse(captured.senderIdentity)
        assertEquals("+40720033833", WhatsAppNotificationAvatarExtractor.extractPhone("40720033833@s.whatsapp.net"))
        assertNull(WhatsAppNotificationAvatarExtractor.extractPhone("random-prefix-40720033833"))
    }
    @Test fun notificationMessagesNeverReachPrivateStorage() = runBlocking {
        processor.process("com.whatsapp", notification(), null)
        val text = context.noBackupFilesDir.walkTopDown().filter { it.extension == "json" }
            .joinToString { it.readText() }
        assertFalse(text.contains("Private message for testing"))
        assertFalse(text.contains("EXTRA_PICTURE"))
    }
    @Test fun revokedContactsOrDisabledSourcesNeverWrite() = runBlocking {
        processor.process("com.whatsapp", notification(), null)
        val item = reviews.items.value.single()
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.WRITE_CONTACTS)
        assertFalse(processor.resolveReview(item.id, true, false))
        assertEquals(0, provider.writes)
    }
    @Test fun gravatarHashesNormalizeEmailAndOfficialSitesAreBounded() {
        assertEquals(GravatarPhotoSource.emailHash(" contact@example.test "), GravatarPhotoSource.emailHash("CONTACT@EXAMPLE.TEST"))
        assertEquals(64, GravatarPhotoSource.emailHash("contact@example.test")?.length)
        assertNull(GravatarPhotoSource.emailHash("not-email"))
        assertEquals("https://example.com/", OfficialCompanyLogoSource.publicOrigin("https://example.com/contact?person=1"))
        for (url in listOf("http://example.com", "https://127.0.0.1/", "https://example.local/", "https://user:pass@example.com/"))
            assertNull(url, OfficialCompanyLogoSource.publicOrigin(url))
    }
    @Test fun latestAloneAllowsSmallerAndManualNeverFillsMissing() = runBlocking {
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Latest,autoUpdateExisting=true) }
        provider.pictures[20]=imageBytes(512,Color.RED)
        capturedAvatar(192,Color.GREEN)
        assertEquals(192,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        assertEquals(1,provider.writes)
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Manual) }
        provider.outputs.clear(); provider.pictures.clear()
        capturedAvatar(512,Color.GREEN)
        assertNull(provider.photo(20)); assertEquals(1,provider.writes)
        assertTrue(reviews.items.value.isNotEmpty())
    }
    @Test fun manualApplyPinsPhotoUntilExplicitResume() = runBlocking {
        capturedAvatar(192,Color.GREEN)
        assertTrue(processor.resolveReview(reviews.items.value.single().id,true,false))
        assertTrue(processor.manualPhotoPinned(account,20))
        processor.profiles.update(account) { it.copy(autoUpdateExisting=true) }
        capturedAvatar(512,Color.BLUE)
        assertEquals(1,provider.writes)
        processor.resumeAutomaticPhoto(account,20)
        capturedAvatar(512,Color.BLUE)
        assertEquals(2,provider.writes)
        assertEquals(512,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
    }
    @Test fun periodicPassAppliesConsentedGravatarWithoutReviewAndRepeatedFetchDoesNotWrite() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar=true,gravatarDisclosed=true,autoUpdateExisting=true) }
        gravatarResponse=imageBytes(512,Color.GREEN)
        processor.runAutomaticPass()
        assertEquals(1,provider.writes); assertEquals(512,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        assertTrue(reviews.items.value.isEmpty())
        processor.runAutomaticPass(); assertEquals(1,provider.writes)
        assertTrue(context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.isEmpty())
    }
    @Test fun periodicPassRefusesAmbiguousEmailAndNoSourceConsentMeansNoExternalRequest() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar=true,gravatarDisclosed=true,autoUpdateExisting=true) }
        provider.names[11]="Test second contact"
        provider.raws+=OptimizationContactsProvider.Raw(21,11,account)
        provider.emails[21]=listOf("contact@example.test")
        processor.runAutomaticPass(); assertEquals(0,provider.writes)
        processor.profiles.update(account) { it.copy(gravatarDisclosed=false,business=false,normalize=false) }
        processor.runAutomaticPass(); assertEquals(0,provider.writes)
    }
    @Test fun automaticNumberFormattingPreservesDestinationAndPhotoPolicyProtection() = runBlocking {
        processor.profiles.update(account) { it.copy(normalize=true,autoUpdateExisting=true) }
        processor.process("com.whatsapp",notification(size=192),null)
        assertEquals("+40720033833",provider.phones.single().number)
        assertEquals("Test Alexandru Butica",provider.names[10])
        assertTrue(reviews.items.value.none { it.kind=="number" })
        assertTrue(sameNullablePhoto(imageBytes(192),provider.photo(20)))
    }
    @Test fun periodicNumbersNeedKnownInternationalPrefixOrExplicitCountryAndNeverGuessFromSim() = runBlocking {
        processor.profiles.update(account) { it.copy(normalize=true,enabled=false,automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Manual) }
        processor.runAutomaticPass()
        assertEquals("0720 033 833",provider.phones.single().number); assertEquals(0,provider.writes)
        prefs.setString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO,"RO")
        prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO,null)
        withTimeout(5000) { prefs.settingsChanged.first { prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO,null)=="RO" } }
        processor.runAutomaticPass()
        assertEquals("+40720033833",provider.phones.single().number); assertEquals(1,provider.writes)
        assertEquals(2,provider.phones.single().type)
    }
    @Test fun sourceFieldChangesDuringFetchPreventAnAutomaticPhotoWrite() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar=true,gravatarDisclosed=true,autoUpdateExisting=true) }
        gravatarWait=CompletableDeferred(); gravatarEntered=CompletableDeferred()
        val job=launch { processor.runAutomaticPass() }
        withTimeout(5000) { gravatarEntered!!.await() }
        provider.phones.single().number="+40721111111"
        gravatarWait!!.complete(Unit); job.join()
        assertEquals(0,provider.writes)
        assertEquals("+40721111111",provider.phones.single().number)
    }
    @Test fun unsavedExactPhoneAvatarIsCachedWithoutCreatingOrWritingAnyContact() = runBlocking {
        provider.phones.clear(); provider.names.clear(); provider.raws.clear()
        capturedAvatar(192,Color.GREEN)
        assertNotNull(UnsavedNumberAvatars(context,account).resolve("+40720033833"))
        assertEquals(0,provider.writes); assertTrue(provider.names.isEmpty()); assertTrue(provider.raws.isEmpty())
        assertTrue(reviews.items.value.isEmpty())
    }
    @Test fun anExistingContactInAnotherAccountCannotBeTreatedAsAnUnsavedNumber() = runBlocking {
        provider.raws.clear();provider.raws+=OptimizationContactsProvider.Raw(20,10,"second@example.test")
        capturedAvatar(192,Color.GREEN)
        assertNull(UnsavedNumberAvatars(context,account).resolve("+40720033833")); assertEquals(0,provider.writes)
    }
    @Test fun firstRemoteFetchDoesNotClaimANewerAvatarWhenCurrentPhotoIsAdequate() = runBlocking {
        processor.profiles.update(account) { it.copy(gravatar=true,gravatarDisclosed=true,autoUpdateExisting=true) }
        provider.pictures[20]=imageBytes(512,Color.RED)
        gravatarResponse=imageBytes(1024,Color.GREEN)
        processor.runAutomaticPass(); assertEquals(0,provider.writes)
        gravatarResponse=imageBytes(1024,Color.BLUE)
        processor.runAutomaticPass(); assertEquals(1,provider.writes)
        assertTrue(sameNullablePhoto(gravatarResponse,provider.photo(20)))
    }
    @Test fun commonSourceEvaluatorKeepsOldImagesFromOscillatingAndChoosesActuallyChangedSource() = runBlocking {
        processor.profiles.update(account) { it.copy(autoUpdateExisting=true) }
        var queued=0
        val engine=ContactsOptimizerEngine(context,processor.profiles,contacts,reviews,{ _,_ -> true },{ null },
            object:ContactAvatarSource { override val sourceName="Gravatar"; override suspend fun load(email:String):ByteArray?=null },
            object:CompanyLogoSource { override suspend fun load(website:String):ByteArray?=null },
            { target,image,smaller,valid -> contacts.writePhoto(target,image.bytes,smaller,valid) },{ _,_ -> queued++ })
        fun candidate(source:String,color:Int,size:Int=512)=CapturedWhatsAppAvatar(imageBytes(size,color),size,size,null,"Test Alexandru Butica",source=source)
        val google=candidate("Google",Color.GREEN,1024); val gravatar=candidate("Gravatar",Color.BLUE)
        assertTrue(engine.consider(contacts.accountTargets(account).single(),listOf(google,gravatar)))
        assertEquals(1024,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        assertFalse(engine.consider(contacts.accountTargets(account).single(),listOf(gravatar,google)))
        assertEquals(1,provider.writes)
        val fresh=candidate("WhatsApp",Color.RED,1024)
        assertTrue(engine.consider(contacts.accountTargets(account).single(),listOf(google,gravatar,fresh)))
        assertEquals(2,provider.writes)
        assertTrue(sameNullablePhoto(fresh.bytes,provider.photo(20)))
        assertFalse(engine.consider(contacts.accountTargets(account).single(),listOf(google,gravatar,fresh)))
        assertEquals(2,provider.writes)
    }
    @Test fun discoveredEmailLogoFillsMissingButNeverReplacesAnExistingPersonPhoto() = runBlocking {
        processor.profiles.update(account) { it.copy(business=true,businessDisclosed=true,businessDiscoveryDisclosed=true,autoUpdateExisting=true) }
        provider.pictures.clear();provider.emails[20]=listOf("person@testindustries.example")
        processor.runAutomaticPass();assertEquals(1,provider.writes)
        assertEquals(256,provider.photo(20)!!.toContactPhotoImageInfo()!!.width)
        provider.outputs.clear();provider.pictures[20]=imageBytes(512,Color.RED)
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Latest) }
        processor.runAutomaticPass();assertEquals(1,provider.writes)
        assertArrayEquals(imageBytes(512,Color.RED),provider.photo(20))
    }
    @Test fun ambiguousCompanyDomainsStaySeparateAndLateIdentityOrConsentChangeBlocksApply() = runBlocking {
        processor.profiles.update(account) { it.copy(business=true,businessDisclosed=true,businessDiscoveryDisclosed=true) }
        provider.companies[20]="Test Industries" to null;provider.emails.clear()
        companySearchResponse=CompanyNameSearchResult(listOf(CompanyNameMatch("Test Industries","first.example"),CompanyNameMatch("Test Industries","second.example")))
        processor.scanAccount("business")
        val choices=reviews.items.value;assertEquals(setOf("first.example","second.example"),choices.map { it.companyDomain }.toSet())
        assertTrue(choices.all(processor::canApply));assertEquals(0,provider.writes)
        provider.companies[20]="Other Industries" to null
        assertFalse(choices.any(processor::canApply));assertFalse(processor.resolveReview(choices.first().id,true,false));assertEquals(0,provider.writes)
        provider.companies[20]="Test Industries" to null
        processor.profiles.update(account) { it.copy(businessDiscoveryDisclosed=false) }
        assertFalse(choices.any(processor::canApply));assertEquals(0,provider.writes)
    }
    @Test fun companyFieldsChangedDuringAcquisitionDoNotQueueOldLogos() = runBlocking {
        processor.profiles.update(account) { it.copy(business=true,businessDisclosed=true,businessDiscoveryDisclosed=true) }
        provider.companies[20]="Test Industries" to "https://testindustries.example/";provider.emails.clear()
        companyLoadHook={ provider.companies[20]="Different Industries" to "https://different.example/" }
        processor.scanAccount("business");assertTrue(reviews.items.value.isEmpty());assertEquals(0,provider.writes)
    }
    @Test fun verifiedNameCanFillMissingAndUnavailableServiceIsVisibleWithoutAnEmptyProposal() = runBlocking {
        processor.profiles.update(account) { it.copy(business=true,businessDisclosed=true,businessDiscoveryDisclosed=true,autoUpdateExisting=true) }
        provider.companies[20]="Test Industries" to null;provider.emails.clear();provider.pictures.clear()
        processor.scanAccount("business")
        assertEquals("company_search_unavailable",processor.profiles.result(account).outcome)
        assertTrue(reviews.items.value.isEmpty());assertEquals(0,provider.writes)
        companySearchResponse=CompanyNameSearchResult(listOf(CompanyNameMatch("Test Industries","testindustries.example")));companyBrandMatches=true
        processor.runAutomaticPass();assertEquals(1,provider.writes);assertTrue(reviews.items.value.isEmpty())
    }
    private suspend fun capturedAvatar(size:Int,color:Int) {
        val event=requireNotNull(processor.capture("com.whatsapp",notification(),null))
        processor.processCaptured(event.copy(avatar=CapturedWhatsAppAvatar(imageBytes(size,color),size,size,
            event.identity.phoneNumber,event.identity.displayName)))
    }
    private fun nameNotification(name:String="Test Alexandru Butica",color:Int=Color.BLUE):Notification {
        val sender=Person.Builder().setName(name).setKey("998877665544332@lid")
            .setIcon(Icon.createWithBitmap(bitmap(128,color))).build()
        val style=Notification.MessagingStyle(Person.Builder().setName("Test user").build())
            .setGroupConversation(false).addMessage(Notification.MessagingStyle.Message("Test message",1000,sender))
        return Notification.Builder(context,"test-messages").setSmallIcon(android.R.drawable.sym_action_call)
            .setCategory(Notification.CATEGORY_MESSAGE).setShortcutId("998877665544332@lid").setStyle(style).build()
    }
    private suspend fun queueTransparentNamePhoto():ByteArray {
        provider.pictures.clear()
        processor.profiles.update(account) { it.copy(automaticPhotoPolicy=ContactPhotoAutomaticPolicy.Manual) }
        val image=transparentImageBytes()
        val event=requireNotNull(processor.capture("com.whatsapp",nameNotification(),null))
        processor.processCaptured(event.copy(avatar=CapturedWhatsAppAvatar(image,128,128,null,"Test Alexandru Butica")))
        assertEquals(1,reviews.items.value.size);assertEquals(0,provider.writes)
        return image
    }
    private fun transparentImageBytes():ByteArray {
        val image=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        Canvas(image).drawCircle(64f,64f,64f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLUE })
        return ByteArrayOutputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it);image.recycle();it.toByteArray() }
    }
    private fun whiteProviderPhoto(bytes:ByteArray):ByteArray {
        val source=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        val output=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        Canvas(output).apply { drawColor(Color.WHITE);drawBitmap(source,0f,0f,null) }
        return ByteArrayOutputStream().use { output.compress(Bitmap.CompressFormat.JPEG,90,it)
            source.recycle();output.recycle();it.toByteArray() }
    }
    private fun notification(group: Boolean = false, size: Int = 128, color: Int = Color.GREEN): Notification {
        val sender = Person.Builder().setName("Test Alexandru Butica").setUri("tel:+40720033833")
            .setIcon(Icon.createWithBitmap(bitmap(size,color))).build()
        val me = Person.Builder().setName("Test user").build()
        val style = Notification.MessagingStyle(me).setGroupConversation(group)
            .addMessage(Notification.MessagingStyle.Message("Private message for testing", 1000, sender))
        return Notification.Builder(context, "test-messages").setSmallIcon(android.R.drawable.sym_action_call)
            .setCategory(Notification.CATEGORY_MESSAGE).setStyle(style).setLargeIcon(bitmap(640, Color.RED)).build()
    }
    private fun bitmap(size: Int, color: Int = Color.GREEN): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun imageBytes(size: Int, color: Int = Color.GREEN): ByteArray = ByteArrayOutputStream().use {
        bitmap(size, color).compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray()
    }
}
