package com.iank.im.peek.wear.data.bridge

import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WearClientListenerService : WearableListenerService() {

    @Inject
    lateinit var bridgeClient: WearPhoneBridgeClient

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val configPayloads = mutableListOf<String>()
        try {
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == WearBridgeConstants.PATH_STATE_CONFIG
                ) {
                    val raw = DataMapItem.fromDataItem(event.dataItem)
                        .dataMap
                        .getString(WearBridgeConstants.KEY_CONFIG_JSON)
                    if (!raw.isNullOrBlank()) {
                        configPayloads += raw
                    }
                }
            }
        } finally {
            dataEvents.release()
        }
        configPayloads.forEach { bridgeClient.handleSyncedConfigJsonFromService(it) }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        bridgeClient.handleIncomingMessage(messageEvent)
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        bridgeClient.handleIncomingChannel(channel)
    }
}
