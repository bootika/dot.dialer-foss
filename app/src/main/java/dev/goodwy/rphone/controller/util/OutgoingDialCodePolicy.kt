package dev.goodwy.rphone.controller.util

import android.net.Uri
import io.github.bootika.dotdialer.core.call.DialCodeClassifier

internal fun outgoingCallUiAllowed(number: String, requested: Boolean = true): Boolean =
    DialCodeClassifier.shouldShowCallUi(Uri.decode(number), requested)
