package com.nuvio.app.features.netmax

import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object NetmaxAiService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun history(): AiHistoryResult {
        return call(withGuestIdentity { put("action", "history") }).toHistory()
    }

    suspend fun chat(message: String, conversationId: String? = null): AiChatResult {
        val body = withGuestIdentity {
            put("action", "chat")
            put("message", message.take(4000))
            put("newChat", false)
            if (conversationId != null) put("conversationId", conversationId)
            put("clientContext", buildJsonObject { put("app", "NetMax") })
        }
        return call(body).toChat()
    }

    suspend fun submit(action: AiPendingAction, conversationId: String?): String {
        val body = withGuestIdentity {
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
        return (call(body) as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
            .ifBlank { "Request submit ho gayi hai." }
    }

    /**
     * Every action carries the device identity: a session token when the project has one
     * (email login or an anonymous session) plus the guest id, which is what lets a
     * signed-out user still chat, submit requests and report bugs.
     */
    private suspend fun withGuestIdentity(build: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            build()
            put("guestId", NetmaxGuestAccess.guestId())
            NetmaxGuestAccess.accessTokenOrNull()?.let { token ->
                if (token.isNotBlank()) put("accessToken", token)
            }
        }

    private suspend fun call(body: JsonObject): JsonElement {
        // Lazily creates the NetMax session (once per process) so the Functions plugin can
        // attach its bearer token. A failure here is not fatal: the guest id covers it.
        runCatching { NetmaxGuestAccess.ensureSession() }
        val text = try {
            NetmaxSupabaseProvider.client.functions.invoke("netmax-ai", body).bodyAsText()
        } catch (rest: RestException) {
            // The Functions plugin always raises a RestException for non-2xx responses, and that
            // exception's message is a multi-line dump (URL + masked headers + HTTP method).
            // Surfacing it verbatim in the chat banner looked like a broken screen, so map it to
            // one readable line here.
            throw IllegalStateException(rest.netmaxAiUserMessage(), rest)
        }
        val element = try {
            json.parseToJsonElement(text)
        } catch (@Suppress("TooGenericExceptionCaught") parseError: Throwable) {
            // Empty body / gateway HTML page / truncated stream must never reach jsonObject.
            throw IllegalStateException(text.aiErrorFromPlainText() ?: "NetMax AI ka response parse nahi ho paya.", parseError)
        }
        val obj = element as? JsonObject
            ?: throw IllegalStateException("NetMax AI ka response JSON object nahi tha.")
        if (obj["ok"]?.jsonPrimitive?.contentOrNull == "false" || obj["error"] != null) {
            throw IllegalStateException(obj["message"]?.jsonPrimitive?.contentOrNull ?: "NetMax AI unavailable")
        }
        return element
    }

    /** Human-readable reason for a backend HTTP failure, in the app's Hinglish voice. */
    private fun RestException.netmaxAiUserMessage(): String = when (statusCode) {
        401, 403 -> "NetMax AI ka session verify nahi ho paya. App dobara kholke try karein."
        503 -> "AI abhi configured nahi hai."
        429 -> "AI thoda busy hai, ek minute baad dobara try karein."
        else -> description?.takeIf { it.isNotBlank() } ?: error.takeIf { it.isNotBlank() }
            ?: "NetMax AI unavailable (HTTP $statusCode)."
    }

    private fun String.aiErrorFromPlainText(): String? {
        val message = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(this)?.groupValues?.getOrNull(1)
        return message?.takeIf { it.isNotBlank() }
    }

    private fun JsonElement.toChat(): AiChatResult {
        val o = (this as? JsonObject) ?: return AiChatResult(null, "", null, UNLIMITED_USAGE)
        val usage = o["usage"]?.jsonObject
        val pending = o["pendingAction"]?.takeIf { !it.toString().equals("null", true) }?.jsonObject
        return AiChatResult(
            conversationId = o["conversationId"]?.jsonPrimitive?.contentOrNull,
            reply = o["reply"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pendingAction = pending?.let {
                AiPendingAction(
                    type = it["type"]?.jsonPrimitive?.contentOrNull ?: "feature_request",
                    title = it["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    description = it["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    movieName = it["movieName"]?.jsonPrimitive?.contentOrNull,
                    year = it["year"]?.jsonPrimitive?.intOrNull,
                    tmdbId = it["tmdbId"]?.jsonPrimitive?.intOrNull,
                    category = it["category"]?.jsonPrimitive?.contentOrNull,
                )
            },
            usage = readUsage(usage),
        )
    }

    private fun JsonElement.toHistory(): AiHistoryResult {
        val o = (this as? JsonObject) ?: return AiHistoryResult(null, emptyList(), UNLIMITED_USAGE)
        val usage = o["usage"]?.jsonObject
        // Decode defensively: a row whose content is missing/null must not become a null String
        // (that later crashes the Text() call) and must not abort the whole history render.
        val messages = (o["messages"] as? JsonArray)
            ?.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val role = item["role"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val content = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                AiHistoryMessage(
                    role = role,
                    content = content,
                    created_at = item["created_at"]?.jsonPrimitive?.contentOrNull,
                    image_meta = item["image_meta"],
                )
            }
            ?: emptyList()
        return AiHistoryResult(
            conversationId = o["conversationId"]?.jsonPrimitive?.contentOrNull,
            messages = messages,
            usage = readUsage(usage),
        )
    }

    /**
     * The backend no longer caps requests per day; `limit: -1` means unlimited. An older
     * deployment that still answers with a real count keeps showing that count in the pill.
     */
    internal fun readUsage(usage: JsonObject?): AiUsage {
        if (usage == null) return UNLIMITED_USAGE
        val limit = usage["limit"]?.jsonPrimitive?.intOrNull
        val unlimited = usage["unlimited"]?.jsonPrimitive?.contentOrNull?.equals("true", true) == true ||
            (limit == null || limit < 0)
        if (unlimited) return UNLIMITED_USAGE
        return AiUsage(
            used = usage["used"]?.jsonPrimitive?.intOrNull ?: 0,
            limit = limit,
            remaining = usage["remaining"]?.jsonPrimitive?.intOrNull ?: -1,
        )
    }

    internal val UNLIMITED_USAGE = AiUsage(used = 0, limit = -1, remaining = -1)
}

/** True only when the backend reports a real cap; unlimited answers hide the counter pill. */
internal fun AiUsage.showsDailyCounter(): Boolean = remaining >= 0

@kotlinx.serialization.Serializable
data class AiHistoryMessage(val role: String, val content: String, val created_at: String? = null, val image_meta: kotlinx.serialization.json.JsonElement? = null)

data class AiHistoryResult(val conversationId: String?, val messages: List<AiHistoryMessage>, val usage: AiUsage)
data class AiUsage(val used: Int, val limit: Int, val remaining: Int)
data class AiPendingAction(val type: String, val title: String, val description: String, val movieName: String? = null, val year: Int? = null, val tmdbId: Int? = null, val category: String? = null)
data class AiChatResult(val conversationId: String?, val reply: String, val pendingAction: AiPendingAction?, val usage: AiUsage)
