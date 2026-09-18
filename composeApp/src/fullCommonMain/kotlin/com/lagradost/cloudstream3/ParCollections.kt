package com.lagradost.cloudstream3

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> Iterable<A>.amap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> Array<A>.amap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> Iterable<A>.apmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> Array<A>.apmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> List<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> = coroutineScope {
    mapIndexed { index, item -> async { f(index, item) } }.awaitAll()
}

suspend fun <A, B> Iterable<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> = coroutineScope {
    mapIndexed { index, item -> async { f(index, item) } }.awaitAll()
}

suspend fun <A, B> List<A>.apmapIndexed(f: suspend (Int, A) -> B): List<B> = amapIndexed(f)
suspend fun <A, B> Iterable<A>.apmapIndexed(f: suspend (Int, A) -> B): List<B> = amapIndexed(f)

suspend fun <A> argamap(vararg f: suspend () -> A): List<A> = coroutineScope {
    f.map { async { it() } }.awaitAll()
}

suspend fun <A> List<A>.parFilter(f: suspend (A) -> Boolean): List<A> = coroutineScope {
    map { async { it to f(it) } }.awaitAll().filter { it.second }.map { it.first }
}

suspend fun <A> Iterable<A>.parFilter(f: suspend (A) -> Boolean): List<A> = coroutineScope {
    map { async { it to f(it) } }.awaitAll().filter { it.second }.map { it.first }
}
