package com.ai.challenge.sharedkernel.port

import com.ai.challenge.sharedkernel.vo.ContextModeId

/**
 * Port -- validates that a [ContextModeId] value is recognized.
 *
 * Defined in shared kernel so that Conversation bounded context can
 * validate context mode IDs without depending on Context Management's
 * internal enum of strategies.
 *
 * Implemented by Context Management bounded context.
 *
 * Dependency direction: defined in shared-kernel,
 * implemented in context-management/domain, consumed by conversation/domain.
 */
interface ContextModeValidatorPort {
    fun isValid(contextModeId: ContextModeId): Boolean
}
