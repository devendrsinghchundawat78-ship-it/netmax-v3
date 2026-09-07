package com.nuvio.app.features.netmax

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The counter pill in the AI screen is driven entirely by these numbers: the backend now
 * answers "unlimited" (`limit: -1` / `unlimited: true`) and the UI must stay silent then,
 * while a deployment that still reports a real cap has to keep showing it.
 */
class NetmaxAiUsageTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun usageOf(payload: String): JsonObject? =
        json.parseToJsonElement(payload).jsonObject["usage"]?.jsonObject

    @Test
    fun `unlimited backend answers hide the daily counter`() {
        assertFalse(
            NetmaxAiService.readUsage(
                usageOf("""{"usage":{"used":4,"limit":-1,"remaining":-1,"unlimited":true}}""")
            ).showsDailyCounter()
        )
        // a boolean `unlimited` (not a string) must be understood too
        assertFalse(
            NetmaxAiService.readUsage(
                usageOf("""{"usage":{"used":41,"limit":-1,"remaining":-1,"unlimited":true}}""")
            ).showsDailyCounter()
        )
        // missing usage block / missing limit fields → treated as unlimited, never "0 left"
        assertFalse(NetmaxAiService.readUsage(null).showsDailyCounter())
        assertFalse(NetmaxAiService.readUsage(usageOf("""{"usage":{"used":1}}""")).showsDailyCounter())
        assertFalse(NetmaxAiService.readUsage(usageOf("""{}""")).showsDailyCounter())
        assertEquals(NetmaxAiService.UNLIMITED_USAGE, NetmaxAiService.readUsage(null))
    }

    @Test
    fun `a configured cap is still reported to the user`() {
        val usage = NetmaxAiService.readUsage(usageOf("""{"usage":{"used":3,"limit":10,"remaining":7}}"""))
        assertEquals(AiUsage(used = 3, limit = 10, remaining = 7), usage)
        assertTrue(usage.showsDailyCounter())
        assertTrue(
            NetmaxAiService.readUsage(usageOf("""{"usage":{"used":9,"limit":10,"remaining":1}}"""))
                .showsDailyCounter()
        )
        // the last request of the day still shows the number rather than disappearing
        assertTrue(
            NetmaxAiService.readUsage(usageOf("""{"usage":{"used":10,"limit":10,"remaining":0}}"""))
                .showsDailyCounter()
        )
    }
}
