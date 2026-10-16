package io.github.bootika.dotdialer.core.license

enum class AppPlan { FREE, PLUS, PREMIUM }
enum class PlanFeature { MANUAL_OPTIMIZER, AUTOMATIC_OPTIMIZER, PREMIUM_APPEARANCE }

data class PlanEntitlements(
    val paid: AppPlan = AppPlan.FREE,
    val trialStarted: Long = 0,
    val trialEnds: Long = 0,
) {
    fun effective(now: Long): AppPlan = if (trialStarted > 0 && now >= trialStarted && now < trialEnds)
        AppPlan.PREMIUM else paid
    fun allows(feature: PlanFeature, now: Long): Boolean = when (feature) {
        PlanFeature.MANUAL_OPTIMIZER -> effective(now) >= AppPlan.PLUS
        PlanFeature.AUTOMATIC_OPTIMIZER, PlanFeature.PREMIUM_APPEARANCE -> effective(now) == AppPlan.PREMIUM
    }
}
