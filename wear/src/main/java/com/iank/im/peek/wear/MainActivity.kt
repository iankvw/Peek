package com.iank.im.peek.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.iank.im.peek.wear.ui.navigation.WearNavGraph
import com.iank.im.peek.wear.ui.theme.PeekWearTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            PeekWearTheme {
                WearNavGraph()
            }
        }
    }
}
