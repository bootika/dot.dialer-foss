package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import dev.goodwy.rphone.controller.util.currentDisplayRotation as platformDisplayRotation

/** Centralizes the API 29 fallback used to lay out settings around landscape system bars. */
internal fun Context.currentDisplayRotation(): Int {
    return platformDisplayRotation()
}
