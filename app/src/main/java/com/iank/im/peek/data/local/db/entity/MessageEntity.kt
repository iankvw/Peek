package com.iank.im.peek.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.model.MessageStatus

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId", "position"], unique = true)]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val position: Int,
    val turnIndex: Int,
    val role: MessageRole,
    val textContent: String,
    val status: MessageStatus,
    val errorCode: String?,
    val errorMessage: String?,
    val presetIdUsed: String?,
    val modelIdUsed: String?,
    val providerMetadataJson: String?,
    val createdAt: Long
)
