package com.ai.challenge.core.event

/**
 * Domain Event Publisher — dispatches events to registered handlers.
 *
 * In-process implementation: handlers are called synchronously
 * in the same coroutine. Guarantees that side effects complete
 * before the publishing operation returns.
 *
 * Interface defined in core; implemented in application layer.
 */
interface DomainEventPublisher {
    suspend fun publish(event: DomainEvent)
}
