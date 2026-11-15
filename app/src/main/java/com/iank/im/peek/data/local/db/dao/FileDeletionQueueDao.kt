package com.iank.im.peek.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iank.im.peek.data.local.db.entity.PendingFileDeletionEntity
import com.iank.im.peek.data.local.db.entity.PendingRemoteDeletionEntity

@Dao
interface FileDeletionQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueLocalDeletions(entries: List<PendingFileDeletionEntity>)

    @Query("SELECT * FROM pending_file_deletions ORDER BY enqueuedAt ASC")
    suspend fun getPendingLocalDeletions(): List<PendingFileDeletionEntity>

    @Query("DELETE FROM pending_file_deletions WHERE relPath IN (:relPaths)")
    suspend fun removeLocalDeletions(relPaths: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueRemoteDeletions(entries: List<PendingRemoteDeletionEntity>)

    @Query("SELECT * FROM pending_remote_deletions ORDER BY enqueuedAt ASC")
    suspend fun getPendingRemoteDeletions(): List<PendingRemoteDeletionEntity>

    @Query("DELETE FROM pending_remote_deletions WHERE fileName = :fileName AND credentialFingerprint = :fingerprint")
    suspend fun removeRemoteDeletion(fileName: String, fingerprint: String)

    @Query("DELETE FROM pending_remote_deletions WHERE enqueuedAt <= :cutoffMillis")
    suspend fun removeExpiredRemoteDeletions(cutoffMillis: Long)
}
