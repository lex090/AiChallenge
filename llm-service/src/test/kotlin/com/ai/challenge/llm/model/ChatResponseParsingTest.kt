package com.ai.challenge.llm.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses full response with all usage details`() {
        val input = """
        {
          "id": "gen-123",
          "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hi"}}],
          "model": "test-model",
          "usage": {
            "prompt_tokens": 100,
            "completion_tokens": 50,
            "total_tokens": 150,
            "prompt_tokens_details": {
              "cached_tokens": 20,
              "cache_write_tokens": 80
            },
            "completion_tokens_details": {
              "reasoning_tokens": 10
            },
            "cost": 0.0015
          },
          "cost": 0.0015,
          "cost_details": {
            "upstream_inference_cost": 0.0012,
            "upstream_inference_prompt_cost": 0.0008,
            "upstream_inference_completions_cost": 0.0004
          }
        }
        """.trimIndent()

        val response = json.decodeFromString<ChatResponse>(input)

        val usage = response.usage!!
        assertEquals(100, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(150, usage.totalTokens)
        assertEquals(20, usage.promptTokensDetails!!.cachedTokens)
        assertEquals(80, usage.promptTokensDetails!!.cacheWriteTokens)
        assertEquals(10, usage.completionTokensDetails!!.reasoningTokens)
        assertEquals(0.0015, usage.cost!!, 1e-9)
        assertEquals(0.0015, response.cost!!, 1e-9)
        assertEquals(0.0012, response.costDetails!!.upstreamCost, 1e-9)
        assertEquals(0.0008, response.costDetails!!.upstreamPromptCost, 1e-9)
        assertEquals(0.0004, response.costDetails!!.upstreamCompletionsCost, 1e-9)
    }

    @Test
    fun `parses response without optional details`() {
        val input = """
        {
          "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hi"}}],
          "usage": {
            "prompt_tokens": 100,
            "completion_tokens": 50,
            "total_tokens": 150
          }
        }
        """.trimIndent()

        val response = json.decodeFromString<ChatResponse>(input)

        val usage = response.usage!!
        assertEquals(100, usage.promptTokens)
        assertNull(usage.promptTokensDetails)
        assertNull(usage.completionTokensDetails)
        assertNull(usage.cost)
        assertNull(response.cost)
        assertNull(response.costDetails)
    }

    @Test
    fun `parses response without usage`() {
        val input = """
        {
          "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hi"}}]
        }
        """.trimIndent()

        val response = json.decodeFromString<ChatResponse>(input)
        assertNull(response.usage)
    }
}
