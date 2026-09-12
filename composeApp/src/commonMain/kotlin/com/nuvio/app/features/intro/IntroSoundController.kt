package com.nuvio.app.features.intro

/**
 * Plays the NetMax intro sound (deep boom -> rising whoosh -> logo impact -> tail).
 *
 * The audio is a single pre-baked file whose internal timing matches the intro
 * animation timeline, so starting both together keeps them in sync. Every call
 * fails silently: a broken sound must never crash or delay the intro.
 */
internal expect object IntroSoundController {

    /** Starts loading the sound in the background; idempotent. */
    fun preload()

    /** Suspends until the sound is ready (or fails), at most [timeoutMs]. */
    suspend fun awaitReady(timeoutMs: Long)

    /** Plays the intro sound from the beginning. No-op when unavailable. */
    fun play()

    /** Stops a currently playing intro sound. */
    fun stop()
}
