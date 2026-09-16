package com.nuvio.app.features.equalizer

enum class EqualizerPreset(
    val id: String,
    val label: String,
    val bandGainsDb: List<Float>, // 10 bands: 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
    val bassBoost: Float = 0f,
    val virtualizer: Float = 0f,
) {
    Flat(
        id = "flat",
        label = "Flat",
        bandGainsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    ),
    Cinema(
        id = "cinema",
        label = "Cinema / Movie",
        bandGainsDb = listOf(6f, 5f, 3f, 0f, 1.5f, 2.5f, 3.5f, 5f, 6.5f, 8f),
        bassBoost = 0.35f,
        virtualizer = 0.40f,
    ),
    BassBoost(
        id = "bass_boost",
        label = "Bass Boost",
        bandGainsDb = listOf(9f, 8f, 5f, 2f, 0f, -1f, 0f, 2f, 4f, 6f),
        bassBoost = 0.60f,
    ),
    Voice(
        id = "voice",
        label = "Vocal / Dialogue",
        bandGainsDb = listOf(-5f, -3f, 0f, 4f, 7f, 7.5f, 4f, 2f, 0f, -2f),
        virtualizer = 0.15f,
    ),
    PureClarity(
        id = "clarity",
        label = "Pure Clarity",
        bandGainsDb = listOf(-2f, -1f, 0f, 1f, 3f, 5f, 6f, 5f, 3f, 2f),
        virtualizer = 0.25f,
    ),
    Rock(
        id = "rock",
        label = "Rock",
        bandGainsDb = listOf(6f, 4.5f, 3f, 1f, -2f, 2.5f, 4f, 5f, 6.5f, 7.5f),
        bassBoost = 0.25f,
    ),
    Pop(
        id = "pop",
        label = "Pop",
        bandGainsDb = listOf(-3f, 0f, 2f, 3.5f, 5f, 4.5f, 3f, 1.5f, -1f, -2.5f),
        virtualizer = 0.20f,
    ),
    Electronic(
        id = "electronic",
        label = "Electronic / EDM",
        bandGainsDb = listOf(7f, 5.5f, 2.5f, -1f, -3f, 1f, 3.5f, 6f, 8f, 9.5f),
        bassBoost = 0.45f,
        virtualizer = 0.30f,
    ),
    SoftBass(
        id = "soft_bass",
        label = "Soft Bass",
        bandGainsDb = listOf(4f, 3.5f, 2.8f, 1.6f, 0.6f, 0.4f, 1.2f, 1.8f, 2.2f, 2.6f),
        bassBoost = 0.20f,
    ),
    Jazz(
        id = "jazz",
        label = "Jazz",
        bandGainsDb = listOf(3f, 2f, 1.2f, 2.8f, 4f, 3.6f, 2.4f, 3.6f, 4.4f, 4f),
        virtualizer = 0.20f,
    );

    companion object {
        val FREQUENCY_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    }
}

data class EqualizerState(
    val enabled: Boolean = false,
    val isAdvanced: Boolean = false,
    val currentPreset: EqualizerPreset = EqualizerPreset.Flat,
    val bandGainsDb: List<Float> = EqualizerPreset.Flat.bandGainsDb,
    val bass: Float = 0f,      // -10f to +10f in simple mode
    val mid: Float = 0f,       // -10f to +10f in simple mode
    val treble: Float = 0f,    // -10f to +10f in simple mode
    val bassBoost: Float = 0f, // 0f to 1f
    val virtualizer: Float = 0f, // 0f to 1f (3D Surround)
)
