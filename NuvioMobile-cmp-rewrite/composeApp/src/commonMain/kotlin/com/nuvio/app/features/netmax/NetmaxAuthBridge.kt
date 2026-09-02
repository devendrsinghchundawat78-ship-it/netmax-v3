package com.nuvio.app.features.netmax

import co.touchlab.kermit.Logger
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.auth

/** Mirrors email credentials into the NetMax Supabase project without making
 * NetMax cloud availability a hard dependency for the main Nuvio login flow.
 */
object NetmaxAuthBridge {
    private val log = Logger.withTag("NetmaxAuthBridge")

    suspend fun signIn(email: String, password: String) {
        runCatching {
            NetmaxSupabaseProvider.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }.recoverCatching {
            // First login for an existing Nuvio user: create the corresponding
            // NetMax account. If email confirmation is enabled, AI will simply
            // remain unavailable until the NetMax account is confirmed.
            NetmaxSupabaseProvider.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
        }.onFailure { log.w(it) { "NetMax account bridge failed; primary login remains valid" } }
    }

    suspend fun signUp(email: String, password: String) = signIn(email, password)

    suspend fun signOut() {
        runCatching { NetmaxSupabaseProvider.client.auth.signOut() }
            .onFailure { log.w(it) { "NetMax sign-out failed" } }
    }
}
