package com.iank.im.peek.wear.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.iank.im.peek.wear.ui.chat.WearChatScreen
import com.iank.im.peek.wear.ui.chat.WearReportScreen
import com.iank.im.peek.wear.ui.home.WearHomeScreen
import com.iank.im.peek.wear.ui.settings.WearInputSettingsScreen
import com.iank.im.peek.wear.ui.settings.WearQualitySettingsScreen
import com.iank.im.peek.wear.ui.settings.WearSettingsScreen
import com.iank.im.peek.wear.ui.settings.WearTargetSettingsScreen
import com.iank.im.peek.wear.ui.viewer.WearImageViewerScreen

object WearRoutes {
    const val HOME = "home"
    const val CHAT = "chat?conversationId={conversationId}"
    const val IMAGE_VIEWER = "image_viewer/{attachmentId}"
    const val REPORT_MESSAGE = "report_message/{messageId}"
    const val SETTINGS = "settings"
    const val SETTINGS_QUALITY = "settings/quality"
    const val SETTINGS_TARGET = "settings/target"
    const val SETTINGS_INPUT = "settings/input"

    fun chatRoute(conversationId: String? = null): String {
        return if (conversationId.isNullOrBlank()) {
            "chat"
        } else {
            "chat?conversationId=${Uri.encode(conversationId)}"
        }
    }

    fun imageViewerRoute(attachmentId: String): String =
        "image_viewer/${Uri.encode(attachmentId)}"

    fun reportMessageRoute(messageId: String): String =
        "report_message/${Uri.encode(messageId)}"
}

@Composable
fun WearNavGraph() {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoutes.HOME
    ) {
        composable(WearRoutes.HOME) {
            WearHomeScreen(
                onOpenNewChat = {
                    navController.navigate(WearRoutes.chatRoute(null))
                },
                onOpenConversation = { conversationId ->
                    navController.navigate(WearRoutes.chatRoute(conversationId))
                },
                onOpenSettings = {
                    navController.navigate(WearRoutes.SETTINGS)
                }
            )
        }

        composable(
            route = WearRoutes.CHAT,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            WearChatScreen(
                onOpenImageViewer = { attachmentId ->
                    navController.navigate(WearRoutes.imageViewerRoute(attachmentId))
                },
                onOpenReportMessage = { messageId ->
                    navController.navigate(WearRoutes.reportMessageRoute(messageId))
                }
            )
        }

        composable(
            route = WearRoutes.IMAGE_VIEWER,
            arguments = listOf(
                navArgument("attachmentId") { type = NavType.StringType }
            )
        ) {
            WearImageViewerScreen(
                onClose = { navController.popBackStack() }
            )
        }

        composable(
            route = WearRoutes.REPORT_MESSAGE,
            arguments = listOf(
                navArgument("messageId") { type = NavType.StringType }
            )
        ) {
            WearReportScreen(
                onReportCompleted = { navController.popBackStack() }
            )
        }

        composable(WearRoutes.SETTINGS) {
            WearSettingsScreen(
                onOpenQualitySettings = { navController.navigate(WearRoutes.SETTINGS_QUALITY) },
                onOpenTargetSettings = { navController.navigate(WearRoutes.SETTINGS_TARGET) },
                onOpenInputSettings = { navController.navigate(WearRoutes.SETTINGS_INPUT) }
            )
        }

        composable(WearRoutes.SETTINGS_QUALITY) {
            WearQualitySettingsScreen(
                onSelected = { navController.popBackStack() }
            )
        }

        composable(WearRoutes.SETTINGS_TARGET) {
            WearTargetSettingsScreen(
                onSelected = { navController.popBackStack() }
            )
        }

        composable(WearRoutes.SETTINGS_INPUT) {
            WearInputSettingsScreen(
                onSelected = { navController.popBackStack() }
            )
        }
    }
}
