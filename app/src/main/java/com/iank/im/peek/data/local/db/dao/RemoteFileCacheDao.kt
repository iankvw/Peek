package com.iank.im.peek.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iank.im.peek.data.local.db.entity.RemoteFileCacheEntity

@Dao
interface RemoteFileCacheDao {
    @Query(
        """
        SELECT * FROM remote_file_cache
        WHERE attachmentId = :attachmentId AND credentialFingerprint = :fingerprint
        LIMIT 1
        """
    )
    suspend fun get(attachmentId: String, fingerprint: String): RemoteFileCacheEntity?

    @Query("SELECT * FROM remote_file_cache WHERE attachmentId = :attachmentId")
    suspend fun getAllForAttachment(attachmentId: String): List<RemoteFileCacheEntity>

    @Query("SELECT * FROM remote_file_cache WHERE attachmentId IN (:attachmentIds)")
    suspend fun getAllForAttachments(attachmentIds: List<String>): List<RemoteFileCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RemoteFileCacheEntity)

    @Query("DELETE FROM remote_file_cache WHERE attachmentId = :attachmentId AND credentialFingerprint = :fingerprint")
    suspend fun delete(attachmentId: String, fingerprint: String)

    @Query("DELETE FROM remote_file_cache WHERE attachmentId = :attachmentId")
    suspend fun deleteByAttachmentId(attachmentId: String)

    @Query("DELETE FROM remote_file_cache WHERE attachmentId IN (:attachmentIds)")
    suspend fun deleteByAttachmentIds(attachmentIds: List<String>)

    @Query("DELETE FROM remote_file_cache WHERE expiresAt <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)
}
