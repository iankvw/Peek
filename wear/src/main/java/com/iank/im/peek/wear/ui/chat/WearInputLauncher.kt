package com.iank.im.peek.wear.ui.chat

import android.app.RemoteInput
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.wear.input.RemoteInputIntentHelper
import com.iank.im.peek.wear.domain.model.WearInputMode

object WearInputLauncher {
    private const val KEY_PROMPT_TEXT = "peek_wear_prompt_text"

    fun createIntentForMode(
        mode: WearInputMode,
        textHint: String,
        voicePrompt: String
    ): Intent = when (mode) {
        WearInputMode.TEXT_KEYBOARD -> createTextRemoteInputIntent(textHint)
        WearInputMode.VOICE_FIRST -> createVoiceRecognizerIntent(voicePrompt)
    }

    fun createTextRemoteInputIntent(hint: String): Intent {
        val remoteInputs = listOf(
            RemoteInput.Builder(KEY_PROMPT_TEXT)
                .setLabel(hint)
                .build()
        )
        return RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        }
    }

    fun extractTextFromRemoteInput(resultIntent: Intent?): String? {
        if (resultIntent == null) return null
        return RemoteInput.getResultsFromIntent(resultIntent)
            ?.getCharSequence(KEY_PROMPT_TEXT)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun createVoiceRecognizerIntent(promptLabel: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptLabel)
        }
    }

    fun extractTextFromVoiceResult(resultIntent: Intent?): String? {
        return resultIntent
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
