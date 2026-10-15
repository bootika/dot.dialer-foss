package dev.goodwy.rphone.controller.util

internal enum class ContactPhotoReadiness { Unavailable, Off, ConsentRequired, NotificationAccessRequired, ContactsAccessRequired, Ready }

/** Selection, disclosure and Android grants are separate requirements. */
internal fun contactPhotoReadiness(supported: Boolean, enabled: Boolean, disclosed: Boolean,
    notificationAccess: Boolean, contactsAccess: Boolean): ContactPhotoReadiness = when {
    !supported -> ContactPhotoReadiness.Unavailable
    !enabled -> ContactPhotoReadiness.Off
    !disclosed -> ContactPhotoReadiness.ConsentRequired
    !notificationAccess -> ContactPhotoReadiness.NotificationAccessRequired
    !contactsAccess -> ContactPhotoReadiness.ContactsAccessRequired
    else -> ContactPhotoReadiness.Ready
}
