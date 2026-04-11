package com.ai.challenge.app.event

import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventHandler
import com.ai.challenge.core.session.AgentSessionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InProcessDomainEventPublisherTest {

    @Test
    fun `publish dispatches to registered handler`() = runTest {
        val handled = mutableListOf<DomainEvent>()
        val handler = object : DomainEventHandler<DomainEvent.SessionDeleted> {
            override suspend fun handle(event: DomainEvent.SessionDeleted) {
                handled.add(element = event)
            }
        }

        val publisher = InProcessDomainEventPublisher(
            handlers = mapOf(DomainEvent.SessionDeleted::class to listOf(handler)),
        )

        val event = DomainEvent.SessionDeleted(sessionId = AgentSessionId.generate())
        publisher.publish(event = event)

        assertEquals(expected = 1, actual = handled.size)
        assertEquals(expected = event, actual = handled.first())
    }

    @Test
    fun `publish with no handlers does not throw`() = runTest {
        val publisher = InProcessDomainEventPublisher(handlers = emptyMap())
        val event = DomainEvent.SessionDeleted(sessionId = AgentSessionId.generate())
        publisher.publish(event = event)
    }

    @Test
    fun `publish dispatches to multiple handlers`() = runTest {
        var count = 0
        val handler1 = object : DomainEventHandler<DomainEvent.SessionDeleted> {
            override suspend fun handle(event: DomainEvent.SessionDeleted) { count++ }
        }
        val handler2 = object : DomainEventHandler<DomainEvent.SessionDeleted> {
            override suspend fun handle(event: DomainEvent.SessionDeleted) { count++ }
        }

        val publisher = InProcessDomainEventPublisher(
            handlers = mapOf(DomainEvent.SessionDeleted::class to listOf(handler1, handler2)),
        )

        publisher.publish(event = DomainEvent.SessionDeleted(sessionId = AgentSessionId.generate()))
        assertEquals(expected = 2, actual = count)
    }
}
