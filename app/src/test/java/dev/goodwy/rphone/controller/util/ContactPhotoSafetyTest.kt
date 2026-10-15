package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
class ContactPhotoSafetyTest {
    private fun context(): Context {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        val root = File(app.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        return object : ContextWrapper(app) {
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
        }
    }
    @Test fun largerInterpolatedImagesAreNotRealUpgrades() {
        val original = pattern(96)
        for (size in listOf(512, 1024, 4096)) {
            assertFalse("Interpolated " + size, isMeaningfulPhotoUpgrade(original, resized(original, size)))
        }
    }
    @Test fun addedRealDetailIsAUsefulMatchingUpgrade() {
        val original = pattern(640)
        val thumbnail = resized(original, 96)
        assertTrue(sameNullablePhoto(thumbnail, original))
        assertTrue(isMeaningfulPhotoUpgrade(thumbnail, original))
    }
    @Test fun missingApprovalNeverReachesProviderWrite() = runBlocking {
        val ctx = context()
        val result = GoogleContactPhotoHydrator(ctx).hydrate(
            GoogleContactPhotoHydrationRequest("+40720033833", "first@example.test", pattern(640)))
        assertEquals(GoogleContactPhotoHydrationResult.Rejected(GoogleContactPhotoHydrationRejection.ApprovalRequired), result)
    }
    @Test fun expiredQueueDeletesCandidateAndPreviousImages() {
        val ctx = context(); var now = System.currentTimeMillis()
        val store = WhatsAppAvatarReviewStore(ctx) { now }; store.selectAccount("first@example.test")
        val item = item(now)
        store.enqueue(item, pattern(128), pattern(96))
        assertEquals(1, store.items.value.size)
        now += WhatsAppAvatarReviewStore.RETENTION_MILLIS + 1
        store.selectAccount(item.accountName)
        assertTrue(store.items.value.isEmpty())
        assertNull(store.candidateBytes(item)); assertNull(store.previousBytes(item))
    }
    @Test fun corruptImageCannotRemainAnActionableProposal() {
        val ctx = context(); val store = WhatsAppAvatarReviewStore(ctx)
        val item = item(System.currentTimeMillis()); store.selectAccount(item.accountName)
        store.enqueue(item, pattern(128), pattern(96))
        val directory = ContactOptimizationProfiles(ctx).directory(item.accountName)
        File(directory, item.candidateFileName).writeBytes(byteArrayOf(0,1,2))
        store.selectAccount(item.accountName)
        assertTrue(store.items.value.isEmpty())
    }
    @Test fun sourceVariantsAreSeparateAndDuplicatesDoNotGrowTheQueue() {
        val ctx = context(); val store = WhatsAppAvatarReviewStore(ctx)
        val item = item(System.currentTimeMillis()); store.selectAccount(item.accountName)
        store.enqueue(item, pattern(128), pattern(96))
        store.enqueue(item.copy(id = UUID.randomUUID().toString()), pattern(128), pattern(96))
        assertEquals(1, store.items.value.size)
        val secondId = UUID.randomUUID().toString()
        store.enqueue(item.copy(id = secondId, source = "Google",
            candidateFileName = WhatsAppAvatarReviewStore.newFileName(secondId, "candidate"),
            previousFileName = WhatsAppAvatarReviewStore.newFileName(secondId, "previous")), pattern(128), pattern(96))
        assertEquals(2, store.forAccount(item.accountName).size)
        assertEquals(1,store.items.value.size)
        store.selectSource(item.accountName,"google")
        assertEquals("Google",store.items.value.single().source)
    }
    @Test fun eightHundredNumbersSurviveBatchReloadAndExceedOldMetadataLimit() {
        val ctx=context();val store=WhatsAppAvatarReviewStore(ctx);val account="first@example.test"
        store.selectAccount(account);store.beginSearch(account,"numbers")
        val rows=(1..800).map { n -> item(System.currentTimeMillis()).copy(contactId=n.toLong(),rawContactId=n+1000L,
            phoneDataId=n+2000L,source="Phone number",kind="number",previousFileName=null,
            originalNumber="0721000000",proposedNumber="+40721000000",displayName="Test Alexandru Butica $n") }
        rows.forEach { store.enqueue(it,null,null) };assertTrue(store.finishBatch(account))
        val file=File(ContactOptimizationProfiles(ctx).directory(account),"review-queue.json")
        assertTrue(file.length()>256*1024)
        val reopened=WhatsAppAvatarReviewStore(ctx).also { it.selectAccount(account) }
        assertEquals(rows.map { it.id },reopened.items.value.map { it.id })
        assertEquals(800,contactReviewSelectAll(reopened.items.value,emptySet()) { false }.size)
    }
    @Test fun sourceSearchReplacesOnlyItsResultsAndKeepsWhatsAppAvailable() {
        val ctx=context();val store=WhatsAppAvatarReviewStore(ctx);val first=item(System.currentTimeMillis())
        store.selectAccount(first.accountName);store.enqueue(first,pattern(128),pattern(96))
        val wa=first.copy(id=UUID.randomUUID().toString(),source="WhatsApp",
            candidateFileName=WhatsAppAvatarReviewStore.newFileName(UUID.randomUUID().toString(),"candidate"))
        store.enqueue(wa,pattern(128),pattern(96))
        store.beginSearch(first.accountName,"numbers")
        val num=first.copy(id=UUID.randomUUID().toString(),kind="number",source="Phone number",previousFileName=null)
        store.enqueue(num,null,null);assertTrue(store.finishBatch(first.accountName))
        assertEquals(listOf(num.id),store.items.value.map { it.id })
        store.beginSearch(first.accountName,"gravatar");assertTrue(store.items.value.isEmpty())
        assertNull(store.candidateBytes(first));assertNotNull(store.candidateBytes(wa))
        val newer=first.copy(id=UUID.randomUUID().toString(),candidateFileName=WhatsAppAvatarReviewStore.newFileName(UUID.randomUUID().toString(),"candidate"),previousFileName=null)
        store.enqueue(newer,pattern(256),null);assertTrue(store.finishBatch(first.accountName))
        assertEquals(listOf(newer.id),store.items.value.map { it.id })
        assertEquals(setOf(num.id,wa.id,newer.id),store.allItems.value.map { it.id }.toSet())
        assertEquals(setOf(wa.id,newer.id),reviewSelectionCandidates(store.items.value,store.allItems.value).map { it.id }.toSet())
        store.selectSource(first.accountName,"numbers");assertEquals(listOf(num.id),store.items.value.map { it.id })
        assertEquals(listOf(num.id),WhatsAppAvatarReviewStore(ctx).also { it.selectAccount(first.accountName) }.items.value.map { it.id })
        store.clear(first.accountName);assertTrue(store.items.value.isEmpty());assertTrue(store.availableSources.value.isEmpty())
    }
    @Test fun corruptMetadataDoesNotDeletePhotosOrExposeAnotherAccount() {
        val ctx=context();val store=WhatsAppAvatarReviewStore(ctx);val row=item(System.currentTimeMillis())
        store.selectAccount(row.accountName);store.enqueue(row,pattern(128),pattern(96))
        File(ContactOptimizationProfiles(ctx).directory(row.accountName),"review-queue.json").writeText("broken")
        store.selectAccount(row.accountName);assertTrue(store.items.value.isEmpty());assertNotNull(store.candidateBytes(row))
        store.selectAccount("other@example.test");assertTrue(store.allItems.value.isEmpty())
    }
    @Test fun sameAccountInTwoAndroidProfilesHasDifferentStorage() {
        val a = ContactOptimizationProfiles(context()); val b = ContactOptimizationProfiles(context())
        a.update("first@example.test") { it.copy(enabled = false) }
        assertNotEquals(a.directory("first@example.test"), b.directory("first@example.test"))
        assertTrue(b.settings("first@example.test").enabled)
        assertFalse(a.settings("first@example.test").enabled)
    }
    private fun item(time: Long): WhatsAppAvatarReviewItem {
        val id = UUID.randomUUID().toString()
        return WhatsAppAvatarReviewItem(id, 10,20,30,"Test Alexandru Butica","+40720033833",
            "first@example.test",WhatsAppAvatarReviewStore.newFileName(id,"candidate"),
            WhatsAppAvatarReviewStore.newFileName(id,"previous"),128,128,96,96,"Gravatar",
            WhatsAppAvatarMatchStrength.ExactPhone,time, source = "Gravatar")
    }
    private fun pattern(size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        for (y in 0 until size) for (x in 0 until size) {
            val detail = if ((x + y) % 2 == 0) 16 else -16
            bitmap.setPixel(x,y,Color.rgb((40 + x*160/size + detail).coerceIn(0,255),
                (40 + y*160/size + detail).coerceIn(0,255), (100+detail).coerceIn(0,255)))
        }
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it); bitmap.recycle();it.toByteArray() }
    }
    private fun resized(bytes: ByteArray, size: Int): ByteArray {
        val original = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        val scaled = original.scale(size,size,true)
        return ByteArrayOutputStream().use { scaled.compress(Bitmap.CompressFormat.PNG,100,it)
            if (original !== scaled) scaled.recycle();original.recycle();it.toByteArray() }
    }
}
