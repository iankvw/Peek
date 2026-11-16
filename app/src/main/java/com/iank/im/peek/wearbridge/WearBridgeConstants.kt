package com.iank.im.peek.wearbridge

object WearBridgeConstants {
    const val PATH_RPC_CONVERSATIONS_LIST = "/peek/rpc/conversations/list"
    const val PATH_RPC_CONVERSATIONS_DELETE = "/peek/rpc/conversations/delete"
    const val PATH_RPC_MESSAGES_LIST = "/peek/rpc/messages/list"
    const val PATH_RPC_CHAT_SEND = "/peek/rpc/chat/send"
    const val PATH_RPC_CHAT_RETRY = "/peek/rpc/chat/retry"
    const val PATH_RPC_CHAT_CANCEL = "/peek/rpc/chat/cancel"
    const val PATH_RPC_CHAT_REPORT = "/peek/rpc/chat/report"
    const val PATH_RPC_SETTINGS_SELECT_TARGET = "/peek/rpc/settings/select_target"
    const val PATH_RPC_IMAGE_REQUEST = "/peek/rpc/image/request"

    const val PATH_EVENT_CHAT_UPDATED = "/peek/event/chat_updated"
    const val PATH_STATE_CONFIG = "/peek/state/config"
    const val KEY_CONFIG_JSON = "config_json"
    const val PATH_CHANNEL_IMAGE_PREFIX = "/peek/channel/image/"

    const val DEFAULT_CONVERSATIONS_PAGE_SIZE = 10
    const val DEFAULT_MESSAGES_PAGE_SIZE = 15

    const val IMAGE_KIND_THUMBNAIL = "THUMBNAIL"
    const val IMAGE_KIND_FULL = "FULL"

    const val QUALITY_ORIGINAL = "ORIGINAL"
    const val QUALITY_HIGH_4000 = "HIGH_4000"
    const val QUALITY_BALANCED_2000 = "BALANCED_2000"
    const val QUALITY_STANDARD_1000 = "STANDARD_1000"
}
