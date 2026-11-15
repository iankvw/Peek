package com.iank.im.peek.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iank.im.peek.domain.model.ImageResolutionPreset

@Entity(
    tableName = "message_attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class MessageAttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val sortIndex: Int,
    val originalRelPath: String,
    val originalMimeType: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val thumbnailRelPath: String,
    val payloadRelPath: String?,
    val payloadMaxSide: Int?,
    val payloadRevision: Int,
    val requestedPreset: ImageResolutionPreset,
    val isTextReplaced: Boolean,
    val imageCaption: String?
)
