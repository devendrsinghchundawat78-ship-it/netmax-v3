package com.nuvio.app.features.equalizer

expect object PlatformAudioEqualizer {
    fun applyState(state: EqualizerState)
    fun attachAudioSession(sessionId: Int)
    fun detachAudioSession(sessionId: Int)
}
