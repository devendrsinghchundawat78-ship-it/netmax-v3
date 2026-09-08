package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.LiquidGlassSettingsRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Scroll-aware state for the floating navigation bar.
 * Tracks scroll direction and exposes a label visibility fraction (1 = fully visible, 0 = hidden).
 */
@Stable
class NuvioNavBarScrollState {
    /** 1f = labels fully visible (expanded), 0f = labels hidden (collapsed, icons only) */
    var labelVisibility by mutableFloatStateOf(1f)
        private set

    private var accumulatedDelta = 0f

    /** Call to expand (show labels) – e.g. when user scrolls back to top */
    fun expand() {
        labelVisibility = 1f
        accumulatedDelta = 0f
    }

    /** Call to collapse (hide labels) */
    fun collapse() {
        labelVisibility = 0f
        accumulatedDelta = 0f
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val deltaY = available.y
            if (deltaY == 0f) return Offset.Zero

            accumulatedDelta += deltaY

            if (accumulatedDelta < -SCROLL_THRESHOLD && labelVisibility != 0f) {
                // Scrolling down past threshold → snap collapse
                labelVisibility = 0f
                accumulatedDelta = 0f
            } else if (accumulatedDelta > SCROLL_THRESHOLD && labelVisibility != 1f) {
                // Scrolling up past threshold → snap expand
                labelVisibility = 1f
                accumulatedDelta = 0f
            }

            // Reset accumulator if direction changed
            if (deltaY < 0f && accumulatedDelta > 0f) accumulatedDelta = deltaY
            if (deltaY > 0f && accumulatedDelta < 0f) accumulatedDelta = deltaY

            return Offset.Zero // Don't consume any scroll
        }
    }

    companion object {
        private const val SCROLL_THRESHOLD = 60f
    }
}

@Composable
fun rememberNuvioNavBarScrollState(): NuvioNavBarScrollState {
    return androidx.compose.runtime.remember { NuvioNavBarScrollState() }
}

/**
 * Floating pill-shaped navigation bar with scroll-responsive labels.
 *
 * @param hazeState Optional [HazeState] whose source is placed on the content behind this bar.
 *                  When provided, the pill gets a blur-through effect.
 */
@Composable
fun NuvioNavigationBar(
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    hazeState: HazeState? = null,
    glassBackdrop: Backdrop? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    content: @Composable NuvioNavigationBarScope.() -> Unit,
) {
    val liquidGlassEnabled by ThemeSettingsRepository.liquidGlassNativeTabBarEnabled.collectAsStateWithLifecycle()
    val useBackdropGlass = glassBackdrop != null && liquidGlassEnabled
    val selectedTabBounds = remember { mutableStateOf<Rect?>(null) }

    val labelFraction by animateFloatAsState(
        targetValue = scrollState?.labelVisibility ?: 1f,
        animationSpec = tween(
            durationMillis = NuvioTokens.Motion.sheetEnterMillis,
            easing = NuvioTokens.Motion.standard,
        ),
        label = "nav_label_alpha",
    )

    val navigationBarInsets = nuvioBottomNavigationBarInsets()
    val bottomSafePadding = navigationBarInsets.asPaddingValues().calculateBottomPadding()

    // Dynamic horizontal padding: pill shrinks when labels are hidden — driven by same labelFraction
    val expandedHorizontalPadding = 28.dp
    val collapsedHorizontalPadding = 58.dp
    val horizontalPadding = expandedHorizontalPadding + (collapsedHorizontalPadding - expandedHorizontalPadding) * (1f - labelFraction)

    // Outer container — no background, just safe padding
    Box(
        modifier = modifier
            .pointerInput(onSwipeLeft, onSwipeRight) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount -> dragDistance += dragAmount },
                    onDragEnd = {
                        when {
                            dragDistance <= -72f -> onSwipeLeft?.invoke()
                            dragDistance >= 72f -> onSwipeRight?.invoke()
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                )
            }
            .fillMaxWidth()
            .padding(bottom = bottomSafePadding + nuvioBottomNavigationExtraVerticalPadding + NuvioTokens.Space.s8),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val glassShape = RoundedCornerShape(NuvioTokens.Radius.full)
        val pillModifier = if (useBackdropGlass) {
            Modifier
                .padding(horizontal = horizontalPadding)
                .fillMaxWidth()
                .backdropLiquidGlass(
                    backdrop = glassBackdrop,
                    shape = glassShape,
                    fallbackColor = MaterialTheme.nuvio.colors.surface.copy(alpha = 0.94f),
                    contentDimAlpha = 0.20f,
                )
        } else {
            Modifier
                .padding(horizontal = horizontalPadding)
                .fillMaxWidth()
                .liquidGlass(
                    shape = glassShape,
                    hazeState = hazeState,
                    isEnabled = liquidGlassEnabled,
                    borderWidth = 1.2.dp,
                )
        }

        Box(modifier = pillModifier) {
            if (useBackdropGlass) {
                SlidingGlassTabIndicator(
                    backdrop = glassBackdrop,
                    selectedBounds = selectedTabBounds.value,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = NuvioTokens.Space.s6,
                        vertical = NuvioTokens.Space.s4,
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NuvioNavigationBarScopeImpl(
                    rowScope = this,
                    labelFraction = labelFraction,
                    glassIndicatorActive = useBackdropGlass,
                    selectedBoundsState = selectedTabBounds,
                ).content()
            }
        }
    }
}

