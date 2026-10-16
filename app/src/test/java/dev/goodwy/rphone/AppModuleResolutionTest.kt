package dev.goodwy.rphone

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.goodwy.rphone.controller.CallNotificationManager
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.CallPhotoHydrationManager
import dev.goodwy.rphone.controller.util.GoogleContactPhotoScan
import dev.goodwy.rphone.controller.util.GoogleContactsAuthorization
import dev.goodwy.rphone.controller.util.GooglePeoplePhotoSource
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.WhatsAppAvatarContactStore
import dev.goodwy.rphone.controller.util.WhatsAppAvatarReviewStore
import dev.goodwy.rphone.controller.util.WhatsAppAvatarSyncProcessor
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.db.RillDatabase
import dev.goodwy.rphone.modal.repository.CachingContactsRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class AppModuleResolutionTest {
    @Test
    fun productionGraphResolvesBackgroundEntryPointsAndPreservesDistributionBindings() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val graph = koinApplication {
            allowOverride(false)
            androidContext(application)
            modules(appModule)
        }
        try {
            val koin = graph.koin
            // Resolve the same production dependency required by the notification listener and job.
            val processor = koin.get<WhatsAppAvatarSyncProcessor>()
            assertSame(processor, koin.get<WhatsAppAvatarSyncProcessor>())
            assertSame(koin.get<WhatsAppAvatarReviewStore>(), koin.get<WhatsAppAvatarReviewStore>())
            assertSame(koin.get<WhatsAppAvatarContactStore>(), koin.get<WhatsAppAvatarContactStore>())
            assertSame(koin.get<PreferenceManager>(), koin.get<PreferenceManager>())
            assertTrue(koin.get<IContactsRepository>() is CachingContactsRepository)
            assertSame(koin.get<IContactsRepository>(), koin.get<IContactsRepository>())
            assertTrue(koin.get<ICallRepository>() is CallRepositoryImpl)
            koin.get<GetCallerNameUseCase>()
            koin.get<CallStateManager>()
            koin.get<CallNotificationManager>()
            koin.get<GoogleContactsAuthorization>()
            koin.get<GooglePeoplePhotoSource>()
            koin.get<CallPhotoHydrationManager>()
            koin.get<GoogleContactPhotoScan>()

            val expectedPurchaseHelper = when (BuildConfig.FLAVOR) {
                "foss" -> "DonateViewModel"
                "gplay" -> "PlayStoreViewModel"
                "rustore" -> "RuStoreViewModel"
                else -> error("Unverified distribution: ${BuildConfig.FLAVOR}")
            }
            val purchases = koin.get<PurchaseHelper>()
            assertEquals(expectedPurchaseHelper, purchases.javaClass.simpleName)
            assertSame(purchases, koin.get<PurchaseHelper>())
            // A complete graph must not bypass account selection or start automatic contact writes.
            assertFalse(processor.runAutomaticPass())
            assertEquals(0, processor.reconcileAppliedPhotos())
        } finally {
            graph.koin.getOrNull<RillDatabase>()?.close()
            graph.close()
        }
    }
}
