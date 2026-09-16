package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.NuvioClassicNavigationBar
import com.nuvio.app.core.ui.NuvioNavigationBar
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.rememberNuvioNavBarScrollState
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.music.MusicSettingsRepository
import com.nuvio.app.features.music.ui.MusicFullPlayerSheet
import com.nuvio.app.features.music.ui.MusicMiniPlayer
import com.nuvio.app.features.quickwatch.QuickWatchSettings
import com.nuvio.app.features.quickwatch.QuickWatchSettingsRepository
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_music
import nuvio.composeapp.generated.resources.compose_nav_quick_watch
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_profile
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_music
import nuvio.composeapp.generated.resources.sidebar_quick_watch
import nuvio.composeapp.generated.resources.sidebar_search
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MainTabsDestination(
    selectedTab: AppScreenTab,
    initialHomeReady: Boolean,
    rootRouteActive: Boolean,
    useTabletFloatingTabBar: Boolean,
    useNativeNavigation: Boolean,
    useNativeTabBar: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    requests: AppTabRequests,
    state: AppTabState,
    actions: (isTabletLayout: Boolean) -> AppTabActions,
    onBack: () -> Unit,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    onOpenNetmaxAi: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onBack)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLayout = useTabletFloatingTabBar || maxWidth >= 768.dp
        val useNativeBottomTabs = if (useNativeNavigation) {
            useNativeTabBar
        } else {
            liquidGlassNativeTabBarSupported && liquidGlassNativeTabBarEnabled && initialHomeReady
        }
        val tabsRouteActive = rootRouteActive
        val navBarScrollState = rememberNuvioNavBarScrollState()
        val navBarHazeState = rememberHazeState()
        val navBarStyleSetting by remember { ThemeSettingsRepository.navBarStyle }.collectAsStateWithLifecycle()
        QuickWatchSettingsRepository.ensureLoaded()
        val quickWatchSettings by QuickWatchSettingsRepository.settings.collectAsStateWithLifecycle()
        MusicSettingsRepository.ensureLoaded()
        val musicSettings by MusicSettingsRepository.settings.collectAsStateWithLifecycle()
        val swipeTabs = remember(quickWatchSettings.enabled, musicSettings.enabled) {
            buildList {
                add(AppScreenTab.Home)
                add(AppScreenTab.Search)
                if (quickWatchSettings.enabled) add(AppScreenTab.QuickWatch)
                if (musicSettings.enabled) add(AppScreenTab.Music)
                add(AppScreenTab.Library)
                add(AppScreenTab.Settings)
            }
        }
        androidx.compose.runtime.LaunchedEffect(quickWatchSettings.enabled, musicSettings.enabled, selectedTab) {
            if (!quickWatchSettings.enabled && selectedTab == AppScreenTab.QuickWatch) {
                onTabSelected(AppScreenTab.Home)
            }
            if (!musicSettings.enabled && selectedTab == AppScreenTab.Music) {
                onTabSelected(AppScreenTab.Home)
            }
        }
        fun switchTabBySwipe(delta: Int) {
            val index = swipeTabs.indexOf(selectedTab)
            val targetIndex = (index + delta).coerceIn(0, swipeTabs.lastIndex)
            if (targetIndex != index) onTabSelected(swipeTabs[targetIndex])
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (initialHomeReady) 1f else 0f),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!isTabletLayout && !useNativeBottomTabs && navBarStyleSetting == NavBarStyle.CLASSIC) {
                    NuvioClassicNavigationBar {
                        NavItem(
                            selected = selectedTab == AppScreenTab.Home,
                            onClick = { onTabSelected(AppScreenTab.Home) },
                            icon = Icons.Filled.Home,
                            contentDescription = stringResource(Res.string.compose_nav_home),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Search,
                            onClick = { onTabSelected(AppScreenTab.Search) },
                            icon = Res.drawable.sidebar_search,
                            contentDescription = stringResource(Res.string.compose_nav_search),
                        )
                        if (quickWatchSettings.enabled) {
                            NavItem(
                                selected = selectedTab == AppScreenTab.QuickWatch,
                                onClick = { onTabSelected(AppScreenTab.QuickWatch) },
                                icon = Res.drawable.sidebar_quick_watch,
                                contentDescription = stringResource(Res.string.compose_nav_quick_watch),
                            )
                        }
                        if (musicSettings.enabled) {
                            NavItem(
                                selected = selectedTab == AppScreenTab.Music,
                                onClick = { onTabSelected(AppScreenTab.Music) },
                                icon = Res.drawable.sidebar_music,
                                contentDescription = stringResource(Res.string.compose_nav_music),
                            )
                        }
                        NavItem(
                            selected = selectedTab == AppScreenTab.Library,
                            onClick = { onTabSelected(AppScreenTab.Library) },
                            icon = Res.drawable.sidebar_library,
                            contentDescription = stringResource(Res.string.compose_nav_library),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Settings,
                            onClick = { onTabSelected(AppScreenTab.Settings) },
                        ) {
                            ProfileSwitcherTab(
                                selected = selectedTab == AppScreenTab.Settings,
                                onClick = { onTabSelected(AppScreenTab.Settings) },
                                onProfileSelected = onProfileSelected,
                                onAddProfileRequested = onAddProfileRequested,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNuvioBottomNavigationOverlayPadding provides if (useNativeBottomTabs) 49.dp else if (!isTabletLayout && navBarStyleSetting != NavBarStyle.CLASSIC) 72.dp else 0.dp,
                    LocalNuvioNavBarScrollState provides navBarScrollState,
                ) {
                    AppTabHost(
                        selectedTab = selectedTab,
                        requests = requests,
                        state = state,
                        actions = actions(isTabletLayout),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (navBarStyleSetting != NavBarStyle.CLASSIC) Modifier.hazeSource(state = navBarHazeState) else Modifier)
                            .then(if (navBarStyleSetting == NavBarStyle.ADAPTIVE) Modifier.nestedScroll(navBarScrollState.nestedScrollConnection) else Modifier)
                            .padding(innerPadding),
                    )
                }

                if (selectedTab == AppScreenTab.Home && !useNativeNavigation) {
                    FloatingActionButton(
                        onClick = onOpenNetmaxAi,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = if (isTabletLayout) 18.dp else 94.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "NetMax AI")
                    }
                }

                if (isTabletLayout && !useNativeBottomTabs) {
                    TabletFloatingTopBar(
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        onProfileSelected = onProfileSelected,
                        onAddProfileRequested = onAddProfileRequested,
                    )
                }

                if (!isTabletLayout && !useNativeBottomTabs && navBarStyleSetting != NavBarStyle.CLASSIC) {
                    LaunchedEffect(navBarStyleSetting) {
                        when (navBarStyleSetting) {
                            NavBarStyle.EXPANDED -> navBarScrollState.expand()
                            NavBarStyle.COMPACT -> navBarScrollState.collapse()
                            else -> {}
                        }
                    }
                    val currentTabIndex = swipeTabs.indexOf(selectedTab).coerceAtLeast(0)
                    NuvioNavigationBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        scrollState = navBarScrollState,
                        hazeState = navBarHazeState,
                        selectedTabIndex = currentTabIndex,
                        tabsCount = swipeTabs.size,
                        onTabSelected = { index ->
                            if (index in swipeTabs.indices) {
                                onTabSelected(swipeTabs[index])
                            }
                        },
                        onSwipeLeft = { switchTabBySwipe(1) },
                        onSwipeRight = { switchTabBySwipe(-1) },
                    ) {
                        NavItem(
                            selected = selectedTab == AppScreenTab.Home,
                            onClick = { onTabSelected(AppScreenTab.Home) },
                            icon = Icons.Filled.Home,
                            contentDescription = stringResource(Res.string.compose_nav_home),
                            label = stringResource(Res.string.compose_nav_home),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Search,
                            onClick = { onTabSelected(AppScreenTab.Search) },
                            icon = Res.drawable.sidebar_search,
                            contentDescription = stringResource(Res.string.compose_nav_search),
                            label = stringResource(Res.string.compose_nav_search),
                        )
                        if (quickWatchSettings.enabled) {
                            NavItem(
                                selected = selectedTab == AppScreenTab.QuickWatch,
                                onClick = { onTabSelected(AppScreenTab.QuickWatch) },
                                icon = Res.drawable.sidebar_quick_watch,
                                contentDescription = stringResource(Res.string.compose_nav_quick_watch),
                                label = stringResource(Res.string.compose_nav_quick_watch),
                            )
                        }
                        if (musicSettings.enabled) {
                            NavItem(
                                selected = selectedTab == AppScreenTab.Music,
                                onClick = { onTabSelected(AppScreenTab.Music) },
                                icon = Res.drawable.sidebar_music,
                                contentDescription = stringResource(Res.string.compose_nav_music),
                                label = stringResource(Res.string.compose_nav_music),
                            )
                        }
                        NavItem(
                            selected = selectedTab == AppScreenTab.Library,
                            onClick = { onTabSelected(AppScreenTab.Library) },
                            icon = Res.drawable.sidebar_library,
                            contentDescription = stringResource(Res.string.compose_nav_library),
                            label = stringResource(Res.string.compose_nav_library),
                        )
                        NavItem(
                            selected = selectedTab == AppScreenTab.Settings,
                            onClick = { onTabSelected(AppScreenTab.Settings) },
                            label = stringResource(Res.string.compose_nav_profile),
                        ) {
                            ProfileSwitcherTab(
                                selected = selectedTab == AppScreenTab.Settings,
                                onClick = { onTabSelected(AppScreenTab.Settings) },
                                onProfileSelected = onProfileSelected,
                                onAddProfileRequested = onAddProfileRequested,
                            )
                        }
                    }
                }

                if (!isTabletLayout && !useNativeBottomTabs) {
                    MusicMiniPlayer(
                        hazeState = navBarHazeState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (navBarStyleSetting == NavBarStyle.CLASSIC) 64.dp else 84.dp),
                    )
                }
                MusicFullPlayerSheet(hazeState = navBarHazeState)
            }
        }
    }
}
