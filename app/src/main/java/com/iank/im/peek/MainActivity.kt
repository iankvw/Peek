package com.iank.im.peek

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.iank.im.peek.service.AiGenerationService
import com.iank.im.peek.ui.common.AppVisibilityTracker
import com.iank.im.peek.ui.main.MainScreen
import com.iank.im.peek.ui.theme.PeekTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appVisibilityTracker: AppVisibilityTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            PeekTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val conversationId = intent?.getStringExtra(AiGenerationService.EXTRA_CONVERSATION_ID)
        if (!conversationId.isNullOrBlank()) {
            intent.removeExtra(AiGenerationService.EXTRA_CONVERSATION_ID)
            appVisibilityTracker.dispatchNotificationConversationClick(conversationId)
        }
    }
}
