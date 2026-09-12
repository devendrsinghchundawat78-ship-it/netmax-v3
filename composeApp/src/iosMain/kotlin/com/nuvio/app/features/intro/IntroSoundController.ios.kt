package com.nuvio.app.features.intro

/** iOS sound playback is not wired yet; the intro runs silent there. */
internal actual object IntroSoundController {
    actual fun preload() = Unit
    actual suspend fun awaitReady(timeoutMs: Long) = Unit
    actual fun play() = Unit
    actual fun stop() = Unit
}
