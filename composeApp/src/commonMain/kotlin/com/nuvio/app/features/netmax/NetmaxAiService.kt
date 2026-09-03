package com.nuvio.app.features.netmax

import com.nuvio.app.core.network.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object NetmaxAiService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun history(): AiHistoryResult {
        return call(buildJsonObject { put("action", "history") }).toHistory()
    }

    suspend fun chat(message: String, conversationId: String? = null): AiChatResult {
        val session = NetmaxSupabaseProvider.client.auth.currentSessionOrNull()
            ?: SupabaseProvider.client.auth.currentSessionOrNull()
        val body = buildJsonObject {
            put("action", "chat")
            put("message", message.take(4000))
            put("newChat", false)
            if (conversationId != null) put("conversationId", conversationId)
            put("clientContext", buildJsonObject { put("app", "NetMax") })
            if (session != null) {
                put("accessToken", session.accessToken)
            }
        }
        return call(body).toChat()
    }

    suspend fun submit(action: AiPendingAction, conversationId: String?): String {
        val body = buildJsonObject {
            put("action", "submit")
            put("type", action.type)
            put("title", action.title)
            put("description", action.description)
            put("movieName", action.movieName)
            put("year", action.year)
            put("tmdbId", action.tmdbId)
            put("category", action.category)
            put("conversationId", conversationId)
        }
        return call(body).jsonObject["message"]?.jsonPrimitive?.content.orEmpty()
            .ifBlank { "Request submit ho gayi hai." }
    }

    private suspend fun call(body: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonElement {
        val response = NetmaxSupabaseProvider.client.functions.invoke("netmax-ai", body)
        val text = response.bodyAsText()
        val element = json.parseToJsonElement(text)
        val obj = element.jsonObject
        if (obj["ok"]?.jsonPrimitive?.contentOrNull == "false" || obj["error"] != null) {
            throw IllegalStateException(obj["message"]?.jsonPrimitive?.content ?: "NetMax AI unavailable")
        }
        return element
    }

    private fun kotlinx.serialization.json.JsonElement.toChat(): AiChatResult {
        val o = jsonObject
        val usage = o["usage"]?.jsonObject
        val pending = o["pendingAction"]?.takeIf { !it.toString().equals("null", true) }?.jsonObject
        return AiChatResult(
            conversationId = o["conversationId"]?.jsonPrimitive?.contentOrNull,
            reply = o["reply"]?.jsonPrimitive?.content.orEmpty(),
            pendingAction = pending?.let {
                AiPendingAction(
                    type = it["type"]?.jsonPrimitive?.content ?: "feature_request",
                    title = it["title"]?.jsonPrimitive?.content.orEmpty(),
                    description = it["description"]?.jsonPrimitive?.content.orEmpty(),
                    movieName = it["movieName"]?.jsonPrimitive?.contentOrNull,
                    year = it["year"]?.jsonPrimitive?.intOrNull,
                    tmdbId = it["tmdbId"]?.jsonPrimitive?.intOrNull,
                    category = it["category"]?.jsonPrimitive?.contentOrNull,
                )
            },
            usage = AiUsage(
                used = usage?.get("used")?.jsonPrimitive?.intOrNull ?: 0,
                limit = usage?.get("limit")?.jsonPrimitive?.intOrNull ?: 10,
                remaining = usage?.get("remaining")?.jsonPrimitive?.intOrNull ?: 10,
            ),
        )
    }

    private fun kotlinx.serialization.json.JsonElement.toHistory(): AiHistoryResult {
        val o = jsonObject
        val usage = o["usage"]?.jsonObject
        val messages = o["messages"]?.let { arr ->
            arr.toString().let { json.decodeFromString<List<AiHistoryMessage>>(it) }
        } ?: emptyList()
        return AiHistoryResult(
            conversationId = o["conversationId"]?.jsonPrimitive?.contentOrNull,
            messages = messages,
            usage = AiUsage(
                used = usage?.get("used")?.jsonPrimitive?.intOrNull ?: 0,
                limit = usage?.get("limit")?.jsonPrimitive?.intOrNull ?: 10,
                remaining = usage?.get("remaining")?.jsonPrimitive?.intOrNull ?: 10,
            ),
        )
    }
}

@kotlinx.serialization.Serializable
data class AiHistoryMessage(val role: String, val content: String, val created_at: String? = null, val image_meta: kotlinx.serialization.json.JsonElement? = null)

data class AiHistoryResult(val conversationId: String?, val messages: List<AiHistoryMessage>, val usage: AiUsage)
data class AiUsage(val used: Int, val limit: Int, val remaining: Int)
data class AiPendingAction(val type: String, val title: String, val description: String, val movieName: String? = null, val year: Int? = null, val tmdbId: Int? = null, val category: String? = null)
data class AiChatResult(val conversationId: String?, val reply: String, val pendingAction: AiPendingAction?, val usage: AiUsage)
