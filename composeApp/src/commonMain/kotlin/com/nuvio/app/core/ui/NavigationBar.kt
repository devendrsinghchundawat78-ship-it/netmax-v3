package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.LiquidGlassSettingsRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
    selectedTabIndex: Int = -1,
    tabsCount: Int = 0,
    onTabSelected: ((Int) -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    content: @Composable NuvioNavigationBarScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val liquidGlassEnabled by ThemeSettingsRepository.liquidGlassNativeTabBarEnabled.collectAsStateWithLifecycle()

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
    val expandedHorizontalPadding = 12.dp
    val collapsedHorizontalPadding = 48.dp
    val horizontalPadding = expandedHorizontalPadding + (collapsedHorizontalPadding - expandedHorizontalPadding) * (1f - labelFraction)

    val actualTabsCount = tabsCount.coerceAtLeast(1)
    val hasSlidingPuck = tabsCount > 0 && selectedTabIndex >= 0
    val animatedTabIndex = remember { Animatable(if (selectedTabIndex >= 0) selectedTabIndex.toFloat() else 0f) }
    var isDragging by remember { mutableStateOf(false) }
    val dragStartTab = remember { mutableIntStateOf(0) }
    val dragAccumulated = remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedTabIndex) {
        if (!isDragging && selectedTabIndex in 0 until actualTabsCount) {
            animatedTabIndex.animateTo(
                targetValue = selectedTabIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    val puckScaleX by animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "nav_puck_scale_x",
    )
    val puckScaleY by animateFloatAsState(
        targetValue = if (isDragging) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "nav_puck_scale_y",
    )

    val dragModifier = if (tabsCount > 0 && onTabSelected != null) {
        Modifier.pointerInput(actualTabsCount, onTabSelected, tabWidthPx) {
            detectHorizontalDragGestures(
                onDragStart = {
                    isDragging = true
                    dragAccumulated.floatValue = 0f
                    dragStartTab.intValue = animatedTabIndex.value.roundToInt().coerceIn(0, actualTabsCount - 1)
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragAccumulated.floatValue += dragAmount
                    if (tabWidthPx > 0f) {
                        val nextPos = (animatedTabIndex.value + dragAmount / tabWidthPx)
                            .coerceIn(0f, (actualTabsCount - 1).toFloat())
                        coroutineScope.launch {
                            animatedTabIndex.snapTo(nextPos)
                        }
                    }
                },
                onDragEnd = {
                    isDragging = false
                    val startIdx = dragStartTab.intValue
                    val currentVal = animatedTabIndex.value
                    var targetIdx = currentVal.roundToInt().coerceIn(0, actualTabsCount - 1)
                    if (targetIdx == startIdx) {
                        if (dragAccumulated.floatValue > 36f && startIdx < actualTabsCount - 1) {
                            targetIdx = startIdx + 1
                        } else if (dragAccumulated.floatValue < -36f && startIdx > 0) {
                            targetIdx = startIdx - 1
                        }
                    }
                    coroutineScope.launch {
                        animatedTabIndex.animateTo(
                            targetValue = targetIdx.toFloat(),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                        if (targetIdx != selectedTabIndex) {
                            onTabSelected(targetIdx)
                        }
                    }
                    dragAccumulated.floatValue = 0f
                },
                onDragCancel = {
                    isDragging = false
                    coroutineScope.launch {
                        val safeTarget = if (selectedTabIndex in 0 until actualTabsCount) selectedTabIndex.toFloat() else 0f
                        animatedTabIndex.animateTo(
                            targetValue = safeTarget,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                    }
                    dragAccumulated.floatValue = 0f
                },
            )
        }
    } else {
        Modifier
    }

    // Outer container — no background, just safe padding
    Box(
        modifier = modifier
            .then(
                if (tabsCount <= 0) {
                    Modifier.pointerInput(onSwipeLeft, onSwipeRight) {
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
                } else Modifier
            )
            .fillMaxWidth()
            .padding(bottom = bottomSafePadding + nuvioBottomNavigationExtraVerticalPadding + NuvioTokens.Space.s8),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val glassShape = RoundedCornerShape(NuvioTokens.Radius.full)
        val pillModifier = Modifier
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .liquidGlass(
                shape = glassShape,
                hazeState = hazeState,
                isEnabled = liquidGlassEnabled,
                borderWidth = 1.2.dp,
            )

        BoxWithConstraints(
            modifier = pillModifier.then(dragModifier),
        ) {
            val pillWidth = maxWidth
            val innerHorizontalPad = NuvioTokens.Space.s6
            val innerVerticalPad = NuvioTokens.Space.s4
            val availableWidth = (pillWidth - (innerHorizontalPad * 2)).coerceAtLeast(0.dp)
            val tabWidth = if (tabsCount > 0) availableWidth / actualTabsCount else 0.dp
            val density = LocalDensity.current
            LaunchedEffect(tabWidth, density) {
                tabWidthPx = with(density) { tabWidth.toPx() }
            }

            if (hasSlidingPuck && tabWidth > 0.dp) {
                val puckShape = RoundedCornerShape(NuvioTokens.Radius.full)
                val glassBackground = if (isLight) {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDragging) 0.55f else 0.42f),
                            tokens.colors.accent.copy(alpha = if (isDragging) 0.28f else 0.18f),
                            Color.White.copy(alpha = if (isDragging) 0.32f else 0.24f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDragging) 0.18f else 0.12f),
                            tokens.colors.accent.copy(alpha = if (isDragging) 0.32f else 0.22f),
                            Color(0xFF1E1E28).copy(alpha = if (isDragging) 0.42f else 0.32f),
                        ),
                    )
                }
                val glassBorder = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDragging) 0.70f else 0.50f),
                        tokens.colors.accent.copy(alpha = if (isDragging) 0.50f else 0.35f),
                        Color.White.copy(alpha = 0.12f),
                    ),
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(
                            start = innerHorizontalPad + 2.dp,
                            top = innerVerticalPad + 2.dp,
                            bottom = innerVerticalPad + 2.dp,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .width((tabWidth - 4.dp).coerceAtLeast(0.dp))
                            .fillMaxHeight()
                            .graphicsLayer {
                                translationX = (tabWidth * animatedTabIndex.value).toPx()
                                scaleX = puckScaleX
                                scaleY = puckScaleY
                            }
                            .shadow(
                                elevation = if (isDragging) 8.dp else 3.dp,
                                shape = puckShape,
                                spotColor = tokens.colors.accent.copy(alpha = 0.40f),
                                ambientColor = tokens.colors.accent.copy(alpha = 0.20f),
                            )
                            .clip(puckShape)
                            .background(glassBackground)
                            .border(
                                width = 1.dp,
                                brush = glassBorder,
                                shape = puckShape,
                            ),
                    ) {
                        // Top specular liquid glass reflection
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (isDragging) 0.35f else 0.22f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                    }
                }
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
                    hasSlidingPuck = hasSlidingPuck,
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

