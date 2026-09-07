@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi

/**
 * Host side registry of the login/sync providers. This app does not run the
 * CloudStream sync UI, so the services are instantiated but never authorised:
 * providers can still read the shapes they link against.
 */
class AccountManager {
    companion object {
        val aniListApi = AniListApi(0)
        val kitsuApi = KitsuApi()
        val managedPartnerApi = TraktApi()

        fun init() = Unit
    }

    class KitsuApi {
        val name = "Kitsu"
    }

    class TraktApi {
        val name = "Trakt"
    }
}
