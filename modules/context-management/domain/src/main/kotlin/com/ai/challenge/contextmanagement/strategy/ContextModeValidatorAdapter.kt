package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.sharedkernel.port.ContextModeValidatorPort
import com.ai.challenge.sharedkernel.vo.ContextModeId

/**
 * Adapter -- implements [ContextModeValidatorPort] from the shared kernel.
 *
 * Validates that a [ContextModeId] value corresponds to a known
 * [ContextManagementType]. Used by Conversation bounded context
 * to validate context mode IDs without depending on Context Management internals.
 */
class ContextModeValidatorAdapter : ContextModeValidatorPort {

    override fun isValid(contextModeId: ContextModeId): Boolean =
        ContextManagementType.fromModeId(contextModeId = contextModeId) != null
}
