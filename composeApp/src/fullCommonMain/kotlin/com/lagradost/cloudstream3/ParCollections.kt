package com.lagradost.cloudstream3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> = runBlocking(Dispatchers.IO) {
    map { async { f(it) } }.awaitAll()
}

fun <A, B> Iterable<A>.amap(f: suspend (A) -> B): List<B> = runBlocking(Dispatchers.IO) {
    map { async { f(it) } }.awaitAll()
}

fun <A, B> Array<A>.amap(f: suspend (A) -> B): List<B> = runBlocking(Dispatchers.IO) {
    map { async { f(it) } }.awaitAll()
}

fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = runBlocking(Dispatchers.IO) {
    map { async { f(it) } }.awaitAll()
}

fun <A, B> Iterable<A>.apmap(f: suspend (A) -> B): List<B> = runBlocking(Dispatchers.IO) {
    map { async { f(it) } }.awaitAll()
}

fun <A, B> Array<A>.apmap(f: suspend (A) -> B): List<B> = runBlocking(Dispatchers.IO) {
    map { async { f(it) } }.awaitAll()
}

fun <A, B> List<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> = runBlocking(Dispatchers.IO) {
    mapIndexed { index, item -> async { f(index, item) } }.awaitAll()
}

fun <A, B> Iterable<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> = runBlocking(Dispatchers.IO) {
    mapIndexed { index, item -> async { f(index, item) } }.awaitAll()
}

fun <A, B> List<A>.apmapIndexed(f: suspend (Int, A) -> B): List<B> = amapIndexed(f)
fun <A, B> Iterable<A>.apmapIndexed(f: suspend (Int, A) -> B): List<B> = amapIndexed(f)

fun <A> argamap(vararg f: suspend () -> A): List<A> = runBlocking(Dispatchers.IO) {
    f.map { async { it() } }.awaitAll()
}

fun <A> List<A>.parFilter(f: suspend (A) -> Boolean): List<A> = runBlocking(Dispatchers.IO) {
    map { async { it to f(it) } }.awaitAll().filter { it.second }.map { it.first }
}

fun <A> Iterable<A>.parFilter(f: suspend (A) -> Boolean): List<A> = runBlocking(Dispatchers.IO) {
    map { async { it to f(it) } }.awaitAll().filter { it.second }.map { it.first }
}
