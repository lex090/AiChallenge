package com.ai.challenge.ui.user.store

import com.ai.challenge.conversation.model.UserName
import com.ai.challenge.conversation.model.UserPreferences
import com.ai.challenge.conversation.service.UserService
import com.ai.challenge.conversation.usecase.CreateUserUseCase
import com.ai.challenge.conversation.usecase.DeleteUserUseCase
import com.ai.challenge.conversation.usecase.UpdateUserUseCase
import com.ai.challenge.sharedkernel.identity.UserId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class UserSettingsStoreFactory(
    private val storeFactory: StoreFactory,
    private val userService: UserService,
    private val createUserUseCase: CreateUserUseCase,
    private val updateUserUseCase: UpdateUserUseCase,
    private val deleteUserUseCase: DeleteUserUseCase,
) {
    fun create(): UserSettingsStore =
        object : UserSettingsStore,
            Store<UserSettingsStore.Intent, UserSettingsStore.State, Nothing> by storeFactory.create(
                name = "UserSettingsStore",
                initialState = UserSettingsStore.State(
                    userId = null,
                    isNewUser = true,
                    userName = "",
                    preferences = "",
                    isSaving = false,
                    isSaved = false,
                    isDeleted = false,
                    errorText = null,
                ),
                executorFactory = {
                    ExecutorImpl(
                        userService = userService,
                        createUserUseCase = createUserUseCase,
                        updateUserUseCase = updateUserUseCase,
                        deleteUserUseCase = deleteUserUseCase,
                    )
                },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class Loaded(
            val userId: UserId,
            val name: String,
            val preferences: String,
        ) : Msg

        data object LoadedNew : Msg
        data class NameUpdated(val name: String) : Msg
        data class PreferencesUpdated(val text: String) : Msg
        data object Saving : Msg
        data class Saved(val userId: UserId) : Msg
        data object Deleted : Msg
        data class Error(val text: String) : Msg
    }

    private class ExecutorImpl(
        private val userService: UserService,
        private val createUserUseCase: CreateUserUseCase,
        private val updateUserUseCase: UpdateUserUseCase,
        private val deleteUserUseCase: DeleteUserUseCase,
    ) : CoroutineExecutor<UserSettingsStore.Intent, Nothing, UserSettingsStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: UserSettingsStore.Intent) {
            when (intent) {
                is UserSettingsStore.Intent.Load -> handleLoad(userId = intent.userId)
                is UserSettingsStore.Intent.LoadNew -> dispatch(Msg.LoadedNew)
                is UserSettingsStore.Intent.UpdateName -> dispatch(Msg.NameUpdated(name = intent.name))
                is UserSettingsStore.Intent.UpdatePreferences -> dispatch(Msg.PreferencesUpdated(text = intent.text))
                is UserSettingsStore.Intent.Save -> handleSave()
                is UserSettingsStore.Intent.Delete -> handleDelete()
            }
        }

        private fun handleLoad(userId: UserId) {
            scope.launch {
                userService.get(id = userId)
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { user ->
                            dispatch(
                                Msg.Loaded(
                                    userId = user.id,
                                    name = user.name.value,
                                    preferences = user.preferences.value,
                                )
                            )
                        },
                    )
            }
        }

        private fun handleSave() {
            val currentState = state()
            dispatch(Msg.Saving)
            scope.launch {
                if (currentState.isNewUser) {
                    createUserUseCase.execute(
                        name = UserName(value = currentState.userName),
                        preferences = UserPreferences(value = currentState.preferences),
                    ).fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { user -> dispatch(Msg.Saved(userId = user.id)) },
                    )
                } else {
                    val userId = currentState.userId ?: return@launch
                    updateUserUseCase.execute(
                        id = userId,
                        name = UserName(value = currentState.userName),
                        preferences = UserPreferences(value = currentState.preferences),
                    ).fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { user -> dispatch(Msg.Saved(userId = user.id)) },
                    )
                }
            }
        }

        private fun handleDelete() {
            val userId = state().userId ?: return
            scope.launch {
                deleteUserUseCase.execute(userId = userId)
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { dispatch(Msg.Deleted) },
                    )
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<UserSettingsStore.State, Msg> {
        override fun UserSettingsStore.State.reduce(msg: Msg): UserSettingsStore.State =
            when (msg) {
                is Msg.Loaded -> copy(
                    userId = msg.userId,
                    isNewUser = false,
                    userName = msg.name,
                    preferences = msg.preferences,
                    errorText = null,
                )
                is Msg.LoadedNew -> copy(
                    userId = null,
                    isNewUser = true,
                    userName = "",
                    preferences = "",
                    isSaving = false,
                    isSaved = false,
                    isDeleted = false,
                    errorText = null,
                )
                is Msg.NameUpdated -> copy(userName = msg.name)
                is Msg.PreferencesUpdated -> copy(preferences = msg.text)
                is Msg.Saving -> copy(isSaving = true, errorText = null)
                is Msg.Saved -> copy(
                    userId = msg.userId,
                    isNewUser = false,
                    isSaving = false,
                    isSaved = true,
                    errorText = null,
                )
                is Msg.Deleted -> copy(
                    isSaving = false,
                    isDeleted = true,
                    errorText = null,
                )
                is Msg.Error -> copy(
                    isSaving = false,
                    errorText = msg.text,
                )
            }
    }
}
