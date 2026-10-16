package io.github.bootika.dotdialer.core.license

/** Highest acknowledged tier is monotonic; a downgrade never replays a guide. */
object PlanGuidePolicy {
    fun pending(effective: AppPlan, acknowledged: AppPlan): List<AppPlan> =
        listOf(AppPlan.PLUS, AppPlan.PREMIUM).filter { it <= effective && it > acknowledged }
}
