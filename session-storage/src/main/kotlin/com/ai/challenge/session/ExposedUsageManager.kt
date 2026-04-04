package com.ai.challenge.session

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object RequestMetricsTable : Table("request_metrics") {
    val turnId = varchar("turn_id", 36)
        .references(TurnsTable.id, onDelete = ReferenceOption.CASCADE)
    val promptTokens = integer("prompt_tokens")
    val completionTokens = integer("completion_tokens")
    val cachedTokens = integer("cached_tokens")
    val cacheWriteTokens = integer("cache_write_tokens")
    val reasoningTokens = integer("reasoning_tokens")
    val totalCost = double("total_cost")
    val upstreamCost = double("upstream_cost")
    val upstreamPromptCost = double("upstream_prompt_cost")
    val upstreamCompletionsCost = double("upstream_completions_cost")

    override val primaryKey = PrimaryKey(turnId)
}

class ExposedUsageManager(private val database: Database) : UsageManager {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(RequestMetricsTable)
        }
    }

    override fun record(turnId: TurnId, metrics: RequestMetrics) {
        transaction(database) {
            RequestMetricsTable.insert {
                it[RequestMetricsTable.turnId] = turnId.value
                it[promptTokens] = metrics.tokens.promptTokens
                it[completionTokens] = metrics.tokens.completionTokens
                it[cachedTokens] = metrics.tokens.cachedTokens
                it[cacheWriteTokens] = metrics.tokens.cacheWriteTokens
                it[reasoningTokens] = metrics.tokens.reasoningTokens
                it[totalCost] = metrics.cost.totalCost
                it[upstreamCost] = metrics.cost.upstreamCost
                it[upstreamPromptCost] = metrics.cost.upstreamPromptCost
                it[upstreamCompletionsCost] = metrics.cost.upstreamCompletionsCost
            }
        }
    }

    override fun getByTurn(turnId: TurnId): RequestMetrics? = transaction(database) {
        RequestMetricsTable.selectAll()
            .where { RequestMetricsTable.turnId eq turnId.value }
            .singleOrNull()
            ?.toRequestMetrics()
    }

    override fun getBySession(sessionId: SessionId): Map<TurnId, RequestMetrics> = transaction(database) {
        (RequestMetricsTable innerJoin TurnsTable)
            .selectAll()
            .where { TurnsTable.sessionId eq sessionId.value }
            .associate { row ->
                TurnId(row[RequestMetricsTable.turnId]) to row.toRequestMetrics()
            }
    }

    override fun getSessionTotal(sessionId: SessionId): RequestMetrics =
        getBySession(sessionId).values.fold(RequestMetrics()) { acc, m -> acc + m }

    private fun ResultRow.toRequestMetrics() = RequestMetrics(
        tokens = TokenDetails(
            promptTokens = this[RequestMetricsTable.promptTokens],
            completionTokens = this[RequestMetricsTable.completionTokens],
            cachedTokens = this[RequestMetricsTable.cachedTokens],
            cacheWriteTokens = this[RequestMetricsTable.cacheWriteTokens],
            reasoningTokens = this[RequestMetricsTable.reasoningTokens],
        ),
        cost = CostDetails(
            totalCost = this[RequestMetricsTable.totalCost],
            upstreamCost = this[RequestMetricsTable.upstreamCost],
            upstreamPromptCost = this[RequestMetricsTable.upstreamPromptCost],
            upstreamCompletionsCost = this[RequestMetricsTable.upstreamCompletionsCost],
        ),
    )
}
