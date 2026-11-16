package com.iank.im.peek.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iank.im.peek.domain.model.ImageResolutionPreset

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"])]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val activeTargetId: String? = null,
    val overrideResolutionPreset: ImageResolutionPreset?,
    val createdAt: Long,
    val updatedAt: Long
)
