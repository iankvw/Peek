package com.iank.im.peek.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "remote_file_cache",
    primaryKeys = ["attachmentId", "credentialFingerprint"],
    foreignKeys = [
        ForeignKey(
            entity = MessageAttachmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["attachmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RemoteFileCacheEntity(
    val attachmentId: String,
    val credentialFingerprint: String,
    val payloadRevision: Int,
    val fileName: String,
    val fileUri: String,
    val mimeType: String,
    val state: String,
    val expiresAt: Long,
    val createdAt: Long
)
