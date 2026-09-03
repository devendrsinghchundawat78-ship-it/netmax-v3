package com.nuvio.app.core.build

/**
 * Full (sideloaded) distribution: every feature is enabled.
 */
actual object AppFeaturePolicy {
    actual val pluginsEnabled: Boolean = true
    actual val personalMediaAddonCopyEnabled: Boolean = true
    actual val p2pEnabled: Boolean = true
    actual val inAppUpdaterEnabled: Boolean = true
    actual val customServerConnectionsEnabled: Boolean = true
    actual val mediaPlaybackForegroundServiceEnabled: Boolean = true
    actual val imdbRatingLogoEnabled: Boolean = true
    actual val donationActionsEnabled: Boolean = true
    actual val donationProgressEnabled: Boolean = true
    actual val accountDeletionEnabled: Boolean = true
    actual val supportersContributorsPageEnabled: Boolean = true
    actual val heroTrailerPlaybackSupported: Boolean = true
    actual val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
}
