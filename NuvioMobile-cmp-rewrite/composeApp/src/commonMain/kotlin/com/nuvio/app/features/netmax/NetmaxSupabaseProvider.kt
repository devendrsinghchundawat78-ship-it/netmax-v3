package com.nuvio.app.features.netmax

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/** Dedicated NetMax cloud backend. Kept separate from Nuvio's server backend so
 * existing Nuvio sync/server connections remain intact while NetMax services
 * (AI, requests, reports and future NetMax services) use the NetMax project.
 */
object NetmaxSupabaseProvider {
    private const val URL = "https://rnjukbhdoxozlefhexyq.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_-I_TiMNm45qr5NuJlA5F0A_iZOx-nK8"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = URL,
            supabaseKey = PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Functions)
            install(Storage)
        }
    }
}