private class NuvioNavigationBarScopeImpl(
    private val rowScope: androidx.compose.foundation.layout.RowScope,
    private val labelFraction: Float,
    private val hasSlidingPuck: Boolean = false,
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
        LiquidGlassSettingsRepository.ensureLoaded()
        val glassSettings by LiquidGlassSettingsRepository.uiState.collectAsStateWithLifecycle()
        val glassTextColor = if (glassSettings.enabled) glassSettings.textColor else tokens.colors.textMuted
        val iconColor by animateColorAsState(
            targetValue = if (selected) tokens.colors.accent else tokens.colors.textMuted,
            label = "nav_icon_color",
        )
        // Selected item gets a pill-shaped highlight using accent at low opacity (suppressed if sliding puck active)
        val selectedBgColor by animateColorAsState(
            targetValue = if (selected && !hasSlidingPuck) tokens.colors.accent.copy(alpha = NuvioTokens.Opacity.selected)
            else Color.Transparent,
            label = "nav_bg_color",
        )

        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(NuvioTokens.Radius.full))
                    .background(selectedBgColor)
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
            targetValue = if (selected && !hasSlidingPuck) tokens.colors.accent.copy(alpha = NuvioTokens.Opacity.selected)
            else Color.Transparent,
            label = "nav_bg_color",
        )

        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(NuvioTokens.Radius.full))
                    .background(selectedBgColor)
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
            targetValue = if (selected && !hasSlidingPuck) tokens.colors.accent.copy(alpha = NuvioTokens.Opacity.selected)
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
                    .clip(RoundedCornerShape(NuvioTokens.Radius.full))
                    .background(selectedBgColor)
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
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = iconColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
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
