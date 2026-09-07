package com.jarvis.assistant.brain

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fully offline brain. Runs a small quantized Gemma model on-device via
 * Google's MediaPipe LLM Inference API. No internet required after setup.
 *
 * SETUP REQUIRED (one-time, see README):
 * 1. Download a .task model file (e.g. gemma-2b-it-cpu-int4.task) from
 *    https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference
 *    (requires accepting Gemma's license on Kaggle/HuggingFace)
 * 2. Push it to the phone at /data/local/tmp/llm/gemma.task via adb, e.g.:
 *    adb push gemma-2b-it-cpu-int4.task /data/local/tmp/llm/gemma.task
 *
 * Expect noticeably lower quality than Claude, and slower responses
 * (several seconds per reply on a mid-range phone) since it's a ~2B
 * parameter model running on a phone CPU.
 */
class LocalBrain(context: Context) : Brain {

    private val modelPath = "/data/local/tmp/llm/gemma.task"

    private val llmInference: LlmInference by lazy {
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)
            .build()
        LlmInference.createFromOptions(context, options)
    }

    override suspend fun respond(userText: String): String = withContext(Dispatchers.Default) {
        try {
            val prompt = "You are Jarvis, a concise voice assistant. Reply briefly.\nUser: $userText\nJarvis:"
            llmInference.generateResponse(prompt)
        } catch (e: Exception) {
            "ERROR: local model failed (${e.message}). Did you push gemma.task to $modelPath ?"
        }
    }
}
