package com.iank.im.peek.ui.common

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppVisibilityTracker @Inject constructor() : Application.ActivityLifecycleCallbacks {
    private val startedCount = AtomicInteger(0)

    val isAppInForeground: Boolean
        get() = startedCount.get() > 0

    private val _pendingNotificationConversationId = MutableStateFlow<String?>(null)
    val pendingNotificationConversationId: StateFlow<String?> =
        _pendingNotificationConversationId.asStateFlow()

    fun dispatchNotificationConversationClick(conversationId: String) {
        if (conversationId.isNotBlank()) {
            _pendingNotificationConversationId.value = conversationId
        }
    }

    fun consumeNotificationConversationClick(conversationId: String) {
        _pendingNotificationConversationId.compareAndSet(conversationId, null)
    }

    override fun onActivityStarted(activity: Activity) {
        startedCount.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
