package com.iank.im.peek.data.local.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iank.im.peek.data.local.db.entity.ConversationEntity
import com.iank.im.peek.domain.model.ImageResolutionPreset
import kotlinx.coroutines.flow.Flow

data class ConversationSearchRow(
    @Embedded val conversation: ConversationEntity,
    val matchedMessageText: String?,
    val matchedCaptionText: String?
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT c.*,
            (
                SELECT m.textContent
                FROM messages m
                WHERE m.conversationId = c.id
                  AND m.textContent LIKE '%' || :query || '%' ESCAPE '\'
                ORDER BY m.position DESC
                LIMIT 1
            ) AS matchedMessageText,
            (
                SELECT ma.imageCaption
                FROM message_attachments ma
                INNER JOIN messages m2 ON ma.messageId = m2.id
                WHERE m2.conversationId = c.id
                  AND ma.imageCaption IS NOT NULL
                  AND ma.imageCaption LIKE '%' || :query || '%' ESCAPE '\'
                ORDER BY m2.position DESC, ma.sortIndex ASC
                LIMIT 1
            ) AS matchedCaptionText
        FROM conversations c
        WHERE c.title LIKE '%' || :query || '%' ESCAPE '\'
           OR EXISTS (
               SELECT 1 FROM messages m
               WHERE m.conversationId = c.id
                 AND m.textContent LIKE '%' || :query || '%' ESCAPE '\'
           )
           OR EXISTS (
               SELECT 1 FROM message_attachments ma
               INNER JOIN messages m2 ON ma.messageId = m2.id
               WHERE m2.conversationId = c.id
                 AND ma.imageCaption IS NOT NULL
                 AND ma.imageCaption LIKE '%' || :query || '%' ESCAPE '\'
           )
        ORDER BY c.updatedAt DESC
        """
    )
    fun observeByQuery(query: String): Flow<List<ConversationSearchRow>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateUpdatedAt(id: String, updatedAt: Long)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    @Query("UPDATE conversations SET activeTargetId = :targetId WHERE id = :id")
    suspend fun updateActiveTargetId(id: String, targetId: String?)

    @Query("UPDATE conversations SET overrideResolutionPreset = :preset, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOverrideResolution(id: String, preset: ImageResolutionPreset?, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)
}
