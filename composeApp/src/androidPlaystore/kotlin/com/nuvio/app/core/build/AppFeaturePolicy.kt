package com.nuvio.app.core.build

/**
 * Play Store distribution: restricted feature set.
 *
 * JS plugins, P2P streaming, in-app APK self-updates and the IMDb logo are
 * sideload-only features and stay disabled in the store build.
 */
actual object AppFeaturePolicy {
    actual val pluginsEnabled: Boolean = false
    actual val personalMediaAddonCopyEnabled: Boolean = false
    actual val p2pEnabled: Boolean = false
    actual val inAppUpdaterEnabled: Boolean = false
    actual val customServerConnectionsEnabled: Boolean = true
    actual val mediaPlaybackForegroundServiceEnabled: Boolean = true
    actual val imdbRatingLogoEnabled: Boolean = false
    actual val donationActionsEnabled: Boolean = true
    actual val donationProgressEnabled: Boolean = true
    actual val accountDeletionEnabled: Boolean = true
    actual val supportersContributorsPageEnabled: Boolean = true
    actual val heroTrailerPlaybackSupported: Boolean = false
    actual val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.EXTERNAL
}
