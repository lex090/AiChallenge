package com.ai.challenge.ui.user.store

import com.ai.challenge.sharedkernel.identity.UserId
import com.arkivanov.mvikotlin.core.store.Store

interface UserSettingsStore : Store<UserSettingsStore.Intent, UserSettingsStore.State, Nothing> {

    sealed interface Intent {
        data class Load(val userId: UserId) : Intent
        data object LoadNew : Intent
        data class UpdateName(val name: String) : Intent
        data class UpdatePreferences(val text: String) : Intent
        data object Save : Intent
        data object Delete : Intent
    }

    data class State(
        val userId: UserId?,
        val isNewUser: Boolean,
        val userName: String,
        val preferences: String,
        val isSaving: Boolean,
        val isSaved: Boolean,
        val isDeleted: Boolean,
        val errorText: String?,
    )
}
