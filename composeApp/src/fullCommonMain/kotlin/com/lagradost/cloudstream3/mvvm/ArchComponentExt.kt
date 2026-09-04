package com.lagradost.cloudstream3.mvvm

fun logError(throwable: Throwable) {
    System.err.println("LOG_ERROR: ${throwable.message}")
    throwable.printStackTrace()
}

suspend fun <T> safeApiCall(apiCall: suspend () -> T): T? {
    return try {
        apiCall()
    } catch (throwable: Throwable) {
        logError(throwable)
        null
    }
}

suspend fun <T> suspendSafeApiCall(apiCall: suspend () -> T): T? {
    return try {
        apiCall()
    } catch (throwable: Throwable) {
        logError(throwable)
        null
    }
}
