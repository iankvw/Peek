package com.iank.im.peek.data.local.maintenance

import com.iank.im.peek.data.local.db.dao.MessageDao
import com.iank.im.peek.data.local.db.dao.RemoteFileCacheDao
import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.domain.model.ApiError
import com.iank.im.peek.domain.model.MessageStatus
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.VisionAiRepository
import com.iank.im.peek.domain.usecase.ActiveSendRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupMaintenance @Inject constructor(
    private val messageDao: MessageDao,
    private val remoteFileCacheDao: RemoteFileCacheDao,
    private val imageStorage: InternalImageStorage,
    private val conversationRepository: ConversationRepository,
    private val visionAiRepository: VisionAiRepository,
    private val orphanFileSweeper: OrphanFileSweeper,
    private val activeSendRegistry: ActiveSendRegistry
) {
    private val startupRan = AtomicBoolean(false)

    suspend fun runOnStartupOnce() {
        if (!startupRan.compareAndSet(false, true)) return
        runMaintenance(forceOrphanSweep = false)
    }

    suspend fun runManualMaintenance() {
        runMaintenance(forceOrphanSweep = true)
    }

    private suspend fun runMaintenance(forceOrphanSweep: Boolean) = withContext(Dispatchers.IO) {
        imageStorage.ensureDirectories()

        // 1. Mark stale LOADING messages (from process kill) as INTERRUPTED_BY_EXIT
        val loadingIds = messageDao.getLoadingMessageIds()
        for (msgId in loadingIds) {
            if (!activeSendRegistry.isMessageActivelySending(msgId)) {
                messageDao.updateMessageStatus(
                    messageId = msgId,
                    status = MessageStatus.ERROR,
                    errorCode = ApiError.CODE_INTERRUPTED_BY_EXIT,
                    errorMessage = null
                )
            }
        }

        // 2. Process queued local file deletions
        runCatching { conversationRepository.processPendingFileDeletions() }

        // 3. Clean expired staging files (> 24h)
        val stagingCutoff = System.currentTimeMillis() - STAGING_MAX_AGE_MS
        for ((_, file) in imageStorage.listStagingFiles()) {
            if (file.lastModified() < stagingCutoff) {
                file.delete()
            }
        }

        // 4. Clean expired remote file cache & best-effort remote deletions
        runCatching {
            remoteFileCacheDao.deleteExpired(System.currentTimeMillis())
            visionAiRepository.processPendingRemoteDeletions()
        }

        // 5. Orphan file garbage collection (throttled to <= 1/24h unless manual)
        runCatching {
            orphanFileSweeper.sweepIfDue(force = forceOrphanSweep)
        }
    }

    companion object {
        private const val STAGING_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
