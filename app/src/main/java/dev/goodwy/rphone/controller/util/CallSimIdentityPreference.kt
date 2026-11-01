package dev.goodwy.rphone.controller.util

/** Call-screen presentation only; never changes the outgoing account or list badges. */
internal object CallSimIdentityPreference {
    const val KEY = "call_screen_always_show_sim"
    const val DEFAULT = false
}
