package com.ai.challenge.core.branch

import com.ai.challenge.core.turn.TurnId

@JvmInline
value class TurnSequence(val values: List<TurnId>) {
    fun trunkUpTo(turnId: TurnId): TurnSequence {
        val index = values.indexOf(element = turnId)
        require(index >= 0) { "Turn ${turnId.value} not found in sequence" }
        return TurnSequence(values = values.subList(fromIndex = 0, toIndex = index + 1))
    }
}
