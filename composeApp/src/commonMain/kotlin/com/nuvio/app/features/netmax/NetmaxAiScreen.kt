package com.nuvio.app.features.netmax

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.ui.LiquidGlassBackButton
import com.nuvio.app.core.ui.LiquidGlassDefaults
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.core.ui.liquidGlass
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.settings.ThemeSettingsRepository
import kotlinx.coroutines.launch

private data class ChatLine(
    val id: String = "",
    val role: String,
    val text: String,
    val action: AiPendingAction? = null,
    val isSubmitted: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetmaxAiScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val liquidGlassEnabled by ThemeSettingsRepository.liquidGlassNativeTabBarEnabled.collectAsStateWithLifecycle()
    val appTheme by ThemeSettingsRepository.selectedTheme.collectAsStateWithLifecycle()
    val themePalette = remember(appTheme) { ThemeColors.getColorPalette(appTheme) }

    val messages = remember { mutableStateListOf<ChatLine>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var remaining by remember { mutableStateOf(10) }
    var busy by remember { mutableStateOf(false) }
    var submittingActionId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val isAnonymous = authState is AuthState.Authenticated && (authState as AuthState.Authenticated).isAnonymous

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated && !isAnonymous) {
            runCatching { NetmaxAiService.history() }.onSuccess { history ->
                messages.clear()
                messages.addAll(
                    history.messages.mapIndexed { idx, it ->
                        ChatLine(
                            id = "hist_$idx",
                            role = it.role,
                            text = it.content,
                        )
                    }
                )
                conversationId = history.conversationId
                remaining = history.usage.remaining
            }
        }
    }

    fun sendMessage(queryText: String) {
        val text = queryText.trim()
        if (text.isBlank() || busy) return
        input = ""
        val userMsgId = "msg_${System.currentTimeMillis()}"
        messages.add(ChatLine(id = userMsgId, role = "user", text = text))
        busy = true
        error = null

        scope.launch {
            runCatching { NetmaxAiService.chat(text, conversationId) }
                .onSuccess {
                    conversationId = it.conversationId
                    remaining = it.usage.remaining
                    messages.add(
                        ChatLine(
                            id = "reply_${System.currentTimeMillis()}",
                            role = "assistant",
                            text = it.reply,
                            action = it.pendingAction,
                        )
                    )
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
                    }
                }
                .onFailure {
                    error = it.message ?: "Failed to connect to NetMax AI"
                }
            busy = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themePalette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // --- Top Bar ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .liquidGlass(
                    shape = RoundedCornerShape(22.dp),
                    isEnabled = liquidGlassEnabled,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassBackButton(
                    onBack = onBack,
                    isEnabled = liquidGlassEnabled,
                    size = 38.dp,
                    iconSize = 20.dp,
                )

                // AI Avatar Icon Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    themePalette.secondary.copy(alpha = 0.25f),
                                    themePalette.secondaryVariant.copy(alpha = 0.15f),
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(themePalette.accentGradient),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = themePalette.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "NETMAX AI",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = MaterialTheme.nuvio.colors.textPrimary,
                        )
                    }
                    Text(
                        text = "Intelligent Streaming Assistant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.nuvio.colors.textMuted,
                    )
                }

                // Daily Limit Pill Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (remaining > 3) themePalette.secondary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (remaining > 3) themePalette.secondary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "⚡ $remaining left",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (remaining > 3) themePalette.secondary else MaterialTheme.colorScheme.error,
                    )
                }

                // New Chat Button
                if (messages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.nuvio.colors.surfaceCard.copy(alpha = 0.7f))
                            .clickable(
                                indication = ripple(bounded = true, radius = 17.dp),
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    messages.clear()
                                    conversationId = null
                                    error = null
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Chat",
                            tint = MaterialTheme.nuvio.colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // --- Anonymous State Warning ---
        if (isAnonymous) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .liquidGlass(
                        shape = RoundedCornerShape(18.dp),
                        isEnabled = liquidGlassEnabled,
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(themePalette.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = themePalette.secondary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        text = "Sign In Required",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.nuvio.colors.textPrimary,
                    )
                    Text(
                        text = "NetMax AI ka use karne ke liye pehle apne email account se sign in karein.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.nuvio.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // --- Chat Stream Area ---
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && !busy) {
                    // Welcome & Suggestions Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Glowing Emblem
                        val infiniteTransition = rememberInfiniteTransition()
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500),
                                repeatMode = RepeatMode.Reverse,
                            ),
                        )

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            themePalette.secondary.copy(alpha = 0.35f),
                                            Color.Transparent,
                                        )
                                    )
                                )
                                .border(
                                    width = 1.5.dp,
                                    brush = Brush.linearGradient(themePalette.accentGradient),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = themePalette.secondary,
                                modifier = Modifier.size(34.dp),
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Welcome to NetMax AI",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.nuvio.colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Ask for movie recommendations, explore trending shows, or submit requests directly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.nuvio.colors.textMuted,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(28.dp))

                        // Quick Action Suggestions Chips
                        Text(
                            text = "SUGGESTED PROMPTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                            ),
                            color = themePalette.secondary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 12.dp),
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val suggestions = listOf(
                                "🎬 Trending Bollywood movies",
                                "🍿 Best Sci-Fi & Action series",
                                "🔍 Recommend a mystery thriller",
                                "💡 Request a movie or series",
                            )

                            suggestions.forEach { suggestion ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(themePalette.backgroundCard.copy(alpha = 0.8f))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.nuvio.colors.borderSubtle,
                                            shape = RoundedCornerShape(16.dp),
                                        )
                                        .clickable {
                                            sendMessage(suggestion.substringAfter(" "))
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.nuvio.colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val isUser = msg.role == "user"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Top,
                            ) {
                                if (!isUser) {
                                    // Assistant Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(themePalette.secondary.copy(alpha = 0.2f))
                                            .border(1.dp, themePalette.secondary.copy(alpha = 0.5f), CircleShape)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = themePalette.secondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }

                                Column(
                                    modifier = Modifier.widthIn(max = 300.dp),
                                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                                ) {
                                    // Message Bubble
                                    Box(
                                        modifier = Modifier
                                            .clip(
                                                if (isUser) {
                                                    RoundedCornerShape(
                                                        topStart = 18.dp,
                                                        topEnd = 18.dp,
                                                        bottomStart = 18.dp,
                                                        bottomEnd = 4.dp,
                                                    )
                                                } else {
                                                    RoundedCornerShape(
                                                        topStart = 18.dp,
                                                        topEnd = 18.dp,
                                                        bottomStart = 4.dp,
                                                        bottomEnd = 18.dp,
                                                    )
                                                }
                                            )
                                            .background(
                                                if (isUser) {
                                                    Brush.horizontalGradient(
                                                        listOf(
                                                            themePalette.secondary,
                                                            themePalette.secondaryVariant,
                                                        )
                                                    )
                                                } else {
                                                    SolidColor(themePalette.backgroundCard)
                                                }
                                            )
                                            .then(
                                                if (!isUser) {
                                                    Modifier.border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.nuvio.colors.borderSubtle,
                                                        shape = RoundedCornerShape(
                                                            topStart = 18.dp,
                                                            topEnd = 18.dp,
                                                            bottomStart = 4.dp,
                                                            bottomEnd = 18.dp,
                                                        ),
                                                    )
                                                } else Modifier
                                            )
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                    ) {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                lineHeight = 20.sp,
                                            ),
                                            color = if (isUser) Color.White else MaterialTheme.nuvio.colors.textPrimary,
                                        )
                                    }

                                    // Action Proposal Card (if any)
                                    msg.action?.let { action ->
                                        Spacer(Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(themePalette.backgroundElevated)
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.linearGradient(themePalette.accentGradient),
                                                    shape = RoundedCornerShape(16.dp),
                                                )
                                                .padding(14.dp),
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Movie,
                                                        contentDescription = null,
                                                        tint = themePalette.secondary,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Text(
                                                        text = action.type.replace('_', ' ').uppercase(),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = themePalette.secondary,
                                                    )
                                                }

                                                if (action.title.isNotBlank()) {
                                                    Text(
                                                        text = action.title,
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.nuvio.colors.textPrimary,
                                                    )
                                                }

                                                if (action.description.isNotBlank()) {
                                                    Text(
                                                        text = action.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.nuvio.colors.textSecondary,
                                                    )
                                                }

                                                // Submit button
                                                val isThisSubmitting = submittingActionId == msg.id
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            if (msg.isSubmitted) MaterialTheme.nuvio.colors.surfaceCard
                                                            else themePalette.secondary
                                                        )
                                                        .clickable(enabled = !msg.isSubmitted && !isThisSubmitting) {
                                                            submittingActionId = msg.id
                                                            scope.launch {
                                                                runCatching {
                                                                    NetmaxAiService.submit(action, conversationId)
                                                                }.onSuccess { replyText ->
                                                                    val idx = messages.indexOfFirst { it.id == msg.id }
                                                                    if (idx != -1) {
                                                                        messages[idx] = messages[idx].copy(isSubmitted = true)
                                                                    }
                                                                    messages.add(
                                                                        ChatLine(
                                                                            id = "action_res_${System.currentTimeMillis()}",
                                                                            role = "assistant",
                                                                            text = replyText,
                                                                        )
                                                                    )
                                                                }.onFailure { err ->
                                                                    error = err.message
                                                                }
                                                                submittingActionId = null
                                                            }
                                                        }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    if (isThisSubmitting) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            color = Color.White,
                                                            strokeWidth = 2.dp,
                                                        )
                                                    } else {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            if (msg.isSubmitted) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Check,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.nuvio.colors.success,
                                                                    modifier = Modifier.size(16.dp),
                                                                )
                                                                Text(
                                                                    text = "Submitted",
                                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                                    color = MaterialTheme.nuvio.colors.textSecondary,
                                                                )
                                                            } else {
                                                                Text(
                                                                    text = "Submit Request",
                                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                                    color = themePalette.onSecondary,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Typing / Thinking Indicator
                        if (busy) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(themePalette.secondary.copy(alpha = 0.2f))
                                            .border(1.dp, themePalette.secondary.copy(alpha = 0.5f), CircleShape)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = themePalette.secondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 18.dp,
                                                    topEnd = 18.dp,
                                                    bottomStart = 4.dp,
                                                    bottomEnd = 18.dp,
                                                )
                                            )
                                            .background(themePalette.backgroundCard)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.nuvio.colors.borderSubtle,
                                                shape = RoundedCornerShape(
                                                    topStart = 18.dp,
                                                    topEnd = 18.dp,
                                                    bottomStart = 4.dp,
                                                    bottomEnd = 18.dp,
                                                ),
                                            )
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val infiniteTransition = rememberInfiniteTransition()
                                            val dotAlpha by infiniteTransition.animateFloat(
                                                initialValue = 0.3f,
                                                targetValue = 1f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(600),
                                                    repeatMode = RepeatMode.Reverse,
                                                ),
                                            )

                                            Box(
                                                Modifier.size(6.dp).clip(CircleShape)
                                                    .background(themePalette.secondary.copy(alpha = dotAlpha))
                                            )
                                            Box(
                                                Modifier.size(6.dp).clip(CircleShape)
                                                    .background(themePalette.secondary.copy(alpha = (dotAlpha + 0.3f).coerceAtMost(1f)))
                                            )
                                            Box(
                                                Modifier.size(6.dp).clip(CircleShape)
                                                    .background(themePalette.secondary.copy(alpha = (dotAlpha + 0.6f).coerceAtMost(1f)))
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "NetMax AI is thinking...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.nuvio.colors.textMuted,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Error Banner (if any) ---
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                error?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // --- Bottom Input Bar ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .liquidGlass(
                        shape = RoundedCornerShape(26.dp),
                        isEnabled = liquidGlassEnabled,
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                text = "Ask NetMax AI anything…",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    color = MaterialTheme.nuvio.colors.textMuted,
                                ),
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = {
                                input = it.take(4000)
                                error = null
                            },
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                color = MaterialTheme.nuvio.colors.textPrimary,
                            ),
                            cursorBrush = SolidColor(themePalette.secondary),
                            enabled = !busy,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Send Button
                    val canSend = input.isNotBlank() && !busy
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) {
                                    Brush.linearGradient(themePalette.accentGradient)
                                } else {
                                    SolidColor(MaterialTheme.nuvio.colors.surfaceCard.copy(alpha = 0.5f))
                                }
                            )
                            .clickable(
                                enabled = canSend,
                                indication = ripple(bounded = true, radius = 20.dp),
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { sendMessage(input) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = "Send",
                            tint = if (canSend) themePalette.onSecondary else MaterialTheme.nuvio.colors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

