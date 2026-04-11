package com.ai.challenge.app.event

import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventHandler
import com.ai.challenge.core.event.DomainEventPublisher
import kotlin.reflect.KClass

/**
 * In-process synchronous domain event dispatcher.
 *
 * Handlers are called sequentially in the same coroutine.
 * Guarantees that all side effects complete before [publish] returns.
 */
class InProcessDomainEventPublisher(
    private val handlers: Map<KClass<out DomainEvent>, List<DomainEventHandler<*>>>,
) : DomainEventPublisher {

    @Suppress("UNCHECKED_CAST")
    override suspend fun publish(event: DomainEvent) {
        val eventHandlers = handlers[event::class] ?: return
        for (handler in eventHandlers) {
            (handler as DomainEventHandler<DomainEvent>).handle(event = event)
        }
    }
}
