package com.ai.challenge.ui.user.store

import com.ai.challenge.contextmanagement.memory.MemoryScope
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.MemoryType
import com.ai.challenge.contextmanagement.model.FactCategory
import com.ai.challenge.contextmanagement.model.FactKey
import com.ai.challenge.contextmanagement.model.FactValue
import com.ai.challenge.contextmanagement.model.NoteContent
import com.ai.challenge.contextmanagement.model.NoteTitle
import com.ai.challenge.contextmanagement.model.UserFact
import com.ai.challenge.contextmanagement.model.UserNote
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.identity.UserNoteId
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

/**
 * Factory that creates [UserMemoryStore] instances.
 *
 * Wires [MemoryService] to access [MemoryType.UserNotes] and [MemoryType.UserFacts] providers.
 * Notes support individual CRUD. Facts use replace-all semantics on every mutation.
 */
class UserMemoryStoreFactory(
    private val storeFactory: StoreFactory,
    private val memoryService: MemoryService,
) {

    fun create(): UserMemoryStore =
        object : UserMemoryStore,
            Store<UserMemoryStore.Intent, UserMemoryStore.State, Nothing> by storeFactory.create(
                name = "UserMemoryStore",
                initialState = UserMemoryStore.State(
                    userId = null,
                    notes = emptyList(),
                    facts = emptyList(),
                    isLoading = false,
                    error = null,
                ),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data object Loading : Msg
        data class Loaded(
            val userId: UserId,
            val notes: List<UserNote>,
            val facts: List<UserFact>,
        ) : Msg
        data class Error(val message: String) : Msg
        data class NotesUpdated(val notes: List<UserNote>) : Msg
        data class FactsUpdated(val facts: List<UserFact>) : Msg
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<UserMemoryStore.Intent, Nothing, UserMemoryStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: UserMemoryStore.Intent) {
            when (intent) {
                is UserMemoryStore.Intent.LoadMemory -> loadMemory(userId = intent.userId)
                is UserMemoryStore.Intent.SaveNote -> saveNote(title = intent.title, content = intent.content)
                is UserMemoryStore.Intent.UpdateNote -> updateNote(note = intent.note, title = intent.title, content = intent.content)
                is UserMemoryStore.Intent.DeleteNote -> deleteNote(note = intent.note)
                is UserMemoryStore.Intent.SaveFact -> saveFact(index = intent.index, fact = intent.fact)
                is UserMemoryStore.Intent.DeleteFact -> deleteFact(index = intent.index)
                is UserMemoryStore.Intent.AddFact -> addFact()
            }
        }

        private fun loadMemory(userId: UserId) {
            dispatch(message = Msg.Loading)
            scope.launch {
                try {
                    val scope = MemoryScope.User(userId = userId)
                    val notes = memoryService.provider(type = MemoryType.UserNotes).get(scope = scope)
                    val facts = memoryService.provider(type = MemoryType.UserFacts).get(scope = scope)
                    dispatch(
                        message = Msg.Loaded(
                            userId = userId,
                            notes = notes,
                            facts = facts,
                        ),
                    )
                } catch (e: Exception) {
                    dispatch(message = Msg.Error(message = e.message ?: "Failed to load user memory"))
                }
            }
        }

        private fun saveNote(title: String, content: String) {
            val userId = state().userId ?: return
            scope.launch {
                try {
                    val memoryScope = MemoryScope.User(userId = userId)
                    val note = UserNote(
                        id = UserNoteId.generate(),
                        userId = userId,
                        title = NoteTitle(value = title),
                        content = NoteContent(value = content),
                    )
                    memoryService.provider(type = MemoryType.UserNotes).append(scope = memoryScope, note = note)
                    val updatedNotes = state().notes + note
                    dispatch(message = Msg.NotesUpdated(notes = updatedNotes))
                } catch (e: Exception) {
                    dispatch(message = Msg.Error(message = e.message ?: "Failed to save note"))
                }
            }
        }

        private fun updateNote(note: UserNote, title: String, content: String) {
            val userId = state().userId ?: return
            scope.launch {
                try {
                    val memoryScope = MemoryScope.User(userId = userId)
                    val updatedNote = note.copy(
                        title = NoteTitle(value = title),
                        content = NoteContent(value = content),
                    )
                    memoryService.provider(type = MemoryType.UserNotes).update(scope = memoryScope, note = updatedNote)
                    val updatedNotes = state().notes.map { existing ->
                        if (existing.id == note.id) updatedNote else existing
                    }
                    dispatch(message = Msg.NotesUpdated(notes = updatedNotes))
                } catch (e: Exception) {
                    dispatch(message = Msg.Error(message = e.message ?: "Failed to update note"))
                }
            }
        }

        private fun deleteNote(note: UserNote) {
            val userId = state().userId ?: return
            scope.launch {
                try {
                    val memoryScope = MemoryScope.User(userId = userId)
                    memoryService.provider(type = MemoryType.UserNotes).delete(scope = memoryScope, note = note)
                    val updatedNotes = state().notes.filter { it.id != note.id }
                    dispatch(message = Msg.NotesUpdated(notes = updatedNotes))
                } catch (e: Exception) {
                    dispatch(message = Msg.Error(message = e.message ?: "Failed to delete note"))
                }
            }
        }

        private fun saveFact(index: Int, fact: UserFact) {
            val userId = state().userId ?: return
            val currentFacts = state().facts.toMutableList()
            if (index in currentFacts.indices) {
                currentFacts[index] = fact
            } else {
                currentFacts.add(element = fact)
            }
            scope.launch {
                try {
                    val memoryScope = MemoryScope.User(userId = userId)
                    memoryService.provider(type = MemoryType.UserFacts).replace(scope = memoryScope, facts = currentFacts)
                    dispatch(message = Msg.FactsUpdated(facts = currentFacts))
                } catch (e: Exception) {
                    dispatch(message = Msg.Error(message = e.message ?: "Failed to save fact"))
                }
            }
        }

        private fun deleteFact(index: Int) {
            val userId = state().userId ?: return
            val currentFacts = state().facts.toMutableList()
            if (index !in currentFacts.indices) return
            currentFacts.removeAt(index = index)
            scope.launch {
                try {
                    val memoryScope = MemoryScope.User(userId = userId)
                    memoryService.provider(type = MemoryType.UserFacts).replace(scope = memoryScope, facts = currentFacts)
                    dispatch(message = Msg.FactsUpdated(facts = currentFacts))
                } catch (e: Exception) {
                    dispatch(message = Msg.Error(message = e.message ?: "Failed to delete fact"))
                }
            }
        }

        private fun addFact() {
            val userId = state().userId ?: return
            val newFact = UserFact(
                userId = userId,
                category = FactCategory.Goal,
                key = FactKey(value = ""),
                value = FactValue(value = ""),
            )
            val updatedFacts = state().facts + newFact
            scope.launch {
                try {
                    val memoryScope = MemoryScope.User(userId = userId)
                    memoryService.provider(type = MemoryType.UserFacts).replace(scope = memoryScope, facts = updatedFacts)
                    dispatch(message = Msg.FactsUpdated(facts = updatedFacts))
                } catch (e: Exception) {
                    dispatch(message = Msg.Error(message = e.message ?: "Failed to add fact"))
                }
            }
        }
    }

    private object ReducerImpl : Reducer<UserMemoryStore.State, Msg> {
        override fun UserMemoryStore.State.reduce(msg: Msg): UserMemoryStore.State =
            when (msg) {
                is Msg.Loading -> copy(isLoading = true, error = null)
                is Msg.Loaded -> copy(
                    userId = msg.userId,
                    notes = msg.notes,
                    facts = msg.facts,
                    isLoading = false,
                    error = null,
                )
                is Msg.Error -> copy(isLoading = false, error = msg.message)
                is Msg.NotesUpdated -> copy(notes = msg.notes)
                is Msg.FactsUpdated -> copy(facts = msg.facts)
            }
    }
}
