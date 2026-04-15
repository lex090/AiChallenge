package com.ai.challenge.contextmanagement.memory

/**
 * Value Object -- type-safe key for provider registry lookup.
 *
 * Sealed interface with phantom type parameter [P] carrying
 * the concrete provider type. Enables compile-time safety:
 * `memoryService.provider(MemoryType.Facts)` returns [FactMemoryProvider].
 */
sealed interface MemoryType<P : MemoryProvider<*>> {
    /** Extracted facts (replace-all semantics). */
    data object Facts : MemoryType<FactMemoryProvider>
    /** Compressed summaries (append-only semantics). */
    data object Summaries : MemoryType<SummaryMemoryProvider>
    /** Project instructions (upsert semantics). */
    data object ProjectInstructions : MemoryType<ProjectInstructionsMemoryProvider>
    /** User preferences memory (upsert semantics, user-scoped). */
    data object UserPreferences : MemoryType<UserPreferencesMemoryProvider>
    /** User-authored notes (append/update/delete semantics, user-scoped). */
    data object UserNotes : MemoryType<UserNoteMemoryProvider>
    /** LLM-extracted user facts (replace-all semantics, user-scoped). */
    data object UserFacts : MemoryType<UserFactMemoryProvider>
}
