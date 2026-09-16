package com.nuvio.app.features.equalizer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object EqualizerSettingsRepository {
    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        PlatformAudioEqualizer.applyState(_state.value)
    }

    fun setAdvancedMode(isAdvanced: Boolean) {
        _state.update { it.copy(isAdvanced = isAdvanced) }
    }

    fun applyPreset(preset: EqualizerPreset) {
        _state.update {
            it.copy(
                currentPreset = preset,
                bandGainsDb = preset.bandGainsDb,
                bass = (preset.bandGainsDb[1] / 1.2f).coerceIn(-10f, 10f),
                mid = ((preset.bandGainsDb[4] + preset.bandGainsDb[5]) / 2.4f).coerceIn(-10f, 10f),
                treble = (preset.bandGainsDb[8] / 1.2f).coerceIn(-10f, 10f),
                bassBoost = preset.bassBoost,
                virtualizer = preset.virtualizer,
            )
        }
        PlatformAudioEqualizer.applyState(_state.value)
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        val clampedGain = gainDb.coerceIn(-12f, 12f)
        val currentGains = _state.value.bandGainsDb.toMutableList()
        if (bandIndex in currentGains.indices) {
            currentGains[bandIndex] = clampedGain
            _state.update {
                it.copy(
                    bandGainsDb = currentGains,
                    bass = (currentGains[1] / 1.2f).coerceIn(-10f, 10f),
                    mid = ((currentGains[4] + currentGains[5]) / 2.4f).coerceIn(-10f, 10f),
                    treble = (currentGains[8] / 1.2f).coerceIn(-10f, 10f),
                )
            }
            PlatformAudioEqualizer.applyState(_state.value)
        }
    }

    fun setSimpleKnobs(bass: Float, mid: Float, treble: Float) {
        val b = bass.coerceIn(-10f, 10f)
        val m = mid.coerceIn(-10f, 10f)
        val t = treble.coerceIn(-10f, 10f)

        // Map 3 sliders to 10 bands organically matching Convx curve
        val newGains = listOf(
            (b * 1.15f).coerceIn(-12f, 12f),
            (b * 1.0f).coerceIn(-12f, 12f),
            (b * 0.7f + m * 0.3f).coerceIn(-12f, 12f),
            (b * 0.25f + m * 0.75f).coerceIn(-12f, 12f),
            (m * 1.0f).coerceIn(-12f, 12f),
            (m * 1.0f).coerceIn(-12f, 12f),
            (m * 0.75f + t * 0.25f).coerceIn(-12f, 12f),
            (m * 0.3f + t * 0.7f).coerceIn(-12f, 12f),
            (t * 1.0f).coerceIn(-12f, 12f),
            (t * 1.2f).coerceIn(-12f, 12f),
        )

        _state.update {
            it.copy(
                bass = b,
                mid = m,
                treble = t,
                bandGainsDb = newGains,
            )
        }
        PlatformAudioEqualizer.applyState(_state.value)
    }

    fun setBassBoost(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _state.update { it.copy(bassBoost = v) }
        PlatformAudioEqualizer.applyState(_state.value)
    }

    fun setVirtualizer(value: Float) {
        val v = value.coerceIn(0f, 1f)
        _state.update { it.copy(virtualizer = v) }
        PlatformAudioEqualizer.applyState(_state.value)
    }

    fun reset() {
        applyPreset(EqualizerPreset.Flat)
    }
}
