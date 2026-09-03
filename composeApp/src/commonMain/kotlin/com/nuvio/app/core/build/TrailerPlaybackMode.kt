package com.nuvio.app.core.build

/**
 * How hero/detail trailers are played back.
 *
 * [IN_APP] means the app embeds its own trailer player (full distribution only).
 * [EXTERNAL] means trailers open in the system browser/player (store distributions).
 */
enum class TrailerPlaybackMode {
    IN_APP,
    EXTERNAL,
}
