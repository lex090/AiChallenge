package com.ai.challenge.ui.user.store

import com.ai.challenge.contextmanagement.model.UserFact
import com.ai.challenge.contextmanagement.model.UserNote
import com.ai.challenge.sharedkernel.identity.UserId
import com.arkivanov.mvikotlin.core.store.Store

/**
 * MVIKotlin Store contract for the User Memory Panel.
 *
 * Exposes intents to load user memory, manage notes (CRUD),
 * and manage facts (CRUD). Backed by [UserNoteMemoryProvider] and [UserFactMemoryProvider].
 *
 * Invariants:
 * - Notes are individually addressable by identity; CRUD is note-level.
 * - Facts use replace-all semantics; the full list is replaced on every mutation.
 * - [State.userId] is null until [Intent.LoadMemory] succeeds.
 */
interface UserMemoryStore : Store<UserMemoryStore.Intent, UserMemoryStore.State, Nothing> {

    sealed interface Intent {
        data class LoadMemory(val userId: UserId) : Intent
        data class SaveNote(val title: String, val content: String) : Intent
        data class UpdateNote(val note: UserNote, val title: String, val content: String) : Intent
        data class DeleteNote(val note: UserNote) : Intent
        data class SaveFact(val index: Int, val fact: UserFact) : Intent
        data class DeleteFact(val index: Int) : Intent
        data object AddFact : Intent
    }

    data class State(
        val userId: UserId?,
        val notes: List<UserNote>,
        val facts: List<UserFact>,
        val isLoading: Boolean,
        val error: String?,
    )
}
