@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3.utils

/** Tiny observable used by the host for lifecycle events (providers subscribe to e.g. "after plugins loaded"). */
class Event<T> {
    private val observers = mutableSetOf<(T) -> Unit>()

    val size: Int get() = observers.size

    fun subscribe(observer: (T) -> Unit) {
        synchronized(observers) {
            observers.add(observer)
        }
    }

    operator fun plusAssign(observer: (T) -> Unit) = subscribe(observer)

    operator fun minusAssign(observer: (T) -> Unit) = unsubscribe(observer)

    fun unsubscribe(observer: (T) -> Unit) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    fun invoke(value: T) {
        val current = synchronized(observers) { observers.toList() }
        for (observer in current) {
            runCatching { observer.invoke(value) }
        }
    }
}
