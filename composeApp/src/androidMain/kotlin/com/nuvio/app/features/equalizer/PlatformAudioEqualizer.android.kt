package com.nuvio.app.features.equalizer

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

actual object PlatformAudioEqualizer {
    private const val TAG = "AudioEqualizer"

    private val activeSessions = mutableSetOf<Int>()
    private val equalizers = mutableMapOf<Int, Equalizer>()
    private val bassBoosts = mutableMapOf<Int, BassBoost>()
    private val virtualizers = mutableMapOf<Int, Virtualizer>()

    private var lastState: EqualizerState? = null

    actual fun applyState(state: EqualizerState) {
        lastState = state
        equalizers.forEach { (_, eq) ->
            applyToEqualizer(eq, state)
        }
        bassBoosts.forEach { (_, bb) ->
            applyToBassBoost(bb, state)
        }
        virtualizers.forEach { (_, virt) ->
            applyToVirtualizer(virt, state)
        }
    }

    actual fun attachAudioSession(sessionId: Int) {
        if (sessionId <= 0 || activeSessions.contains(sessionId)) return
        activeSessions.add(sessionId)

        try {
            val eq = Equalizer(0, sessionId).apply {
                enabled = lastState?.enabled ?: false
            }
            equalizers[sessionId] = eq
            lastState?.let { applyToEqualizer(eq, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Equalizer for session $sessionId: ${e.message}")
        }

        try {
            val bb = BassBoost(0, sessionId).apply {
                enabled = (lastState?.enabled == true && (lastState?.bassBoost ?: 0f) > 0f)
            }
            bassBoosts[sessionId] = bb
            lastState?.let { applyToBassBoost(bb, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize BassBoost for session $sessionId: ${e.message}")
        }

        try {
            val virt = Virtualizer(0, sessionId).apply {
                enabled = (lastState?.enabled == true && (lastState?.virtualizer ?: 0f) > 0f)
            }
            virtualizers[sessionId] = virt
            lastState?.let { applyToVirtualizer(virt, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Virtualizer for session $sessionId: ${e.message}")
        }
    }

    actual fun detachAudioSession(sessionId: Int) {
        activeSessions.remove(sessionId)
        try {
            equalizers.remove(sessionId)?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release Equalizer for session $sessionId")
        }
        try {
            bassBoosts.remove(sessionId)?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release BassBoost for session $sessionId")
        }
        try {
            virtualizers.remove(sessionId)?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release Virtualizer for session $sessionId")
        }
    }

    private fun applyToEqualizer(eq: Equalizer, state: EqualizerState) {
        try {
            eq.enabled = state.enabled
            if (!state.enabled) return

            val numBands = eq.numberOfBands.toInt()
            val minRange = eq.bandLevelRange[0]
            val maxRange = eq.bandLevelRange[1]

            for (i in 0 until numBands) {
                val gainIndex = ((i.toFloat() / (numBands - 1).coerceAtLeast(1)) * (state.bandGainsDb.size - 1)).toInt().coerceIn(0, state.bandGainsDb.lastIndex)
                val gainDb = state.bandGainsDb[gainIndex]
                // Convert dB (-12..+12) to millibels (-1200..+1200)
                val mB = (gainDb * 100f).toInt().coerceIn(minRange.toInt(), maxRange.toInt()).toShort()
                eq.setBandLevel(i.toShort(), mB)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying equalizer gains: ${e.message}")
        }
    }

    private fun applyToBassBoost(bb: BassBoost, state: EqualizerState) {
        try {
            bb.enabled = state.enabled && state.bassBoost > 0f
            if (bb.enabled && bb.strengthSupported) {
                val strength = (state.bassBoost * 1000f).toInt().coerceIn(0, 1000).toShort()
                bb.setStrength(strength)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying bass boost: ${e.message}")
        }
    }

    private fun applyToVirtualizer(virt: Virtualizer, state: EqualizerState) {
        try {
            virt.enabled = state.enabled && state.virtualizer > 0f
            if (virt.enabled && virt.strengthSupported) {
                val strength = (state.virtualizer * 1000f).toInt().coerceIn(0, 1000).toShort()
                virt.setStrength(strength)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying virtualizer: ${e.message}")
        }
    }
}
