package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactPhotoReadinessTest {
    @Test fun selectedDefaultsCannotPretendConsentOrAndroidAccess() {
        assertEquals(ContactPhotoReadiness.ConsentRequired, contactPhotoReadiness(true,true,false,false,true))
        assertEquals(ContactPhotoReadiness.NotificationAccessRequired, contactPhotoReadiness(true,true,true,false,true))
        assertEquals(ContactPhotoReadiness.ContactsAccessRequired, contactPhotoReadiness(true,true,true,true,false))
        assertEquals(ContactPhotoReadiness.Ready, contactPhotoReadiness(true,true,true,true,true))
        assertEquals(ContactPhotoReadiness.Off, contactPhotoReadiness(true,false,true,true,true))
        assertEquals(ContactPhotoReadiness.Unavailable, contactPhotoReadiness(false,true,true,true,true))
    }
    @Test fun revocationAndAccountConsentMustReturnToSetup() {
        assertEquals(ContactPhotoReadiness.NotificationAccessRequired, contactPhotoReadiness(true,true,true,false,true))
        assertEquals(ContactPhotoReadiness.ConsentRequired, contactPhotoReadiness(true,true,false,true,true))
    }
}
