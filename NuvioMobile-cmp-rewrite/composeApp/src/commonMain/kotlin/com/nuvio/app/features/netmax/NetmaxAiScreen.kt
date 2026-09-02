package com.nuvio.app.features.netmax

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import kotlinx.coroutines.launch

private data class ChatLine(val role: String, val text: String, val action: AiPendingAction? = null)

@Composable
fun NetmaxAiScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val messages = remember { mutableStateListOf<ChatLine>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var remaining by remember { mutableStateOf(10) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated && !(authState as AuthState.Authenticated).isAnonymous) {
            runCatching { NetmaxAiService.history() }.onSuccess { history ->
                messages.clear()
                messages.addAll(history.messages.map { ChatLine(it.role, it.content) })
                conversationId = history.conversationId
                remaining = history.usage.remaining
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
    ) {
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("NETMAX AI", style = MaterialTheme.typography.titleLarge)
                    Text("$remaining AI requests remaining today", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        }

        if (authState is AuthState.Authenticated && (authState as AuthState.Authenticated).isAnonymous) {
            Text(
                "NetMax AI use karne ke liye pehle email account se login karein.",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages) { item ->
                    val isUser = item.role == "user"
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = if (isUser) 2.dp else 0.dp,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.text, style = MaterialTheme.typography.bodyLarge)
                            item.action?.let { action ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                                    androidx.compose.material3.Button(enabled = !busy, onClick = {
                                        busy = true
                                        scope.launch {
                                            runCatching { NetmaxAiService.submit(action, conversationId) }
                                                .onSuccess { messages.add(ChatLine("assistant", it)) }
                                                .onFailure { error = it.message }
                                            busy = false
                                        }
                                    }) { Text("Submit") }
                                }
                            }
                        }
                    }
                }
                error?.let { msg -> item { Text("⚠️ $msg", color = MaterialTheme.colorScheme.error) } }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(4000); error = null },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("NetMax se kuch poochho…") },
                    enabled = !busy,
                    maxLines = 4,
                )
                IconButton(
                    enabled = input.isNotBlank() && !busy,
                    onClick = {
                        val text = input.trim()
                        input = ""
                        messages.add(ChatLine("user", text))
                        busy = true
                        scope.launch {
                            runCatching { NetmaxAiService.chat(text, conversationId) }
                                .onSuccess {
                                    conversationId = it.conversationId
                                    remaining = it.usage.remaining
                                    messages.add(ChatLine("assistant", it.reply, it.pendingAction))
                                    listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
                                }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                ) { Icon(Icons.Rounded.Send, contentDescription = "Send") }
            }
        }
    }
}
