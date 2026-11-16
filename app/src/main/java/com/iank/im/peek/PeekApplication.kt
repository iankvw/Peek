package com.iank.im.peek

import android.app.Application
import android.view.Choreographer
import com.iank.im.peek.data.local.maintenance.StartupMaintenance
import com.iank.im.peek.di.ApplicationScope
import com.iank.im.peek.ui.common.AppVisibilityTracker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PeekApplication : Application() {

    @Inject
    lateinit var startupMaintenance: StartupMaintenance

    @Inject
    lateinit var appVisibilityTracker: AppVisibilityTracker

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(appVisibilityTracker)
        Choreographer.getInstance().postFrameCallback {
            applicationScope.launch {
                startupMaintenance.runOnStartupOnce()
            }
        }
    }
}
