package com.iank.im.peek.data.local.db.converter

import androidx.room.TypeConverter
import com.iank.im.peek.domain.model.ApiProvider
import com.iank.im.peek.domain.model.ImagePayloadFormat
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.model.MessageStatus

class RoomConverters {
    @TypeConverter
    fun fromApiProvider(value: ApiProvider): String = value.name

    @TypeConverter
    fun toApiProvider(value: String): ApiProvider =
        ApiProvider.entries.firstOrNull { it.name == value } ?: ApiProvider.GEMINI

    @TypeConverter
    fun fromImagePayloadFormat(value: ImagePayloadFormat): String = value.name

    @TypeConverter
    fun toImagePayloadFormat(value: String): ImagePayloadFormat =
        ImagePayloadFormat.entries.firstOrNull { it.name == value } ?: ImagePayloadFormat.WEBP

    @TypeConverter
    fun fromImageResolutionPreset(value: ImageResolutionPreset?): String? = value?.name

    @TypeConverter
    fun toImageResolutionPreset(value: String?): ImageResolutionPreset? =
        value?.let { name -> ImageResolutionPreset.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun fromMessageRole(value: MessageRole): String = value.name

    @TypeConverter
    fun toMessageRole(value: String): MessageRole =
        MessageRole.entries.firstOrNull { it.name == value } ?: MessageRole.USER

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus =
        MessageStatus.entries.firstOrNull { it.name == value } ?: MessageStatus.ERROR
}