interface NuvioNavigationBarScope {
    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        label: String? = null,
    )

    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        label: String? = null,
    )

    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        label: String? = null,
        content: @Composable () -> Unit,
    )
}

/**
 * Liquid-glass lens that slides between the navigation tabs. The selected tab
 * reports its bounds; this indicator springs to that position, refracting the
 * content behind the bar (Backdrop lens effect) as it travels.
 */
@Composable
private fun SlidingGlassTabIndicator(
    backdrop: Backdrop?,
    selectedBounds: Rect?,
) {
    if (backdrop == null) return
    val tokens = MaterialTheme.nuvio
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates -> hostOrigin = coordinates.positionInRoot() },
    ) {
        val bounds = selectedBounds
        if (bounds != null && bounds.width > 0f && bounds.height > 0f) {
            val density = LocalDensity.current
            val indicatorWidth = with(density) { bounds.width.toDp() }
            val indicatorHeight = with(density) { bounds.height.toDp() }
            val targetX = bounds.left - hostOrigin.x
            val targetY = bounds.top - hostOrigin.y
            val slideSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 480f)
            val animatedX by animateFloatAsState(
                targetValue = targetX,
                animationSpec = slideSpec,
                label = "nav_glass_slide_x",
            )
            val animatedY by animateFloatAsState(
                targetValue = targetY,
                animationSpec = slideSpec,
                label = "nav_glass_slide_y",
            )
            Box(
                modifier = Modifier
                    .size(indicatorWidth, indicatorHeight)
                    .backdropLiquidGlass(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(NuvioTokens.Radius.full),
                        fallbackColor = tokens.colors.accent.copy(alpha = NuvioTokens.Opacity.selected),
                        contentDimAlpha = 0.06f,
                        tintAlphaOverride = 0.08f,
                        layerBlock = {
                            translationX = animatedX
                            translationY = animatedY
                        },
                    ),
            )
        }
    }
}

private class NuvioNavigationBarScopeImpl(
    private val rowScope: androidx.compose.foundation.layout.RowScope,
    private val labelFraction: Float,
    private val glassIndicatorActive: Boolean,
    private val selectedBoundsState: MutableState<Rect?>,
) : NuvioNavigationBarScope {

    /**
     * Cell background: when the sliding glass lens is active it carries the
     * selection highlight, so the per-item accent pill is skipped and the
     * selected item reports its bounds so the lens can slide to it.
     */
    private fun Modifier.tabCell(selected: Boolean, selectedBgColor: Color): Modifier = this
        .clip(RoundedCornerShape(NuvioTokens.Radius.full))
        .then(if (glassIndicatorActive) Modifier else Modifier.background(selectedBgColor))
        .onGloballyPositioned { coordinates ->
            if (selected) selectedBoundsState.value = coordinates.boundsInRoot()
        }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val tokens = MaterialTheme.nuvio
        val palette = ThemeColors.getColorPalette(MaterialTheme.appTheme)
        LiquidGlassSettingsRepository.ensureLoaded()
        val glassSettings by LiquidGlassSettingsRepository.uiState.collectAsStateWithLifecycle()
        val glassTextColor = if (glassSettings.enabled) glassSettings.textColor else tokens.colors.textMuted
        val iconColor by animateColorAsState(
            targetValue = if (selected) tokens.colors.accent else tokens.colors.textMuted,
            label = "nav_icon_color",
        )
        // Selected item gets a pill-shaped highlight using accent at low opacity
        val selectedBgColor by animateColorAsState(
            targetValue = if (selected) tokens.colors.accent.copy(alpha = NuvioTokens.Opacity.selected)
            else Color.Transparent,
            label = "nav_bg_color",
        )

        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .tabCell(selected = selected, selectedBgColor = selectedBgColor)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(vertical = NuvioTokens.Space.s6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier
                        .size(28.dp)
                        .then(if (selected) Modifier.gradientMask(palette.accentBrush()) else Modifier),
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (selected) Color.White else iconColor,
                )
                NavItemLabel(label = label, labelFraction = labelFraction, iconColor = if (selected) iconColor else glassTextColor, selected = selected)
            }
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val tokens = MaterialTheme.nuvio
        val palette = ThemeColors.getColorPalette(MaterialTheme.appTheme)
        LiquidGlassSettingsRepository.ensureLoaded()
        val glassSettings by LiquidGlassSettingsRepository.uiState.collectAsStateWithLifecycle()
        val glassTextColor = if (glassSettings.enabled) glassSettings.textColor else tokens.colors.textMuted
        val iconColor by animateColorAsState(
            targetValue = if (selected) tokens.colors.accent else tokens.colors.textMuted,
            label = "nav_icon_color",
        )
        val selectedBgColor by animateColorAsState(
            targetValue = if (selected) tokens.colors.accent.copy(alpha = NuvioTokens.Opacity.selected)
            else Color.Transparent,
            label = "nav_bg_color",
        )

        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .tabCell(selected = selected, selectedBgColor = selectedBgColor)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(vertical = NuvioTokens.Space.s6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier
                        .size(28.dp)
                        .then(if (selected) Modifier.gradientMask(palette.accentBrush()) else Modifier),
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    tint = if (selected) Color.White else iconColor,
                )
                NavItemLabel(label = label, labelFraction = labelFraction, iconColor = if (selected) iconColor else glassTextColor, selected = selected)
            }
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
        label: String?,
        content: @Composable () -> Unit,
    ) {
        val tokens = MaterialTheme.nuvio
        LiquidGlassSettingsRepository.ensureLoaded()
        val glassSettings by LiquidGlassSettingsRepository.uiState.collectAsStateWithLifecycle()
        val glassTextColor = if (glassSettings.enabled) glassSettings.textColor else tokens.colors.textMuted
        val selectedBgColor by animateColorAsState(
            targetValue = if (selected) tokens.colors.accent.copy(alpha = NuvioTokens.Opacity.selected)
            else Color.Transparent,
            label = "nav_bg_color",
        )
        val iconColor by animateColorAsState(
            targetValue = if (selected) tokens.colors.accent else tokens.colors.textMuted,
            label = "nav_icon_color",
        )

        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .tabCell(selected = selected, selectedBgColor = selectedBgColor)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(vertical = NuvioTokens.Space.s6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
                NavItemLabel(label = label, labelFraction = labelFraction, iconColor = if (selected) iconColor else glassTextColor, selected = selected)
            }
        }
    }
}

