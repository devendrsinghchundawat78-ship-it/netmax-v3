package com.nuvio.app.features.intro

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.Res

internal actual object IntroSoundController {

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()

    @Volatile
    private var preloadStarted = false

    @Volatile
    private var soundPool: SoundPool? = null

    @Volatile
    private var soundId = 0

    @Volatile
    private var activeStreamId = 0

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun preload() {
        if (preloadStarted) return
        val context = appContext ?: return
        preloadStarted = true
        scope.launch {
            runCatching {
                val bytes = Res.readBytes("files/netmax_intro.wav")
                val cacheFile = File(context.cacheDir, "netmax_intro.wav")
                cacheFile.writeBytes(bytes)

                val pool = SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build(),
                    )
                    .build()
                val loaded = CompletableDeferred<Boolean>()
                pool.setOnLoadCompleteListener { _, _, status ->
                    if (!loaded.isCompleted) {
                        loaded.complete(status == 0)
                    }
                }
                val id = pool.load(cacheFile.absolutePath, 1)
                val ok = withTimeoutOrNull(4_000L) { loaded.await() } == true
                if (ok) {
                    soundId = id
                    soundPool = pool
                } else {
                    runCatching { pool.release() }
                }
            }
            // Ready either way: the intro must never wait on a broken sound.
            ready.complete(Unit)
        }
    }

    actual suspend fun awaitReady(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { ready.await() }
    }

    actual fun play() {
        val pool = soundPool ?: return
        runCatching {
            activeStreamId = pool.play(soundId, 0.9f, 0.9f, 1, 0, 1f)
        }
    }

    actual fun stop() {
        val pool = soundPool ?: return
        runCatching { pool.stop(activeStreamId) }
    }
}
