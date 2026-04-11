package com.ai.challenge.core.chat.model

/**
 * Branch name.
 * Value object — encapsulates validation (non-empty, allowed characters).
 * Main branch has fixed name "main".
 */
@JvmInline
value class BranchName(val value: String)
