package com.tijoriapp.vault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tijoriapp.vault.AppController
import com.tijoriapp.vault.AppTab
import com.tijoriapp.vault.data.ItemType
import com.tijoriapp.vault.data.RoomType

data class PhoneActions(
    val onOpenAppInfoPermissions: () -> Unit,
    val onExportEmergencyKitPdf: () -> Unit,
    val onExportNomineeCardsPdf: () -> Unit,
    val onCopySensitive: (String, String) -> Unit,
    val onTriggerBiometricUnlock: () -> Unit,
)

private data class NavItem(val tab: AppTab, val label: String, val icon: ImageVector)

private val NAV_TABS = listOf(
    NavItem(AppTab.Rooms, "Rooms", Icons.Filled.MeetingRoom),
    NavItem(AppTab.Nominee, "Nominee Kit", Icons.Filled.FamilyRestroom),
    NavItem(AppTab.Trust, "Trust Page", Icons.Filled.Shield),
    NavItem(AppTab.Settings, "Settings", Icons.Filled.Settings),
)

@Composable
fun MainShell(app: AppController, actions: PhoneActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(app.message) {
        app.message?.let {
            snackbar.showSnackbar(it)
            app.message = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TijoriTopBar(app = app, actions = actions)
        },
        floatingActionButton = {
            if (app.openItemId == null && app.editingItem == null && app.addingTemplate == null) {
                FloatingActionButton(
                    onClick = {
                        app.addingTemplate = com.tijoriapp.vault.data.IndiaTemplates.ALL.first()
                    },
                    containerColor = VaultGold,
                    contentColor = VaultNavy,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(t("Add New Login"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = VaultNavy) {
                NAV_TABS.forEach { item ->
                    val selected = app.tab == item.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            app.openItemId = null
                            app.editingItem = null
                            app.addingTemplate = null
                            app.tab = item.tab
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(t(item.label), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VaultNavy,
                            selectedTextColor = VaultGold,
                            indicatorColor = VaultGold,
                            unselectedIconColor = MutedOnDark,
                            unselectedTextColor = MutedOnDark,
                        ),
                    )
                }
            }
        },
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val screenKey = "${app.tab}:${app.openItemId}:${app.editingItem?.id}:${app.addingTemplate?.title}"
        AnimatedContent(
            targetState = screenKey,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
            label = "tijori_screens",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { _ ->
            Box(Modifier.fillMaxSize()) {
                when {
                    app.addingTemplate != null -> {
                        BackHandler { app.addingTemplate = null }
                        AddOrEditItemScreen(
                            app = app,
                            template = app.addingTemplate,
                            existingItem = null,
                            onClose = { app.addingTemplate = null },
                        )
                    }
                    app.editingItem != null -> {
                        BackHandler { app.editingItem = null }
                        AddOrEditItemScreen(
                            app = app,
                            template = null,
                            existingItem = app.editingItem,
                            onClose = { app.editingItem = null },
                        )
                    }
                    app.openItemId != null -> {
                        val openItem = app.items.firstOrNull { it.id == app.openItemId }
                        if (openItem != null) {
                            BackHandler { app.openItemId = null }
                            ItemDetailScreen(
                                item = openItem,
                                app = app,
                                actions = actions,
                                onBack = { app.openItemId = null },
                            )
                        } else {
                            RoomsHomeScreen(app = app, actions = actions)
                        }
                    }
                    else -> {
                        when (app.tab) {
                            AppTab.Rooms -> RoomsHomeScreen(app = app, actions = actions)
                            AppTab.Nominee -> NomineeKitScreen(app = app, actions = actions)
                            AppTab.Trust -> TrustPageScreen(app = app, actions = actions)
                            AppTab.Settings -> SettingsScreen(app = app, actions = actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TijoriTopBar(app: AppController, actions: PhoneActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(VaultNavy)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(VaultGold),
            contentAlignment = Alignment.Center,
        ) {
            Text("🗝️", fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Tijori", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                t("Chaabi sirf aapke paas."),
                color = VaultGold,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            )
        }

        // Language toggle
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(VaultNavy2)
                .border(1.dp, VaultLine, RoundedCornerShape(50))
                .clickable {
                    val next = if (app.language == "en") "hi" else "en"
                    app.language = next
                    Lang.current = next
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (app.language == "hi") "English" else "हिन्दी",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Lock Vault button
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(VaultNavy2)
                .border(1.dp, VaultLine, RoundedCornerShape(50))
                .clickable { app.lockVault() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = VaultCoral, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Lock", color = VaultCoral, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
