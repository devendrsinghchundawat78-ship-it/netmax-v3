package com.nuvio.app.features.netmax

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthStorage
import io.github.jan.supabase.auth.auth
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * NetMax AI is usable without an account. A device is identified, in order of preference, by:
 *
 *  1. a NetMax Supabase session — either the mirrored email login ([NetmaxAuthBridge]) or an
 *     anonymous session, which the SDK persists so a guest keeps history across launches;
 *  2. a per-install guest id, which the Edge Function turns into a stable synthetic user so
 *     chat, requests and reports still work when the project has no anonymous provider.
 *
 * Nothing here replaces or clears the app's own auth state ([com.nuvio.app.core.auth.AuthRepository]):
 * a signed-out user stays signed out for every other feature, this only talks to the NetMax project.
 */
internal object NetmaxGuestAccess {
    private val log = Logger.withTag("NetmaxGuestAccess")
    private val lock = Mutex()

    private var anonymousSignInAttempted = false

    @OptIn(ExperimentalUuidApi::class)
    private var guestIdFallback: String? = null

    /** @return true when the NetMax project has a usable session (email login or anonymous). */
    suspend fun ensureSession(): Boolean {
        val auth = NetmaxSupabaseProvider.client.auth
        if (sessionOrNull() != null) return true
        lock.withLock {
            if (!anonymousSignInAttempted) {
                anonymousSignInAttempted = true
                runCatching { auth.signInAnonymously() }
                    .onFailure {
                        // Anonymous sign-ins are an optional Supabase provider: when the project
                        // has it switched off the AI keeps working through the guest id instead.
                        log.i { "NetMax anonymous sign-in unavailable: ${it.message}" }
                    }
            }
        }
        return sessionOrNull() != null
    }

    /** The bearer token the Edge Function verifies, when this device has one. */
    suspend fun accessTokenOrNull(): String? {
        ensureSession()
        return runCatching { NetmaxSupabaseProvider.client.auth.currentAccessTokenOrNull() }.getOrNull()
            ?: runCatching { com.nuvio.app.core.network.SupabaseProvider.client.auth.currentAccessTokenOrNull() }
                .getOrNull()
    }

    /** Stable identity sent along with every request so a guest is still traceable. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun guestId(): String {
        sessionUserIdOrNull()?.let { return it }
        AuthStorage.loadAnonymousUserId()?.takeIf { it.isNotBlank() }?.let { return it }
        return lock.withLock {
            guestIdFallback ?: Uuid.random().toString().also { guestIdFallback = it }
        }
    }

    private fun sessionOrNull() =
        runCatching { NetmaxSupabaseProvider.client.auth.currentSessionOrNull() }.getOrNull()

    private fun sessionUserIdOrNull(): String? = runCatching {
        NetmaxSupabaseProvider.client.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
