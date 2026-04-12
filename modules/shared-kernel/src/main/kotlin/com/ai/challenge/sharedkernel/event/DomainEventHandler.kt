package com.ai.challenge.sharedkernel.event

/**
 * Domain Event Handler -- processes a specific type of [DomainEvent].
 *
 * Each handler processes one event type. Registered in
 * [DomainEventPublisher] at application startup.
 */
interface DomainEventHandler<T : DomainEvent> {
    suspend fun handle(event: T)
}
