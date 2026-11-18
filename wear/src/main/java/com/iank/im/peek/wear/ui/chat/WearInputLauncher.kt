package com.iank.im.peek.wear.ui.chat

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.iank.im.peek.wear.R
import com.iank.im.peek.wear.ui.theme.PeekWearBackground
import com.iank.im.peek.wear.ui.theme.PeekWearOnBackground
import com.iank.im.peek.wear.ui.theme.PeekWearOnPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearOnSurfaceVariant
import com.iank.im.peek.wear.ui.theme.PeekWearPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainer
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainerHigh
import kotlinx.coroutines.delay

object WearInputLauncher {
    const val KEY_SUBMITTED_TEXT = "wear_submitted_text"

    fun canLaunchVoiceRecognizer(context: Context): Boolean {
        return runCatching {
            val probe = createVoiceRecognizerIntent("")
            probe.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
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

@Composable
fun WearTextInputScreen(
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val canSend = inputText.isNotBlank()

    fun submitCurrent() {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) return
        keyboardController?.hide()
        onSubmit(trimmed)
    }

    LaunchedEffect(Unit) {
        delay(80L)
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .background(PeekWearBackground)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PeekWearSurfaceContainer)
                .border(1.dp, PeekWearPrimary.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (inputText.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_input_text_prompt),
                    style = MaterialTheme.typography.body2,
                    color = PeekWearOnSurfaceVariant
                )
            }
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                textStyle = MaterialTheme.typography.body2.copy(color = PeekWearOnBackground),
                cursorBrush = SolidColor(PeekWearPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onSend = { submitCurrent() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    keyboardController?.hide()
                    onCancel()
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = PeekWearSurfaceContainerHigh,
                    contentColor = PeekWearOnBackground
                ),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    modifier = Modifier.size(18.dp)
                )
            }

            Button(
                onClick = { submitCurrent() },
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (canSend) PeekWearPrimary else PeekWearSurfaceContainerHigh,
                    contentColor = if (canSend) PeekWearOnPrimary else PeekWearOnSurfaceVariant
                ),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.cd_send_message),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
