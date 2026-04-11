package com.ai.challenge.core.chat

import arrow.core.Either
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn

interface ChatService {
    suspend fun send(
        sessionId: AgentSessionId,
        message: MessageContent,
    ): Either<DomainError, Turn>
}