@Composable
private fun NavItemLabel(
    label: String?,
    labelFraction: Float,
    iconColor: Color,
    selected: Boolean,
) {
    if (label == null || labelFraction <= 0f) return
    Spacer(modifier = Modifier.height(NuvioTokens.Space.s3 * labelFraction))
    Box(
        modifier = Modifier
            .height(NuvioTokens.Space.s14 * labelFraction)
            .alpha(labelFraction),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = NuvioTokens.Type.labelXs,
                lineHeight = NuvioTokens.LineHeight.labelXs,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = iconColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}


/**
 * Classic flat navigation bar — the original pre-pill implementation.
 * No floating pill, no labels, no scroll behavior. Simple icon row with a top divider.
 */
@Composable
fun NuvioClassicNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable NuvioNavigationBarScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(modifier.fillMaxWidth()) {
        androidx.compose.material3.HorizontalDivider(
            thickness = NuvioTokens.Space.hairline,
            color = tokens.colors.borderDefault,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(nuvioBottomNavigationBarInsets().asPaddingValues())
                .padding(horizontal = NuvioTokens.Space.s4, vertical = nuvioBottomNavigationExtraVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap, Alignment.CenterHorizontally),
        ) {
            NuvioClassicNavigationBarScopeImpl(this).content()
        }
    }
}

private class NuvioClassicNavigationBarScopeImpl(
    private val rowScope: androidx.compose.foundation.layout.RowScope,
) : NuvioNavigationBarScope {

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val tokens = MaterialTheme.nuvio
        val palette = ThemeColors.getColorPalette(MaterialTheme.appTheme)
        val iconColor by animateColorAsState(
            targetValue = if (selected) tokens.colors.accent else tokens.colors.textMuted,
            label = "classic_nav_icon_color",
        )
        with(rowScope) {
            Icon(
                modifier = modifier
                    .widthIn(max = tokens.components.navItemMaxWidth)
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(tokens.components.navItemShape)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(NuvioTokens.Space.s10)
                    .size(tokens.components.navIconSize)
                    .then(if (selected) Modifier.gradientMask(palette.accentBrush()) else Modifier),
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) Color.White else iconColor,
            )
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val tokens = MaterialTheme.nuvio
        val palette = ThemeColors.getColorPalette(MaterialTheme.appTheme)
        val iconColor by animateColorAsState(
            targetValue = if (selected) tokens.colors.accent else tokens.colors.textMuted,
            label = "classic_nav_icon_color",
        )
        with(rowScope) {
            Icon(
                modifier = modifier
                    .widthIn(max = tokens.components.navItemMaxWidth)
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(tokens.components.navItemShape)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(NuvioTokens.Space.s10)
                    .size(tokens.components.navIconSize)
                    .then(if (selected) Modifier.gradientMask(palette.accentBrush()) else Modifier),
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = if (selected) Color.White else iconColor,
            )
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
        label: String?,
        content: @Composable () -> Unit,
    ) {
        val tokens = MaterialTheme.nuvio
        with(rowScope) {
            Box(
                modifier = modifier
                    .widthIn(max = tokens.components.navItemMaxWidth)
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(tokens.components.navItemShape)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(NuvioTokens.Space.s10),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
