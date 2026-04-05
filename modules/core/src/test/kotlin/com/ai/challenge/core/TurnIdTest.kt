package com.ai.challenge.core

import kotlin.test.Test

class TurnIdTest {
    @Test
    fun `generate creates unique TurnIds`() {
        val id1 = TurnId.generate()
        val id2 = TurnId.generate()
        assert(id1 != id2)
    }
}
