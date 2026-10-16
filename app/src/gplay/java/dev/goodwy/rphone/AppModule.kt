package dev.goodwy.rphone

import dev.goodwy.rphone.controller.PlayStoreViewModel
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.license.GiftLicenseManager
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    includes(commonAppModule)
    single<PurchaseHelper> { PlayStoreViewModel(androidApplication(), get(), get()) }
    single { GiftLicenseManager(androidContext()) }
}
