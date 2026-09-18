package com.lagradost.cloudstream3.utils

object SubtitleHelper {
    data class LanguageMetadata(
        val languageName: String,
        val nativeName: String,
        val IETF_tag: String,
        val ISO_639_1: String,
        val ISO_639_2_B: String,
        val ISO_639_3: String,
        val openSubtitles: String = "",
    )

    val languages = listOf(
        LanguageMetadata("English", "English", "en", "en", "eng", "eng", "en"),
        LanguageMetadata("Hindi", "हिन्दी", "hi", "hi", "hin", "hin", "hi"),
        LanguageMetadata("Spanish", "Español", "es", "es", "spa", "spa", "es"),
        LanguageMetadata("French", "Français", "fr", "fr", "fre", "fra", "fr"),
        LanguageMetadata("German", "Deutsch", "de", "de", "ger", "deu", "de"),
        LanguageMetadata("Italian", "Italiano", "it", "it", "ita", "ita", "it"),
        LanguageMetadata("Portuguese", "Português", "pt", "pt", "por", "por", "pt-pt"),
        LanguageMetadata("Portuguese (Brazil)", "Português (Brasil)", "pt-br", "pt", "por", "por", "pt-br"),
        LanguageMetadata("Russian", "Русский", "ru", "ru", "rus", "rus", "ru"),
        LanguageMetadata("Japanese", "日本語", "ja", "ja", "jpn", "jpn", "ja"),
        LanguageMetadata("Korean", "한국어", "ko", "ko", "kor", "kor", "ko"),
        LanguageMetadata("Chinese", "中文", "zh", "zh", "chi", "zho", "zh"),
        LanguageMetadata("Arabic", "العربية", "ar", "ar", "ara", "ara", "ar"),
        LanguageMetadata("Bengali", "বাংলা", "bn", "bn", "ben", "ben", "bn"),
        LanguageMetadata("Telugu", "తెలుగు", "te", "te", "tel", "tel", "te"),
        LanguageMetadata("Marathi", "मराठी", "mr", "mr", "mar", "mar", "mr"),
        LanguageMetadata("Tamil", "தமிழ்", "ta", "ta", "tam", "tam", "ta"),
        LanguageMetadata("Urdu", "اردو", "ur", "ur", "urd", "urd", "ur"),
        LanguageMetadata("Gujarati", "ગુજરાતી", "gu", "gu", "guj", "guj", "gu"),
        LanguageMetadata("Kannada", "ಕನ್ನಡ", "kn", "kn", "kan", "kan", "kn"),
        LanguageMetadata("Malayalam", "മലയാളം", "ml", "ml", "mal", "mal", "ml"),
        LanguageMetadata("Punjabi", "ਪੰਜਾਬੀ", "pa", "pa", "pan", "pan", "pa"),
        LanguageMetadata("Indonesian", "Bahasa Indonesia", "id", "id", "ind", "ind", "id"),
        LanguageMetadata("Vietnamese", "Tiếng Việt", "vi", "vi", "vie", "vie", "vi"),
        LanguageMetadata("Turkish", "Türkçe", "tr", "tr", "tur", "tur", "tr"),
        LanguageMetadata("Thai", "ไทย", "th", "th", "tha", "tha", "th"),
        LanguageMetadata("Polish", "Polski", "pl", "pl", "pol", "pol", "pl"),
        LanguageMetadata("Ukrainian", "Українська", "uk", "uk", "ukr", "ukr", "uk"),
        LanguageMetadata("Dutch", "Nederlands", "nl", "nl", "dut", "nld", "nl"),
        LanguageMetadata("Greek", "Ελληνικά", "el", "el", "gre", "ell", "el"),
        LanguageMetadata("Czech", "Čeština", "cs", "cs", "cze", "ces", "cs"),
        LanguageMetadata("Swedish", "Svenska", "sv", "sv", "swe", "swe", "sv"),
        LanguageMetadata("Hungarian", "Magyar", "hu", "hu", "hun", "hun", "hu"),
        LanguageMetadata("Romanian", "Română", "ro", "ro", "rum", "ron", "ro"),
        LanguageMetadata("Hebrew", "עברית", "he", "he", "heb", "heb", "he"),
        LanguageMetadata("Danish", "Dansk", "da", "da", "dan", "dan", "da"),
        LanguageMetadata("Finnish", "Suomi", "fi", "fi", "fin", "fin", "fi"),
        LanguageMetadata("Norwegian", "Norsk", "no", "no", "nor", "nor", "no"),
        LanguageMetadata("Tagalog", "Tagalog", "tl", "tl", "tgl", "tgl", "tl"),
        LanguageMetadata("Malay", "Bahasa Melayu", "ms", "ms", "may", "msa", "ms"),
    )

    private val byName = languages.associateBy { it.languageName.lowercase() }
    private val byIso1 = languages.filter { it.ISO_639_1.isNotBlank() }.associateBy { it.ISO_639_1.lowercase() }
    private val byIso2B = languages.filter { it.ISO_639_2_B.isNotBlank() }.associateBy { it.ISO_639_2_B.lowercase() }
    private val byIso3 = languages.filter { it.ISO_639_3.isNotBlank() }.associateBy { it.ISO_639_3.lowercase() }
    private val byIetf = languages.associateBy { it.IETF_tag.lowercase() }

    fun fromTwoLettersToLanguage(input: String): String? {
        val clean = input.trim().lowercase()
        return byIso1[clean]?.languageName ?: byIetf[clean]?.languageName
    }

    fun fromThreeLettersToLanguage(input: String): String? {
        val clean = input.trim().lowercase()
        return byIso3[clean]?.languageName ?: byIso2B[clean]?.languageName
    }

    fun fromCodeToLangTagIETF(languageCode: String?): String? {
        if (languageCode.isNullOrBlank()) return null
        val clean = languageCode.trim().lowercase()
        return byIetf[clean]?.IETF_tag ?: byIso1[clean]?.IETF_tag ?: byIso3[clean]?.IETF_tag ?: clean
    }

    fun fromLanguageToTagIETF(languageName: String?, halfMatch: Boolean? = false): String? {
        if (languageName.isNullOrBlank()) return null
        val clean = languageName.trim().lowercase()
        byName[clean]?.let { return it.IETF_tag }
        byIetf[clean]?.let { return it.IETF_tag }
        byIso1[clean]?.let { return it.IETF_tag }
        byIso3[clean]?.let { return it.IETF_tag }
        if (halfMatch == true) {
            val match = languages.firstOrNull { it.languageName.lowercase().contains(clean) || clean.contains(it.languageName.lowercase()) }
            if (match != null) return match.IETF_tag
        }
        return null
    }
}
