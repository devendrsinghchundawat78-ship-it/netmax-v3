package com.nuvio.app.core.build

/**
 * Compile-time feature policy for the current app distribution.
 *
 * The full (sideloaded) distribution enables every feature, while the store
 * distributions (Play Store / App Store) ship a restricted set that complies
 * with store policies. Actuals live in the distribution source sets
 * (`fullCommonMain`, `androidPlaystore`, `iosAppStore`).
 */
expect object AppFeaturePolicy {
    val pluginsEnabled: Boolean
    val personalMediaAddonCopyEnabled: Boolean
    val p2pEnabled: Boolean
    val inAppUpdaterEnabled: Boolean
    val customServerConnectionsEnabled: Boolean
    val mediaPlaybackForegroundServiceEnabled: Boolean
    val imdbRatingLogoEnabled: Boolean
    val donationActionsEnabled: Boolean
    val donationProgressEnabled: Boolean
    val accountDeletionEnabled: Boolean
    val supportersContributorsPageEnabled: Boolean
    val heroTrailerPlaybackSupported: Boolean
    val trailerPlaybackMode: TrailerPlaybackMode
}
