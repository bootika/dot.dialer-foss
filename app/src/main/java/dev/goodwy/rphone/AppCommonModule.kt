package dev.goodwy.rphone

import androidx.room.Room
import dev.goodwy.rphone.controller.license.PlanAccessManager
import io.github.bootika.dotdialer.core.license.PlanFeature
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.CallNotificationManager
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.repository.CallLogRepository
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.modal.repository.CachingContactsRepository
import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.data.repository.CallerRepositoryImpl
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.controller.MainViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.CallPhotoHydrationManager
import dev.goodwy.rphone.controller.util.GoogleContactPhotoScan
import dev.goodwy.rphone.controller.util.GoogleContactAccounts
import dev.goodwy.rphone.controller.util.GoogleContactsAuthorization
import dev.goodwy.rphone.controller.util.GooglePeoplePhotoClient
import dev.goodwy.rphone.controller.util.GooglePeoplePhotoSource
import dev.goodwy.rphone.controller.util.PlatformGoogleContactsAuthorization
import dev.goodwy.rphone.modal.db.RillDatabase
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Production bindings shared by every distribution; store bindings stay in the flavor module. */
internal val commonAppModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            RillDatabase::class.java,
            "rill_database"
        ).addMigrations(RillDatabase.MIGRATION_1_2)
            .addMigrations(RillDatabase.MIGRATION_2_3)
            .build()
    }
    single { get<RillDatabase>().privateContactDao() }

    single { ContactsRepository(androidContext(), get()) }
    single<IContactsRepository> { CachingContactsRepository(androidContext(), get<ContactsRepository>()) }
    single<ICallLogRepository> {
        CallLogRepository(androidContext(), androidContext().contentResolver, get(), get())
    }
    single {
        PreferenceManager(androidContext(), featureAllowed = {
            dev.goodwy.rphone.controller.license.PlanAccessManager.get(androidContext()).allows(it)
        }, onPlanRequired = { dev.goodwy.rphone.controller.license.PlanAccessManager.get(androidContext()).requestUpgrade(it) })
    }
    single<GoogleContactsAuthorization> { PlatformGoogleContactsAuthorization(androidContext()) }
    single { GoogleContactAccounts(androidContext()) }
    single<GooglePeoplePhotoSource> { GooglePeoplePhotoClient() }
    single { CallPhotoHydrationManager(androidContext(), get(), get(), get(), isFeatureEnabled = {
        get<PreferenceManager>().getBoolean(dev.goodwy.rphone.controller.util.GoogleContactPhotoPreferences.ENABLED, false) &&
            dev.goodwy.rphone.controller.license.PlanAccessManager.get(androidContext()).allows(io.github.bootika.dotdialer.core.license.PlanFeature.MANUAL_OPTIMIZER)
    }) }
    single { GoogleContactPhotoScan(androidContext(), get(), get(), get(),
        featureAllowed={PlanAccessManager.get(androidContext()).allows(PlanFeature.MANUAL_OPTIMIZER)}) }
    single { dev.goodwy.rphone.controller.util.WhatsAppAvatarReviewStore(androidContext()) }
    single { dev.goodwy.rphone.controller.util.WhatsAppAvatarContactStore(androidContext(),
        numberRegion = { dev.goodwy.rphone.controller.util.contactNumberRegion(androidContext(), get<PreferenceManager>()) }) }
    single {
        dev.goodwy.rphone.controller.util.WhatsAppAvatarSyncProcessor(
            androidContext(), get(), get(), get(), get(),
            featureAllowed = { dev.goodwy.rphone.controller.license.TestAccessManager.get(androidContext()).allowed &&
                dev.goodwy.rphone.controller.license.PlanAccessManager.get(androidContext()).allows(io.github.bootika.dotdialer.core.license.PlanFeature.MANUAL_OPTIMIZER) },
            automaticAllowed = { dev.goodwy.rphone.controller.license.PlanAccessManager.get(androidContext()).allows(io.github.bootika.dotdialer.core.license.PlanFeature.AUTOMATIC_OPTIMIZER) },
            onUnknownPhoto = { get<IContactsRepository>().invalidateAndNotify() },
        )
    }
    // Clean Architecture Wires
    single<ICallerRepository> { CallerRepositoryImpl(get()) }
    single { GetCallerNameUseCase(get()) }
    single { CallStateManager(get(), resolvePhoto = dev.goodwy.rphone.controller.util.ReadableCallPhotoResolver(androidContext())::resolve, unknownPhoto = { dev.goodwy.rphone.controller.util.UnsavedNumberAvatars.resolve(androidContext(),it,get()) }) }
    single { CallNotificationManager(androidContext(), get()) }
    single<ICallRepository> { CallRepositoryImpl() }

    viewModel { ContactsViewModel(androidApplication(), get(), get()) }
    viewModel { CallLogViewModel(androidApplication(), get(), androidContext().contentResolver, get(), get(), get()) }
    viewModel { CallViewModel(androidContext(), get(), get()) }
    viewModel { MainViewModel(get()) }
}
