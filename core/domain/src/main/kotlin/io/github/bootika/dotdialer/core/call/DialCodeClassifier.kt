package io.github.bootika.dotdialer.core.call

sealed interface DialCodeAction {
    data object None : DialCodeAction
    data object OpenDeviceInfo : DialCodeAction
    data object OpenSarInfo : DialCodeAction
    data class DispatchSecretCode(val digits: String) : DialCodeAction
    data class PlaceCarrierCode(val code: String) : DialCodeAction
}

object DialCodeClassifier {
    private val secretCodePattern = Regex("^\\*#\\*#(\\d+)#\\*#\\*$")

    fun shouldShowCallUi(input: String, requested: Boolean = true): Boolean =
        requested && classify(input) == DialCodeAction.None

    fun classify(input: String): DialCodeAction {
        val code = input.trim()
        if (code.length < 3) return DialCodeAction.None
        if (code == "*#06#") return DialCodeAction.OpenDeviceInfo
        if (code == "*#07#") return DialCodeAction.OpenSarInfo

        secretCodePattern.matchEntire(code)?.let { match ->
            return DialCodeAction.DispatchSecretCode(match.groupValues[1])
        }

        return if ((code.startsWith("*") || code.startsWith("#")) && code.endsWith("#")) {
            DialCodeAction.PlaceCarrierCode(code)
        } else {
            DialCodeAction.None
        }
    }
}
