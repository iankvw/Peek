package com.iank.im.peek.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "pending_remote_deletions",
    primaryKeys = ["fileName", "credentialFingerprint"]
)
data class PendingRemoteDeletionEntity(
    val fileName: String,
    val credentialFingerprint: String,
    val enqueuedAt: Long
)
