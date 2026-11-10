package com.tijoriapp.vault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.data.ThemeMode
import com.tijoriapp.vault.data.VaultSession
import com.tijoriapp.vault.ui.*
import com.tijoriapp.vault.ui.theme.*

class UiHolder : ViewModel() {
    val nav = Navigator()
    /** True from "Create" until the last setup step, even though the vault is already unlocked. */
    var finishingSetup by mutableStateOf(false)
    /** Kept only during setup (to turn on fingerprint unlock), cleared at the end. */
    var setupPassphrase: String = ""
    var setupSecretKey: String = ""
}

@Composable
fun AppRoot(app: TijoriApp, host: Host) {
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val state by app.session.state.collectAsStateWithLifecycle()
    Lang.current = settings.language
    val dark = when (settings.theme) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
    val holder: UiHolder = viewModel()
    val nav = holder.nav
    LaunchedEffect(state.phase) { if (state.phase != VaultSession.Phase.Unlocked) nav.reset() }

    TijoriTheme(dark) {
        CompositionLocalProvider(LocalHost provides host, LocalNav provides nav) {
            val snackbar = remember { SnackbarHostState() }
            val msg by Toasts.flow.collectAsStateWithLifecycle()
            LaunchedEffect(msg) {
                msg?.let { Toasts.flow.value = null; snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) }
            }
            Box(Modifier.fillMaxSize().background(Paper)) {
                val screen = when {
                    state.phase == VaultSession.Phase.NoVault -> "setup"
                    state.phase == VaultSession.Phase.Unlocked && holder.finishingSetup -> "setup"
                    else -> state.phase.name
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                    label = "phase",
                ) { key ->
                    when (key) {
                        "setup" -> OnboardingFlow(app, holder)
                        VaultSession.Phase.Locked.name -> LockScreen(app)
                        else -> MainShell(app)
                    }
                }
                CrashReportDialog(app)
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp, start = 12.dp, end = 12.dp))
            }
        }
    }
}

private data class TabItem(val tab: MainTab, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem(MainTab.Vault, "Vault", Icons.Filled.Shield),
    TabItem(MainTab.Security, "Security", Icons.Filled.HealthAndSafety),
    TabItem(MainTab.Family, "Family", Icons.Filled.FamilyRestroom),
    TabItem(MainTab.Settings, "Settings", Icons.Filled.Settings),
)

@Composable
fun MainShell(app: TijoriApp) {
    val nav = LocalNav.current
    val top = nav.top
    BackHandler(enabled = top != null || nav.tab != MainTab.Vault) { if (!nav.pop()) nav.tab = MainTab.Vault }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (top == null) NavigationBar(containerColor = Ink) {
                TABS.forEach { item ->
                    NavigationBarItem(
                        selected = nav.tab == item.tab,
                        onClick = { nav.tab = item.tab },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(t(item.label), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink, selectedTextColor = Haldi, indicatorColor = Haldi,
                            unselectedIconColor = MutedOnDark, unselectedTextColor = MutedOnDark,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (top == null && nav.tab == MainTab.Vault) ExtendedFloatingActionButton(
                onClick = { nav.push(Dest.PickType) },
                containerColor = Haldi, contentColor = Ink,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(t("Add"), style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = top ?: nav.tab,
            transitionSpec = { fadeIn(tween(200, delayMillis = 40)) togetherWith fadeOut(tween(100)) },
            label = "screen",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { key ->
            when (key) {
                MainTab.Vault -> HomeTab(app)
                MainTab.Security -> SecurityTab(app)
                MainTab.Family -> FamilyTab(app)
                MainTab.Settings -> SettingsTab(app)
                is Dest.RoomList -> RoomScreen(app, key.room)
                is Dest.Item -> ItemDetailScreen(app, key.id)
                is Dest.Edit -> EditItemScreen(app, key)
                Dest.PickType -> PickTypeScreen()
                Dest.Trust -> TrustScreen(app)
                Dest.ChangePassphrase -> ChangePassphraseScreen(app)
                Dest.About -> AboutScreen()
                is Dest.Nominee -> NomineeScreen(app, practice = key.practice, onClose = { nav.pop() })
                else -> {}
            }
        }
    }
}

/** Shown once after a crash: lets the user copy the report and send it to the developer (no internet needed). */
@Composable
private fun CrashReportDialog(app: TijoriApp) {
    var report by remember { mutableStateOf(com.tijoriapp.vault.security.CrashLog.read(app)) }
    val text = report ?: return
    fun close() { com.tijoriapp.vault.security.CrashLog.clear(app); report = null }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("Tijori closed unexpectedly last time") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your vault is safe. Copy this report and send it to the developer so it can be fixed. It contains no vault data.",
                    style = MaterialTheme.typography.bodyMedium)
                Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono))
            }
        },
        confirmButton = {
            TextButton(onClick = { app.copy("Tijori crash report", text, sensitive = false); Toasts.show("Report copied"); close() }) { Text("Copy report") }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text("Close") } },
    )
}
