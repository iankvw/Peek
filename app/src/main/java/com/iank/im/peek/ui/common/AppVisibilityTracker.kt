package com.iank.im.peek.ui.common

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppVisibilityTracker @Inject constructor() : Application.ActivityLifecycleCallbacks {
    private val startedCount = AtomicInteger(0)

    val isAppInForeground: Boolean
        get() = startedCount.get() > 0

    private val _notificationConversationClicks = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 8
    )
    val notificationConversationClicks: SharedFlow<String> = _notificationConversationClicks.asSharedFlow()

    fun dispatchNotificationConversationClick(conversationId: String) {
        if (conversationId.isNotBlank()) {
            _notificationConversationClicks.tryEmit(conversationId)
        }
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
