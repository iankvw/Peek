package com.iank.im.peek.wear.ui.chat

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.speech.RecognizerIntent
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import com.iank.im.peek.wear.R
import com.iank.im.peek.wear.ui.theme.PeekWearBackground
import com.iank.im.peek.wear.ui.theme.PeekWearOnBackground
import com.iank.im.peek.wear.ui.theme.PeekWearOnPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearOnSurfaceVariant
import com.iank.im.peek.wear.ui.theme.PeekWearPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainer
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainerHigh
import java.util.concurrent.atomic.AtomicReference

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
    var inputText by remember { mutableStateOf("") }
    var isHandled by remember { mutableStateOf(false) }
    val editTextRef = remember { AtomicReference<EditText?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hintText = stringResource(R.string.chat_input_text_prompt)
    val hintColorInt = PeekWearOnSurfaceVariant.toArgb()
    val canSend = inputText.isNotBlank() && !isHandled

    fun showKeyboard() {
        val editText = editTextRef.get() ?: return
        editText.requestFocus()
        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val editText = editTextRef.get()
        if (editText != null) {
            val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(editText.windowToken, 0)
            editText.clearFocus()
        }
        keyboardController?.hide()
    }

    fun submitCurrent() {
        if (isHandled) return
        val liveText = editTextRef.get()?.text?.toString() ?: inputText
        val trimmed = liveText.trim()
        if (trimmed.isEmpty()) return
        isHandled = true
        hideKeyboard()
        onSubmit(trimmed)
    }

    fun cancelCurrent() {
        if (isHandled) return
        isHandled = true
        hideKeyboard()
        onCancel()
    }

    val latestSubmit by rememberUpdatedState(::submitCurrent)

    DisposableEffect(Unit) {
        onDispose {
            hideKeyboard()
            editTextRef.set(null)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top),
        modifier = Modifier
            .fillMaxSize()
            .background(PeekWearBackground)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PeekWearSurfaceContainer)
                .border(1.dp, PeekWearPrimary.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .clickable(onClick = ::showKeyboard)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    EditText(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        background = null
                        setPadding(0, 0, 0, 0)
                        setTextColor(Color.WHITE)
                        hint = hintText
                        setHintTextColor(hintColorInt)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        imeOptions = EditorInfo.IME_ACTION_SEND
                        isSingleLine = true
                        maxLines = 1
                        isFocusable = true
                        isFocusableInTouchMode = true
                        doAfterTextChanged { editable ->
                            inputText = editable?.toString().orEmpty()
                        }
                        setOnEditorActionListener { _, actionId, event ->
                            val isSendAction = actionId == EditorInfo.IME_ACTION_SEND ||
                                actionId == EditorInfo.IME_ACTION_DONE ||
                                (actionId == EditorInfo.IME_NULL && event?.action == KeyEvent.ACTION_DOWN)
                            if (isSendAction) {
                                latestSubmit()
                                true
                            } else {
                                false
                            }
                        }
                        editTextRef.set(this)
                        post {
                            requestFocus()
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                },
                update = { editText ->
                    if (editText.hint?.toString() != hintText) {
                        editText.hint = hintText
                    }
                }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { cancelCurrent() },
                enabled = !isHandled,
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
