package com.ai.challenge.ui.user.store

import com.ai.challenge.sharedkernel.identity.UserId
import com.arkivanov.mvikotlin.core.store.Store

interface UserListStore : Store<UserListStore.Intent, UserListStore.State, UserListStore.Label> {

    sealed interface Intent {
        data object LoadUsers : Intent
        data class SelectUser(val id: UserId) : Intent
        data object DeselectAll : Intent
    }

    data class State(
        val users: List<UserItem>,
        val activeUserId: UserId?,
        val errorText: String?,
    )

    data class UserItem(
        val id: UserId,
        val name: String,
        val initial: Char,
    )

    sealed interface Label {
        data class UserSelected(val userId: UserId) : Label
    }
}
