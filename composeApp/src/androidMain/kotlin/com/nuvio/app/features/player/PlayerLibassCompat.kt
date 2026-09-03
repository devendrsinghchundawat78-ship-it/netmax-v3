package com.nuvio.app.features.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.extractor.AssMatroskaExtractor
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import java.util.Collections
import java.util.WeakHashMap

private val assHandlersByPlayer = Collections.synchronizedMap(WeakHashMap<ExoPlayer, AssHandler>())

@OptIn(UnstableApi::class)
internal fun ExoPlayer.Builder.buildWithAssSupportCompat(
    context: Context,
    renderType: AssRenderType = AssRenderType.CUES,
    dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context),
    extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory(),
    renderersFactory: RenderersFactory = DefaultRenderersFactory(context)
): ExoPlayer {
    val assHandler = AssHandler(renderType)
    registerImportedSubtitleFonts(assHandler)
    val assSubtitleParserFactory = CompatAssSubtitleParserFactory(assHandler)
    val assExtractorsFactory = extractorsFactory.withAssMkvSupportCompat(
        subtitleParserFactory = assSubtitleParserFactory,
        assHandler = assHandler
    )

    val mediaSourceFactory = DefaultMediaSourceFactory(
        dataSourceFactory,
        assExtractorsFactory
    )
    mediaSourceFactory.setSubtitleParserFactory(assSubtitleParserFactory)

    val player = this
        .setMediaSourceFactory(mediaSourceFactory)
        .setRenderersFactory(renderersFactory.withAssSupport(assHandler))
        .build()

    assHandlersByPlayer[player] = assHandler

    assHandler.init(player)
    return player
}

internal fun ExoPlayer.getAssHandlerCompat(): AssHandler? = assHandlersByPlayer[this]

/**
 * Registers user-imported fonts (TTF/OTF) with libass so that ASS/SSA scripts
 * can reference them via \fn / Style Fontname. Only called when the user has
 * imported at least one font (keeps libass lazy loading intact otherwise).
 *
 * Each font is registered under a couple of name variants (display name and a
 * compact name) to maximise the chance of matching the script's requested name.
 */
private fun registerImportedSubtitleFonts(assHandler: AssHandler) {
    val importedFonts = runCatching { SubtitleFontStore.listFonts() }.getOrDefault(emptyList())
    if (importedFonts.isEmpty()) return
    importedFonts.forEach { font ->
        val bytes = runCatching { SubtitleFontStore.loadFontBytes(font.path) }.getOrNull() ?: return@forEach
        val compact = font.assFontName.replace(Regex("[^A-Za-z0-9]"), "")
        val names = linkedSetOf(font.assFontName, compact)
        names.filter { it.isNotBlank() }.distinct().forEach { name ->
            runCatching {
                assHandler.ass.addFont(name, bytes)
            }.onFailure {
                android.util.Log.w("AssFontRegister", "Failed to register subtitle font $name", it)
            }
        }
    }
}

@OptIn(UnstableApi::class)
private class CompatAssSubtitleParserFactory(
    private val assHandler: AssHandler
) : SubtitleParser.Factory {
    private val delegate = AssSubtitleParserFactory(assHandler)

    override fun supportsFormat(format: Format): Boolean {
        return delegate.supportsFormat(normalizeSsaFormat(format))
    }

    override fun getCueReplacementBehavior(format: Format): Int {
        return delegate.getCueReplacementBehavior(normalizeSsaFormat(format))
    }

    override fun create(format: Format): SubtitleParser {
        return delegate.create(normalizeSsaFormat(format))
    }

    private fun normalizeSsaFormat(format: Format): Format {
        val isSsaByCodecs = format.codecs == MimeTypes.TEXT_SSA
        val isSsaByMime = format.sampleMimeType == MimeTypes.TEXT_SSA
        if (isSsaByCodecs && !isSsaByMime) {
            return format.buildUpon()
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build()
        }
        return format
    }
}

@OptIn(UnstableApi::class)
private fun ExtractorsFactory.withAssMkvSupportCompat(
    subtitleParserFactory: SubtitleParser.Factory,
    assHandler: AssHandler
): ExtractorsFactory {
    return ExtractorsFactory {
        val extractors = createExtractors()
        extractors.forEachIndexed { index, extractor ->
            if (extractor is MatroskaExtractor) {
                extractors[index] = AssMatroskaExtractor(subtitleParserFactory, assHandler)
            }
        }
        extractors
    }
}
