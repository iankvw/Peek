package com.iank.im.peek.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iank.im.peek.domain.model.ApiProvider
import com.iank.im.peek.domain.model.ImagePayloadFormat

@Entity(tableName = "api_presets")
data class ApiPresetEntity(
    @PrimaryKey val id: String,
    val alias: String,
    val provider: ApiProvider,
    val customBaseUrlEnabled: Boolean,
    val customBaseUrl: String?,
    val modelId: String,
    val imagePayloadFormat: ImagePayloadFormat,
    val maxRequestBytes: Long?,
    val createdAt: Long
)
