package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.app.core.ui.NativeProfileSwitcherController
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.configurePlatformImageLoader
import com.nuvio.app.features.intro.NetMaxIntroOverlay
import com.nuvio.app.features.intro.NetMaxIntroSession
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.navigation.AppRoute
import com.nuvio.app.navigation.TabsRoute

fun disposeRoute(route: AppRoute) {
    disposeRouteResources(route)
}

@OptIn(ExperimentalCoilApi::class)
@Composable
@Preview
fun App(
    initialTab: AppScreenTab = AppScreenTab.Home,
    initialRoute: AppRoute = TabsRoute,
    useNativeNavigation: Boolean = false,
    useNativeTabBar: Boolean = false,
    useTabletFloatingTabBar: Boolean = false,
    ownsAppRuntime: Boolean = true,
    bypassAppGate: Boolean = false,
    onNavigate: ((AppRoute, launchSingleTop: Boolean) -> Unit)? = null,
    onGoBack: (() -> Unit)? = null,
    onReplace: ((AppRoute) -> Unit)? = null,
    onActivate: ((AppScreenTab) -> Unit)? = null,
    onAppReady: ((Boolean) -> Unit)? = null,
    onTabTitles: ((home: String, search: String, library: String, profile: String, switchProfile: String, addProfile: String) -> Unit)? = null,
    nativeProfileSwitcherController: NativeProfileSwitcherController? = null,
    appGateController: AppGateController? = null,
) {
    AppEnvironment {
        // Cinematic NetMax intro on cold start, layered above the app so its
        // final fade lands directly on the Home screen.
        val isPreview = LocalInspectionMode.current
        var showIntro by remember {
            mutableStateOf(!NetMaxIntroSession.playedThisProcess && !isPreview)
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AppGate(
                initialTab = initialTab,
                initialRoute = initialRoute,
                useNativeNavigation = useNativeNavigation,
                useNativeTabBar = useNativeTabBar,
                useTabletFloatingTabBar = useTabletFloatingTabBar,
                ownsAppRuntime = ownsAppRuntime,
                bypassAppGate = bypassAppGate,
                renderMainContent = true,
                onNavigate = onNavigate,
                onGoBack = onGoBack,
                onReplace = onReplace,
                onActivate = onActivate,
                onAppReady = onAppReady,
                onMainContentMountChanged = null,
                onMainContentVisibleChanged = null,
                onTabTitles = onTabTitles,
                nativeProfileSwitcherController = nativeProfileSwitcherController,
                appGateController = appGateController,
            )
            if (showIntro) {
                NetMaxIntroOverlay(onFinished = { showIntro = false })
            }
        }
    }
}

@Composable
internal fun AppEnvironment(content: @Composable () -> Unit) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                add(SvgDecoder.Factory())
                add(
                    coil3.network.ktor3.KtorNetworkFetcherFactory(
                        cacheStrategy = { coil3.network.cachecontrol.CacheControlCacheStrategy() },
                    ),
                )
            }
            .configurePlatformImageLoader()
            .build()
    }
    val selectedTheme by remember {
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.selectedTheme
    }.collectAsStateWithLifecycle()
    val themeMode by remember {
        ThemeSettingsRepository.themeMode
    }.collectAsStateWithLifecycle()
    val amoledEnabled by remember {
        ThemeSettingsRepository.amoledEnabled
    }.collectAsStateWithLifecycle()
    // Observing the colour is what recomposes the whole app when the user applies a custom theme.
    val customThemeAccent by remember {
        ThemeSettingsRepository.customThemeAccentHex
    }.collectAsStateWithLifecycle()

    NuvioTheme(
        appTheme = selectedTheme,
        themeMode = themeMode,
        amoled = amoledEnabled,
        customThemeAccentHex = customThemeAccent,
    ) {
        content()
    }
}
