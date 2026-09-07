@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal annotation class WorkerThread

/**
 * Same surface as the CloudStream library helper (providers and the vendored
 * library code call `Coroutines.mainWork` / `ioWork` / `atomicListOf`).
 */
object Coroutines {
    fun <T> T.main(work: suspend ((T) -> Unit)): Job {
        val value = this
        return CoroutineScope(Dispatchers.Main).launch {
            work(value)
        }
    }

    fun <T> T.ioSafe(work: suspend (CoroutineScope.(T) -> Unit)): Job {
        val value = this
        return CoroutineScope(workerDispatcher).launch {
            try {
                work(value)
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    suspend fun <T, V> V.ioWorkSafe(work: suspend (CoroutineScope.(V) -> T)): T? {
        val value = this
        return withContext(workerDispatcher) {
            try {
                work(value)
            } catch (e: Exception) {
                logError(e)
                null
            }
        }
    }

    suspend fun <T, V> V.ioWork(work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(workerDispatcher) {
            work(value)
        }
    }

    suspend fun <T, V> V.mainWork(work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(Dispatchers.Main) {
            work(value)
        }
    }

    fun runOnMainThread(work: (() -> Unit)) {
        runOnMainThreadNative(work)
    }

    /**
     * Safe to add and remove whenever you want, iterate inside `list.withLock { }`.
     */
    fun <T> atomicListOf(vararg items: T): AtomicMutableList<T> {
        return AtomicMutableList(items.toMutableList())
    }
}

fun runOnMainThreadNative(work: (() -> Unit)) {
    CoroutineScope(Dispatchers.Main).launch { work() }
}

val workerDispatcher: CoroutineDispatcher = Dispatchers.IO
