package com.iank.im.peek.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GroupRoutingMode {
    FALLBACK,
    ROUND_ROBIN
}

@Serializable
enum class RoundRobinSubMode {
    SEQUENTIAL,
    RANDOM
}

@Serializable
data class ModelGroup(
    val id: String,
    val name: String,
    val memberPresetIds: List<String>,
    val routingMode: GroupRoutingMode = GroupRoutingMode.FALLBACK,
    val roundRobinSubMode: RoundRobinSubMode = RoundRobinSubMode.SEQUENTIAL,
    val failoverOnRoundRobinError: Boolean = true,
    val lastUsedIndex: Int = -1,
    val createdAt: Long = System.currentTimeMillis()
)

sealed interface SelectableTarget {
    val id: String
    val displayName: String
    val isGroup: Boolean

    data class PresetTarget(val preset: ApiPreset) : SelectableTarget {
        override val id: String get() = preset.id
        override val displayName: String get() = preset.alias.ifBlank { preset.modelId }
        override val isGroup: Boolean get() = false
    }

    data class GroupTarget(
        val group: ModelGroup,
        val memberNames: List<String>
    ) : SelectableTarget {
        override val id: String get() = group.id
        override val displayName: String get() = group.name
        override val isGroup: Boolean get() = true
    }
}

sealed interface ExecutionTarget {
    val targetId: String
    val forceInlineBase64: Boolean
    val orderedCandidates: List<Pair<ApiPreset, String>>

    data class Single(
        val preset: ApiPreset,
        val apiKey: String
    ) : ExecutionTarget {
        override val targetId: String get() = preset.id
        override val forceInlineBase64: Boolean get() = false
        override val orderedCandidates: List<Pair<ApiPreset, String>>
            get() = listOf(preset to apiKey)
    }

    data class Group(
        val group: ModelGroup,
        override val orderedCandidates: List<Pair<ApiPreset, String>>
    ) : ExecutionTarget {
        override val targetId: String get() = group.id
        override val forceInlineBase64: Boolean get() = true
    }
}
