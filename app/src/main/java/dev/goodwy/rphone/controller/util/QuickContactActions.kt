package dev.goodwy.rphone.controller.util


/** Explicit copy action only; no additional contact fields leave the device. */
internal fun contactIdentityText(name: String?, number: String): String =
    name?.trim()?.takeIf { it.isNotEmpty() && it != number.trim() }
        ?.let { "$it\n${number.trim()}" } ?: number.trim()

/** Reject ambiguous text, multiple numbers, MMI and hidden values. Never initiates a call. */
internal fun selectedPhoneNumber(text: CharSequence?): String? {
    val raw = text?.toString()?.trim()?.takeIf { it.length <= 120 } ?: return null
    if (!raw.matches(Regex("[+]?\\d[\\d\\s().-]*"))) return null
    val digits = raw.filter(Char::isDigit)
    if (digits.length !in 3..15) return null
    return (if (raw.startsWith('+')) "+" else "") + digits
}
