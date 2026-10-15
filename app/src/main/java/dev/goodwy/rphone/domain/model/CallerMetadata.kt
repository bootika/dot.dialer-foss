package dev.goodwy.rphone.domain.model

data class CallerMetadata(
    val number: String,
    val name: String,
    val isLocalContact: Boolean,
    val photoUri: String? = null,
    val lookupRegionIso: String? = null
) {
    /** Scope transient enrichment; identical national digits in another country are not identity. */
    fun forLookupRegion(regionIso: String?): CallerMetadata? = takeIf { lookupRegionIso == regionIso }
}
