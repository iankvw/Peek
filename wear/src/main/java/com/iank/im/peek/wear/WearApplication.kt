package com.iank.im.peek.wear

import android.app.Application
import com.iank.im.peek.wear.data.bridge.WearPhoneBridgeClient
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WearApplication : Application() {

    @Inject
    lateinit var bridgeClient: WearPhoneBridgeClient

    override fun onCreate() {
        super.onCreate()
        bridgeClient.start()
    }
}
