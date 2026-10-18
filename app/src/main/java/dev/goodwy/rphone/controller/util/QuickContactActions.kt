package dev.goodwy.rphone.controller.util


/** Explicit copy action only; no additional contact fields leave the device. */
internal fun contactIdentityText(name: String?, number: String): String =
    name?.trim()?.takeIf { it.isNotEmpty() && it != number.trim() }
        ?.let { "$it\n${number.trim()}" } ?: number.trim()

/** Reject ambiguous text, multiple numbers, MMI and hidden values. Never initiates a call. */
internal fun selectedPhoneNumber(text: CharSequence?): String? {
    val raw = text?.toString()?.trim()?.takeIf { it.length <= 120 } ?: return null
    val prefix = Regex("^\\(\\+([1-9]\\d{0,2})\\)\\s*(?=\\d)").find(raw)
    val normalized = if (prefix != null) "+" + prefix.groupValues[1] + raw.substring(prefix.range.last + 1) else raw
    if (!normalized.matches(Regex("[+]?\\d[\\d\\s().-]*"))) return null
    val digits = normalized.filter(Char::isDigit)
    if (digits.length !in 3..15) return null
    return (if (normalized.startsWith('+')) "+" else "") + digits
}
