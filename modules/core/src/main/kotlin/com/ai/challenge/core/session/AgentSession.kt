package com.ai.challenge.core.session

import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import kotlin.time.Clock

data class AgentSession(
    val id: AgentSessionId,
    val title: SessionTitle,
    val contextManagementType: ContextManagementType,
    val createdAt: CreatedAt,
    val updatedAt: UpdatedAt,
) {
    fun withUpdatedTitle(newTitle: SessionTitle): AgentSession =
        copy(title = newTitle, updatedAt = UpdatedAt(value = Clock.System.now()))

    fun withContextManagementType(type: ContextManagementType): AgentSession =
        copy(contextManagementType = type, updatedAt = UpdatedAt(value = Clock.System.now()))
}
