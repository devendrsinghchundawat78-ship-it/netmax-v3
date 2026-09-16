package com.nuvio.app.features.equalizer

actual object PlatformAudioEqualizer {
    actual fun applyState(state: EqualizerState) {}
    actual fun attachAudioSession(sessionId: Int) {}
    actual fun detachAudioSession(sessionId: Int) {}
}
