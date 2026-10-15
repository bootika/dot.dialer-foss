package dev.goodwy.rphone.controller

import com.ramcosta.composedestinations.generated.destinations.ContactPhotosScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactManagementScreenDestination

// Android Navigation's object overload serializes DirectionImpl; these generated directions use String routes.
internal fun contactPhotoReviewRoute(account: String?): String =
    ContactPhotosScreenDestination(requestedAccount=account,openReview=true).route

internal fun contactManagementIntentRoute(): String = ContactManagementScreenDestination().route
