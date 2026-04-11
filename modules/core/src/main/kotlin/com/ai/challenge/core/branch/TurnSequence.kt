package com.ai.challenge.core.branch

import com.ai.challenge.core.turn.TurnId

/**
 * Value Object — ordered sequence of [TurnId] references within a [Branch].
 *
 * Has no identity — defined only by the list of IDs it contains.
 * Immutable. Append-only in practice (new turns appended at the end).
 *
 * [trunkUpTo] creates a subsequence for branch creation —
 * the new branch inherits history up to the branching point.
 *
 * Embedded in [Branch]. Does not store [Turn] objects directly —
 * only ID-based references to avoid object graph complexity.
 */
@JvmInline
value class TurnSequence(val values: List<TurnId>) {
    fun trunkUpTo(turnId: TurnId): TurnSequence {
        val index = values.indexOf(element = turnId)
        require(index >= 0) { "Turn ${turnId.value} not found in sequence" }
        return TurnSequence(values = values.subList(fromIndex = 0, toIndex = index + 1))
    }
}
