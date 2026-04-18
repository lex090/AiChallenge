package com.ai.challenge.contextmanagement.memory

import com.ai.challenge.contextmanagement.model.ProjectInstructions

/**
 * Domain Service -- project instructions memory provider with upsert write semantics.
 *
 * Invariant: [save] inserts or replaces instructions for the project scope.
 * [get] returns null if instructions have not been synced yet.
 */
interface ProjectInstructionsMemoryProvider : MemoryProvider<ProjectInstructions?> {
    suspend fun save(scope: MemoryScope, instructions: ProjectInstructions)
}
