@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.ui.home

import com.lagradost.cloudstream3.utils.DataStoreHelper

/**
 * Only the companion members providers touch are mirrored here (this app has its
 * own home screen), so a provider asking for "resume watching" gets an empty list
 * instead of a missing class.
 */
class HomeViewModel {
    companion object {
        suspend fun getResumeWatching(): List<DataStoreHelper.ResumeWatchingResult> =
            DataStoreHelper.getResumeWatching()
    }
}
