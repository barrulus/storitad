package uk.storitad.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import uk.storitad.capture.ui.DetailScreen
import uk.storitad.capture.ui.EntryListScreen
import uk.storitad.capture.ui.HistoryScreen
import uk.storitad.capture.ui.HomeScreen
import uk.storitad.capture.ui.MetadataScreen
import uk.storitad.capture.ui.RecordingScreen
import uk.storitad.capture.ui.ReviewScreen
import uk.storitad.capture.ui.SettingsScreen
import uk.storitad.capture.ui.VideoRecordingScreen
import uk.storitad.capture.ui.Route
import uk.storitad.capture.ui.theme.StoritadTheme
import uk.storitad.capture.reminders.ReminderNotification
import uk.storitad.capture.reminders.ReminderPrefs
import uk.storitad.capture.reminders.ReminderScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val quickVoice = intent?.action == "uk.storitad.capture.action.QUICK_VOICE"
        ReminderNotification.ensureChannel(this)
        if (ReminderPrefs(this).enabled) {
            ReminderScheduler.reschedule(this)
        }
        setContent { StoritadTheme { App(startOnRecording = quickVoice) } }
    }
}

@Composable
private fun App(startOnRecording: Boolean) {
    val nav = rememberNavController()
    val start = if (startOnRecording) Route.Recording.path else Route.Home.path
    NavHost(navController = nav, startDestination = start) {
        composable(Route.Home.path) {
            HomeScreen(
                onRecordVoice = { nav.navigate(Route.Recording.path) },
                onRecordVideo = { nav.navigate(Route.VideoRecording.path) },
                onPending = { nav.navigate(Route.Pending.path) },
                onHistory = { nav.navigate(Route.History.path) },
                onSettings = { nav.navigate(Route.Settings.path) }
            )
        }
        composable(Route.Recording.path) {
            RecordingScreen(
                onStopped = { basename ->
                    nav.navigate(Route.Review.of(basename)) {
                        popUpTo(Route.Home.path)
                    }
                },
                onCancel = { nav.popBackStack() }
            )
        }
        composable(Route.VideoRecording.path) {
            VideoRecordingScreen(
                onStopped = { basename ->
                    nav.navigate(Route.Review.of(basename)) {
                        popUpTo(Route.Home.path)
                    }
                },
                onCancel = { nav.popBackStack() }
            )
        }
        composable(
            Route.Review.path,
            arguments = listOf(navArgument("basename") { type = NavType.StringType })
        ) { entry ->
            val basename = entry.arguments!!.getString("basename")!!
            ReviewScreen(
                basename = basename,
                onContinue = { nav.navigate(Route.Metadata.of(basename)) },
                onRerecord = {
                    val reRoute = if (basename.contains("-video"))
                        Route.VideoRecording.path else Route.Recording.path
                    nav.navigate(reRoute) { popUpTo(Route.Home.path) }
                },
                onDiscard = { nav.popBackStack(Route.Home.path, inclusive = false) }
            )
        }
        composable(
            Route.Metadata.path,
            arguments = listOf(
                navArgument("basename") { type = NavType.StringType },
                navArgument("edit") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            val basename = entry.arguments!!.getString("basename")!!
            val edit = entry.arguments!!.getBoolean("edit")
            MetadataScreen(
                basename = basename,
                editExisting = edit,
                onSaved = { nav.popBackStack(Route.Home.path, inclusive = false) },
                onDiscard = { nav.popBackStack(Route.Home.path, inclusive = false) }
            )
        }
        composable(Route.Pending.path) {
            EntryListScreen(
                title = "Pending",
                onlyPending = true,
                onOpen = { basename -> nav.navigate(Route.Detail.of(basename)) },
                onEdit = { basename -> nav.navigate(Route.Metadata.of(basename, edit = true)) },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Route.History.path) {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Route.Detail.path,
            arguments = listOf(navArgument("basename") { type = NavType.StringType })
        ) { entry ->
            val basename = entry.arguments!!.getString("basename")!!
            DetailScreen(
                basename = basename,
                onBack = { nav.popBackStack() },
                onEdit = { b -> nav.navigate(Route.Metadata.of(b, edit = true)) }
            )
        }
        composable(Route.Settings.path) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
