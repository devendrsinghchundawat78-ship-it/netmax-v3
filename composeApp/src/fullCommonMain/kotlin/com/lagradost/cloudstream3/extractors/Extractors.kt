package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi

open class Vidmoly : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.me"
    override val requiresReferer = false
}

open class StreamSB : ExtractorApi() {
    override val name = "StreamSB"
    override val mainUrl = "https://streamsb.net"
    override val requiresReferer = false
}

open class StreamTape : ExtractorApi() {
    override val name = "StreamTape"
    override val mainUrl = "https://streamtape.com"
    override val requiresReferer = false
}

open class Doodstream : ExtractorApi() {
    override val name = "Doodstream"
    override val mainUrl = "https://dood.to"
    override val requiresReferer = false
}

open class FPlayer : ExtractorApi() {
    override val name = "FPlayer"
    override val mainUrl = "https://fplayer.info"
    override val requiresReferer = false
}

open class Voe : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = false
}

open class MixDrop : ExtractorApi() {
    override val name = "MixDrop"
    override val mainUrl = "https://mixdrop.co"
    override val requiresReferer = false
}

open class Filemoon : ExtractorApi() {
    override val name = "Filemoon"
    override val mainUrl = "https://filemoon.sx"
    override val requiresReferer = false
}

open class StreamWishExtractor : ExtractorApi() {
    override val name = "StreamWish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = false
}
