package com.iank.im.peek.domain.usecase

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveSendRegistry @Inject constructor() {
    private val jobsByConversation = ConcurrentHashMap<String, Job>()
    private val activeMessageIds = ConcurrentHashMap.newKeySet<String>()

    private val _activeConversationIds = MutableStateFlow<Set<String>>(emptySet())
    val activeConversationIds: StateFlow<Set<String>> = _activeConversationIds.asStateFlow()

    fun register(conversationId: String, assistantMessageId: String, job: Job) {
        jobsByConversation[conversationId] = job
        activeMessageIds.add(assistantMessageId)
        _activeConversationIds.value = jobsByConversation.keys.toSet()
    }

    fun unregister(conversationId: String, assistantMessageId: String, job: Job) {
        jobsByConversation.remove(conversationId, job)
        activeMessageIds.remove(assistantMessageId)
        _activeConversationIds.value = jobsByConversation.keys.toSet()
    }

    fun cancel(conversationId: String): Boolean {
        val job = jobsByConversation.remove(conversationId)
        _activeConversationIds.value = jobsByConversation.keys.toSet()
        return if (job != null) {
            job.cancel()
            true
        } else {
            false
        }
    }

    fun isMessageActivelySending(messageId: String): Boolean =
        activeMessageIds.contains(messageId)
}
