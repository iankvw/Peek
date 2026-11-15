package com.iank.im.peek.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_file_deletions")
data class PendingFileDeletionEntity(
    @PrimaryKey val relPath: String,
    val enqueuedAt: Long
)
