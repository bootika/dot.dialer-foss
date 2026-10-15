package io.github.bootika.dotdialer.observability

enum class ReportingMode(val value: String) {
    Automatic("automatic"), OnDemand("on_demand");

    companion object {
        fun parse(value: String?): ReportingMode = entries.firstOrNull { it.value == value } ?: Automatic
        fun maySend(current: ReportingMode, configured: ReportingMode, manual: Boolean): Boolean =
            current == configured && (current == Automatic || manual)
    }
}
