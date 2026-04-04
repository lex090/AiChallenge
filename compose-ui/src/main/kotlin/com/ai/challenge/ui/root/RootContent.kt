package com.ai.challenge.ui.root

import androidx.compose.runtime.Composable
import com.ai.challenge.ui.chat.ChatContent
import com.arkivanov.decompose.extensions.compose.stack.Children

@Composable
fun RootContent(component: RootComponent) {
    Children(stack = component.childStack) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Chat -> ChatContent(instance.component)
        }
    }
}
