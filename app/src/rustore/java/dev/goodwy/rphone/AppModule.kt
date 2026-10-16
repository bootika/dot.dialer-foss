package dev.goodwy.rphone

import dev.goodwy.rphone.controller.RuStoreViewModel
import dev.goodwy.rphone.controller.PurchaseHelper
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val appModule = module {
    includes(commonAppModule)
    single<PurchaseHelper> { RuStoreViewModel(androidApplication(), get()) }
}
