package com.ai.challenge.core.context

sealed interface ContextManagementType {
    data object None : ContextManagementType
    data object SummarizeOnThreshold : ContextManagementType
    data object SlidingWindow : ContextManagementType
}
