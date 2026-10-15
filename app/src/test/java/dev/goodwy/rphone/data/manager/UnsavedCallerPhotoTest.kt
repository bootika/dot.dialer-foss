package dev.goodwy.rphone.data.manager

import dev.goodwy.rphone.domain.repository.ICallerRepository
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import dev.goodwy.rphone.modal.data.Contact
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class UnsavedCallerPhotoTest {
    @Test fun cachedAvatarDoesNotInventAnAddressBookContactOrOverrideSystemPhoto() = runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val repo=object:ICallerRepository { override suspend fun getContactByNumber(number:String):Contact?=null }
        val manager=CallStateManager(GetCallerNameUseCase(repo),scope,unknownPhoto={ "file:///local-avatar.png" })
        manager.onNewCallReceived("+40720033833",null)!!.join()
        val metadata=manager.callerMetadataMap.value.getValue("+40720033833")
        assertFalse(metadata.isLocalContact);assertEquals("+40720033833",metadata.name)
        assertEquals("file:///local-avatar.png",metadata.photoUri)
        manager.onNewCallReceived("+40720033833",null,"content://system/photo")!!.join()
        assertEquals("content://system/photo",manager.callerMetadataMap.value.getValue("+40720033833").photoUri)
        assertNull(manager.onNewCallReceived("",null));manager.onCallEnded();assertTrue(manager.callerMetadataMap.value.isEmpty())
        scope.cancel()
    }
}
