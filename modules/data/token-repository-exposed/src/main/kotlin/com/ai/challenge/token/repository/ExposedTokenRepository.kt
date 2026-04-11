package com.ai.challenge.token.repository

import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.token.TokenDetailsRepository
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedTokenRepository(
    private val database: Database,
    private val turnRepository: TurnRepository,
) : TokenDetailsRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(TokenDetailsTable)
        }
    }

    override suspend fun record(turnId: TurnId, details: TokenDetails) {
        val turn = turnRepository.get(turnId = turnId)
            ?: error("Turn not found for turnId=${turnId.value}")
        transaction(database) {
            TokenDetailsTable.insert {
                it[TokenDetailsTable.turnId] = turnId.value
                it[TokenDetailsTable.sessionId] = turn.sessionId.value
                it[promptTokens] = details.promptTokens
                it[completionTokens] = details.completionTokens
                it[cachedTokens] = details.cachedTokens
                it[cacheWriteTokens] = details.cacheWriteTokens
                it[reasoningTokens] = details.reasoningTokens
            }
        }
    }

    override suspend fun getByTurn(turnId: TurnId): TokenDetails? = transaction(database) {
        TokenDetailsTable.selectAll()
            .where { TokenDetailsTable.turnId eq turnId.value }
            .singleOrNull()
            ?.toTokenDetails()
    }

    override suspend fun getBySession(sessionId: AgentSessionId): Map<TurnId, TokenDetails> = transaction(database) {
        TokenDetailsTable.selectAll()
            .where { TokenDetailsTable.sessionId eq sessionId.value }
            .associate { row ->
                TurnId(row[TokenDetailsTable.turnId]) to row.toTokenDetails()
            }
    }

    override suspend fun getSessionTotal(sessionId: AgentSessionId): TokenDetails =
        getBySession(sessionId).values.fold(TokenDetails(promptTokens = 0, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0)) { acc, t -> acc + t }

    private fun ResultRow.toTokenDetails() = TokenDetails(
        promptTokens = this[TokenDetailsTable.promptTokens],
        completionTokens = this[TokenDetailsTable.completionTokens],
        cachedTokens = this[TokenDetailsTable.cachedTokens],
        cacheWriteTokens = this[TokenDetailsTable.cacheWriteTokens],
        reasoningTokens = this[TokenDetailsTable.reasoningTokens],
    )
}
