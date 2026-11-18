package com.iank.im.peek.wear.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.iank.im.peek.wear.ui.chat.WearChatScreen
import com.iank.im.peek.wear.ui.chat.WearInputLauncher
import com.iank.im.peek.wear.ui.chat.WearReportScreen
import com.iank.im.peek.wear.ui.chat.WearTextInputScreen
import com.iank.im.peek.wear.ui.home.WearHomeScreen
import com.iank.im.peek.wear.ui.settings.WearInputSettingsScreen
import com.iank.im.peek.wear.ui.settings.WearQualitySettingsScreen
import com.iank.im.peek.wear.ui.settings.WearSettingsScreen
import com.iank.im.peek.wear.ui.settings.WearTargetSettingsScreen
import com.iank.im.peek.wear.ui.viewer.WearImageViewerScreen

object WearRoutes {
    const val HOME = "home"
    const val CHAT = "chat?conversationId={conversationId}"
    const val TEXT_INPUT = "text_input"
    const val IMAGE_VIEWER = "image_viewer/{attachmentId}?revision={revision}"
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

    fun imageViewerRoute(attachmentId: String, payloadRevision: Int = 0): String =
        "image_viewer/${Uri.encode(attachmentId)}?revision=$payloadRevision"

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
        ) { backStackEntry ->
            val submittedText by backStackEntry.savedStateHandle
                .getStateFlow<String?>(WearInputLauncher.KEY_SUBMITTED_TEXT, null)
                .collectAsStateWithLifecycle()
            WearChatScreen(
                submittedText = submittedText,
                onConsumeSubmittedText = {
                    backStackEntry.savedStateHandle[WearInputLauncher.KEY_SUBMITTED_TEXT] = null
                },
                onOpenTextInput = {
                    navController.navigate(WearRoutes.TEXT_INPUT)
                },
                onOpenImageViewer = { attachmentId, payloadRevision ->
                    navController.navigate(WearRoutes.imageViewerRoute(attachmentId, payloadRevision))
                },
                onOpenReportMessage = { messageId ->
                    navController.navigate(WearRoutes.reportMessageRoute(messageId))
                }
            )
        }

        composable(WearRoutes.TEXT_INPUT) {
            WearTextInputScreen(
                onSubmit = { text ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(WearInputLauncher.KEY_SUBMITTED_TEXT, text)
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = WearRoutes.IMAGE_VIEWER,
            arguments = listOf(
                navArgument("attachmentId") { type = NavType.StringType },
                navArgument("revision") {
                    type = NavType.IntType
                    defaultValue = 0
                }
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
