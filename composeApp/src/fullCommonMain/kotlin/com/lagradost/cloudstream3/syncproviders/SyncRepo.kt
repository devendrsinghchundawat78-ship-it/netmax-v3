@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.utils.UiText

/** Anything a provider needs from the host to talk to a login based service. */
abstract class AuthAPI(open val idName: SyncIdName, open val apiDocUrl: String = "") {
    open val name: String get() = idName.toString()
    open val key: String = ""
    open val hasAccountSupport = true
    open val supportsLibrary = false

    open suspend fun getToken(code: String): AuthUser? = null
    open suspend fun getLoginUrl(): String? = null
    open suspend fun getLoginState(): String? = null
    open suspend fun logout() {}
    open fun isLoggedIn(): Boolean = false

}

class AuthUser(
    val name: String,
    val userId: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val expireTime: Long? = null,
)

/** Sync (list) services: providers push progress / read the user library. */
abstract class SyncAPI(idName: SyncIdName, apiDocUrl: String = "") : AuthAPI(idName, apiDocUrl) {
    data class LibraryList(val name: UiText?, val items: List<LibraryItems>?)
    data class LibraryItems(val id: String?, val name: String?, val url: String?, val coverImage: String?)
    data class LibraryMetadata(val allLibraryLists: List<LibraryList>)

    data class SyncResult(
        val id: String?,
        var plot: String? = null,
        var tags: List<String>? = null,
        var name: String? = null,
        var posterUrl: String? = null,
        var year: Int? = null,
    )

    abstract class AbstractSyncStatus {
        abstract val status: SyncStatus
    }

    enum class SyncStatus {
        Completed,
        Watching,
        Dropped,
        OnHold,
        PlanToWatch,
        Repeating,
        None,
    }

    var requireLibraryRefresh: Boolean = true

    open suspend fun status(authenticatedUser: AuthUser?, syncId: String): AbstractSyncStatus? = null
    open suspend fun updateStatus(authenticatedUser: AuthUser?, syncId: String, status: AbstractSyncStatus): Boolean = false
    open suspend fun load(authenticatedUser: AuthUser?, syncId: String): SyncResult? = null
    open suspend fun library(authenticatedUser: AuthUser?): LibraryMetadata? = null
}

open class AuthRepo(val api: AuthAPI) {
    open fun isLoggedIn(): Boolean = api.isLoggedIn()
    open suspend fun freshAuth(): AuthUser? = null
}

/** Stateless wrapper over [SyncAPI] with the same member names providers call. */
class SyncRepo(val syncApi: SyncAPI) : AuthRepo(syncApi) {
    val syncIdName: SyncIdName get() = syncApi.idName
    var requireLibraryRefresh: Boolean
        get() = syncApi.requireLibraryRefresh
        set(value) {
            syncApi.requireLibraryRefresh = value
        }

    fun authUser(): AuthUser? = null

    suspend fun library(): Result<SyncAPI.LibraryMetadata?> = runCatching { syncApi.library(freshAuth()) }
    suspend fun status(id: String): Result<SyncAPI.AbstractSyncStatus?> = runCatching { syncApi.status(freshAuth(), id) }
    suspend fun updateStatus(id: String, newStatus: SyncAPI.AbstractSyncStatus): Result<Boolean> =
        runCatching { syncApi.updateStatus(freshAuth(), id, newStatus) }
}
