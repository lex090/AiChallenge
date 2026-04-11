package com.ai.challenge.core.chat.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MessageContentTest {

    @Test
    fun `equal content produces equal objects`() {
        val a = MessageContent(value = "hello")
        val b = MessageContent(value = "hello")
        assertEquals(a, b)
    }

    @Test
    fun `different content produces unequal objects`() {
        val a = MessageContent(value = "hello")
        val b = MessageContent(value = "world")
        assertNotEquals(a, b)
    }

    @Test
    fun `value returns wrapped string`() {
        val content = MessageContent(value = "test message")
        assertEquals("test message", content.value)
    }
}
