package dev.goodwy.rphone.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import org.koin.core.context.GlobalContext

class DotDialerCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    @Suppress("DEPRECATION")
    override fun onCreateSession(): Session = createSession()

    override fun onCreateSession(sessionInfo: SessionInfo): Session = createSession()

    private fun createSession(): Session {
        val koin = GlobalContext.get()
        val source = AndroidAutoCatalogSource(
            context = applicationContext,
            contactsRepository = koin.get<IContactsRepository>(),
            callLogRepository = koin.get<ICallLogRepository>(),
            preferences = koin.get<PreferenceManager>(),
        )
        return DotDialerCarSession(source)
    }
}

private class DotDialerCarSession(
    private val source: AndroidAutoCatalogSource,
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        AndroidAutoHomeScreen(carContext, source)
}
