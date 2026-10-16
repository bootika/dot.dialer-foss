package dev.goodwy.rphone.controller.license

import io.github.bootika.dotdialer.core.license.AppPlan
import java.util.Locale

enum class PlanPeriod { MONTHLY, YEARLY, LIFETIME }
data class PlanProduct(val tier: AppPlan, val period: PlanPeriod, val id: String) {
    val subscription get() = period != PlanPeriod.LIFETIME
}
object PlanProducts {
    val all = listOf(AppPlan.PLUS, AppPlan.PREMIUM).flatMap { tier ->
        PlanPeriod.entries.map { period -> PlanProduct(tier, period, "dot_${tier.name.lowercase(Locale.ROOT)}_${period.name.lowercase(Locale.ROOT)}") }
    }
    fun purchased(products: Set<String>): AppPlan = all.filter { it.id in products }.maxOfOrNull { it.tier } ?: AppPlan.FREE
}
