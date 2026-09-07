package com.jarvis.assistant.brain

/**
 * A "Brain" turns a user utterance into a text reply.
 * Swap implementations (Claude API vs local on-device model) via Settings.
 */
interface Brain {
    /**
     * @param userText what the user said (from speech-to-text)
     * @param onResult called on the calling thread's choosing with the reply text,
     *                  or an error message prefixed with "ERROR: "
     */
    suspend fun respond(userText: String): String
}
