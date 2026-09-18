package com.lagradost.cloudstream3.mvvm

import okhttp3.ResponseBody

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorCode: Int? = null,
        val errorResponse: ResponseBody? = null,
        val errorString: String = "",
    ) : Resource<Nothing>() {
        constructor(isNetworkError: Boolean, errorString: String) : this(
            isNetworkError = isNetworkError,
            errorCode = null,
            errorResponse = null,
            errorString = errorString
        )
    }
    data class Loading(val url: String? = null) : Resource<Nothing>()
}

fun logError(throwable: Throwable) {
    throwable.printStackTrace()
}

fun <T> safe(block: () -> T): T? {
    return try {
        block()
    } catch (e: Throwable) {
        logError(e)
        null
    }
}

suspend fun <T> safeApiCall(
    apiCall: suspend () -> T,
): Resource<T> {
    return try {
        Resource.Success(apiCall())
    } catch (throwable: Throwable) {
        logError(throwable)
        Resource.Failure(
            isNetworkError = false,
            errorString = throwable.message ?: "Error"
        )
    }
}
