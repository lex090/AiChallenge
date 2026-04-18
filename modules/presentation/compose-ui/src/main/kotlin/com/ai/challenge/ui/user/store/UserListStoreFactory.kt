package com.ai.challenge.ui.user.store

import com.ai.challenge.conversation.service.UserService
import com.ai.challenge.sharedkernel.identity.UserId
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class UserListStoreFactory(
    private val storeFactory: StoreFactory,
    private val userService: UserService,
) {
    fun create(): UserListStore =
        object : UserListStore,
            Store<UserListStore.Intent, UserListStore.State, UserListStore.Label> by storeFactory.create(
                name = "UserListStore",
                initialState = UserListStore.State(
                    users = emptyList(),
                    activeUserId = null,
                    errorText = null,
                ),
                executorFactory = { ExecutorImpl(userService = userService) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class UsersLoaded(val users: List<UserListStore.UserItem>) : Msg
        data class UserSelected(val id: UserId) : Msg
        data object Deselected : Msg
        data class Error(val text: String) : Msg
    }

    private class ExecutorImpl(
        private val userService: UserService,
    ) : CoroutineExecutor<UserListStore.Intent, Nothing, UserListStore.State, Msg, UserListStore.Label>() {

        override fun executeIntent(intent: UserListStore.Intent) {
            when (intent) {
                is UserListStore.Intent.LoadUsers -> handleLoadUsers()
                is UserListStore.Intent.SelectUser -> handleSelectUser(id = intent.id)
                is UserListStore.Intent.DeselectAll -> handleDeselectAll()
            }
        }

        private fun handleLoadUsers() {
            scope.launch {
                userService.list()
                    .fold(
                        ifLeft = { error -> dispatch(Msg.Error(text = error.message)) },
                        ifRight = { userList ->
                            val items = userList.map { user ->
                                UserListStore.UserItem(
                                    id = user.id,
                                    name = user.name.value,
                                    initial = user.name.value.firstOrNull() ?: '?',
                                )
                            }
                            dispatch(Msg.UsersLoaded(users = items))
                        },
                    )
            }
        }

        private fun handleSelectUser(id: UserId) {
            dispatch(Msg.UserSelected(id = id))
            publish(UserListStore.Label.UserSelected(userId = id))
        }

        private fun handleDeselectAll() {
            dispatch(Msg.Deselected)
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<UserListStore.State, Msg> {
        override fun UserListStore.State.reduce(msg: Msg): UserListStore.State =
            when (msg) {
                is Msg.UsersLoaded -> copy(
                    users = msg.users,
                    errorText = null,
                )
                is Msg.UserSelected -> copy(
                    activeUserId = msg.id,
                    errorText = null,
                )
                is Msg.Deselected -> copy(
                    activeUserId = null,
                    errorText = null,
                )
                is Msg.Error -> copy(errorText = msg.text)
            }
    }
}
