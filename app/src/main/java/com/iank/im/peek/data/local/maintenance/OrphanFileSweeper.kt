package com.iank.im.peek.data.local.maintenance

import com.iank.im.peek.data.local.datastore.SettingsDataStore
import com.iank.im.peek.data.local.db.dao.MessageDao
import com.iank.im.peek.data.local.image.InternalImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrphanFileSweeper @Inject constructor(
    private val messageDao: MessageDao,
    private val imageStorage: InternalImageStorage,
    private val settingsDataStore: SettingsDataStore
) {
    suspend fun sweepIfDue(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force) {
            val lastSweepAt = settingsDataStore.getLastOrphanSweepAt()
            if (now - lastSweepAt < SWEEP_INTERVAL_MS) {
                return@withContext 0
            }
        }

        val referenced = messageDao.getAllReferencedRelPaths()
        val referencedSet = HashSet<String>(referenced.size * 3)
        for (item in referenced) {
            referencedSet.add(item.originalRelPath)
            referencedSet.add(item.thumbnailRelPath)
            item.payloadRelPath?.let { referencedSet.add(it) }
        }

        val graceCutoff = now - UNREFERENCED_GRACE_PERIOD_MS
        var deletedCount = 0
        for ((relPath, file) in imageStorage.listCommittedImageFiles()) {
            if (relPath !in referencedSet && file.lastModified() < graceCutoff) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }

        settingsDataStore.setLastOrphanSweepAt(now)
        deletedCount
    }

    companion object {
        private const val SWEEP_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val UNREFERENCED_GRACE_PERIOD_MS = 60L * 60L * 1000L
    }
}
