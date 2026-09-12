# Project-specific ProGuard rules for composeApp Android release builds.

# Keep useful metadata for crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata/signatures needed by reflection/generics-heavy libraries.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations

# Ktor / Supabase client stack (runtime reflective paths in serializers/plugins).
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep @Serializable generated serializers.
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class com.nuvio.app.features.catalog.CatalogTargetKind { *; }

# Avoid R8 merging/optimizing the stream badge chip used in lazy stream rows.
-keep class com.nuvio.app.features.streams.StreamBadgeChipKt { *; }
-keep class com.nuvio.app.features.streams.StreamBadgeChipSize { *; }
-keep class com.nuvio.app.features.streams.StreamBadgeChipDefaults { *; }

-keep class com.nuvio.app.features.streams.StreamsScreenKt { *; }
-keep class com.nuvio.app.features.streams.StreamsScreenKt$* { *; }

# Avoid R8 producing verifier-invalid bytecode for the large player composable.
-keep class com.nuvio.app.features.player.PlayerScreenKt { *; }
-keep class com.nuvio.app.features.player.PlayerScreenKt$* { *; }

# QuickJS plugin runtime is dynamic; keep runtime and app plugin classes.
-keep class com.dokar.quickjs.** { *; }
-keep class com.nuvio.app.features.plugins.** { *; }

# P2P runtime and Nuvio Engine JNI bridge. Native libraries are not processed
# by R8, but their Kotlin/JNI wrapper classes and method names must stay stable.
-keep class com.nuvio.app.features.p2p.** { *; }
-keep class com.nuvio.engine.** { *; }
-keep interface com.nuvio.engine.** { *; }

-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# Media3 / ExoPlayer classes from local AAR decoders and stock modules.
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }

-keep class is.xyz.mpv.** { *; }
-keep interface is.xyz.mpv.** { *; }

# Common optional security providers used by okhttp on some devices.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# CloudStream 3 runtime, extensions, reflection and serializers
-keep class com.lagradost.** { *; }
-keep interface com.lagradost.** { *; }
-dontwarn com.lagradost.**

-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Coil 3 SVG engine (AndroidSVG) - decodes remote SVG artwork such as the
# Torbox/Premiumize provider logos. Keep it intact for release (R8) builds.
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# ASS subtitle parsing (peerless2012 ass-media / ass-kt) used by the player
# for imported subtitle fonts.
-keep class io.github.peerless2012.** { *; }
-dontwarn io.github.peerless2012.**

# KSoup HTML parser used by the plugin scrapers.
-keep class com.fleeksoft.ksoup.** { *; }
-dontwarn com.fleeksoft.ksoup.**

# Keep kotlinx.serialization's generated serializer so the AI/history payloads
# keep deserialising in minified release builds (R8 full mode can drop it and
# then every @Serializable decode throws at runtime).
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class *$$serializer {
    ** INSTANCE;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# NetMax AI: same R8 hardening already applied to StreamsScreenKt/PlayerScreenKt.
# The whole chat is one large composable with nested lambdas and a keyed LazyColumn; R8
# inlining/merging could produce verifier-invalid bytecode, which fatal-crashes the screen
# the moment it is opened from the Home FAB or Settings.
# The whole package is kept (not just the screen): the first thing the screen does is
# history() -> NetmaxGuestAccess.ensureSession()/guestId() (kotlin.uuid.Uuid + Mutex +
# Supabase auth session) and NetmaxAiServiceKt's top-level parse helpers — these run
# inside the first LaunchedEffect, so broken bytecode there crashes on open too.
-keep class com.nuvio.app.features.netmax.** { *; }
-keep class kotlin.uuid.** { *; }

# ─── Liquid Glass (Backdrop library) hardening ────────────────────────────────
# The Backdrop library ships no consumer ProGuard rules of its own. Its render
# pipeline is driven from draw callbacks (DrawModifierNode / GraphicsLayer), the
# exact pattern R8's inlining has already broken twice in this app (StreamsScreenKt,
# PlayerScreenKt → verifier-invalid bytecode, fatal crash on first composition).
-keep class com.kyant.backdrop.** { *; }
-keep class com.kyant.shapes.** { *; }

# Composables that host the liquid-glass surfaces (nav pill + sliding lens, FAB,
# poster long-press action menu, continue-watching cards). Same hardening pattern
# as the screens above: keep the composable class + all its lambda/synthetic
# classes so R8 cannot inline or merge the draw/click paths.
-keep class com.nuvio.app.MainTabsDestinationKt { *; }
-keep class com.nuvio.app.MainTabsDestinationKt$* { *; }
-keep class com.nuvio.app.core.ui.NavigationBarKt { *; }
-keep class com.nuvio.app.core.ui.NavigationBarKt$* { *; }
-keep class com.nuvio.app.core.ui.BackdropLiquidGlassKt { *; }
-keep class com.nuvio.app.core.ui.BackdropLiquidGlassKt$* { *; }
-keep class com.nuvio.app.core.ui.PosterZoomActionOverlayKt { *; }
-keep class com.nuvio.app.core.ui.PosterZoomActionOverlayKt$* { *; }
-keep class com.nuvio.app.core.ui.LiquidGlassKt { *; }
-keep class com.nuvio.app.core.ui.LiquidGlassKt$* { *; }
-keep class com.nuvio.app.MainAppContentKt { *; }
-keep class com.nuvio.app.MainAppContentKt$* { *; }
-keep class com.nuvio.app.features.details.MetaDetailsScreenKt { *; }
-keep class com.nuvio.app.features.details.MetaDetailsScreenKt$* { *; }
-keep class com.nuvio.app.features.home.components.HomeContinueWatchingSectionKt { *; }
-keep class com.nuvio.app.features.home.components.HomeContinueWatchingSectionKt$* { *; }
