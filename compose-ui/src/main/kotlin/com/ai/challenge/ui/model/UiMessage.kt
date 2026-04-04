package com.ai.challenge.ui.model

data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
)
