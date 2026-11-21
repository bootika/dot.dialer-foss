package dev.goodwy.rphone.controller

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.FlowCollector

/** Direct rights reads enforce actual expiry now; collect retains ordinary UI notifications. */
@OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
internal class PlayReadThroughFlow<T>(private val upstream: StateFlow<T>,private val read: ()->T):StateFlow<T> by upstream {
    override val value:T get()=read()
    override val replayCache:List<T> get()=listOf(value)
    override suspend fun collect(collector:FlowCollector<T>):Nothing = upstream.collect(object:FlowCollector<T> {
        override suspend fun emit(value:T) { collector.emit(read()) }
    })
}
