package dev.goodwy.rphone.controller.license

import io.github.bootika.dotdialer.core.license.AppPlan
import org.json.JSONObject

internal enum class PlanConfirmationSource { TRIAL, DESK, INVITATION, GIFT, PLAY_SUBSCRIPTION, PLAY_LIFETIME }

internal data class PlanConfirmation(
    val plan: AppPlan,
    val source: PlanConfirmationSource,
    val until: Long? = null,
    val permanent: Boolean = false,
    val basePlan: AppPlan = AppPlan.FREE,
) {
    fun toRecord(time: Long): JSONObject = JSONObject()
        .put("plan", plan.name).put("source", source.name)
        .put("permanent", permanent).put("basePlan", basePlan.name)
        .put("issued", time).also { record -> until?.let { record.put("until", it) } }

    companion object {
        fun fromRecord(record: JSONObject?, time: Long): PlanConfirmation? {
            if (record == null) return null
            return runCatching {
                if (time - record.getLong("issued") !in 0L..86_400L) return null
                val until = if (record.has("until")) record.getLong("until") else null
                if (until != null && until <= time) return null
                PlanConfirmation(
                    AppPlan.valueOf(record.getString("plan")),
                    PlanConfirmationSource.valueOf(record.getString("source")),
                    until, record.getBoolean("permanent"), AppPlan.valueOf(record.getString("basePlan")),
                )
            }.getOrNull()
        }
    }
}

internal fun newPlanConfirmation(
    previous: PlanGrant?,
    verified: PlanGrant,
    time: Long,
    trialRequestStarted: Long?,
    invitation: Boolean,
    basePlan: AppPlan,
): PlanConfirmation? {
    val access = verified.entitlementsAt(time, leaseValid = true, clockTrusted = true)
    val paid = access.paid
    if (paid > AppPlan.FREE && (previous != null && paid > previous.entitlements.paid ||
            invitation && previous == null)) {
        val terms = verified.paidTerms
        return PlanConfirmation(
            paid, if (invitation) PlanConfirmationSource.INVITATION else PlanConfirmationSource.DESK,
            until = terms?.get(paid), permanent = terms?.containsKey(paid) == true && terms[paid] == null,
        )
    }
    val issuedAt = verified.issuedAt
    if (trialRequestStarted != null && issuedAt != null && (previous?.entitlements?.trialStarted ?: 0L) == 0L &&
        access.trialStarted > 0 && access.trialStarted == issuedAt &&
        issuedAt in (trialRequestStarted - 300)..(time + 300) &&
        time < access.trialEnds && basePlan < AppPlan.PREMIUM) {
        return PlanConfirmation(AppPlan.PREMIUM, PlanConfirmationSource.TRIAL, access.trialEnds, basePlan = basePlan)
    }
    return null
}
