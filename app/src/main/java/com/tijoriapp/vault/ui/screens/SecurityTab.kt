package com.tijoriapp.vault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tijoriapp.vault.TijoriApp
import com.tijoriapp.vault.core.vault.Health
import com.tijoriapp.vault.core.vault.Room
import com.tijoriapp.vault.ui.*
import com.tijoriapp.vault.ui.components.*
import com.tijoriapp.vault.ui.theme.*

/** Offline "Watchtower": everything is computed on the phone from the decrypted items in memory. */
@Composable
fun SecurityTab(app: TijoriApp) {
    val nav = LocalNav.current
    val state by app.session.state.collectAsStateWithLifecycle()
    val report = remember(state.items) { Health.analyze(state.items, words = app.words) }

    HeroPage(hero = {
        Text(t("Security"), color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Text("Checked on this phone. Nothing is sent anywhere to compare.", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(report.score, Modifier.size(140.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${report.score}", color = Color.White, style = MaterialTheme.typography.displayMedium)
                    Text("of 100", color = MutedOnDark, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(when { report.score >= 85 -> "Excellent"; report.score >= 65 -> "Good, a few fixes left"; report.score >= 40 -> "Needs work"; else -> "At risk" },
                    color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("${report.passwords} passwords checked", color = MutedOnDark, style = MaterialTheme.typography.bodySmall)
                if (report.needsAttention > 0) Text("${report.needsAttention} items to fix", color = Coral, style = MaterialTheme.typography.labelMedium)
            }
        }
    }) {
        if (!state.moneyOpen && (state.counts[Room.MONEY] ?: 0) > 0) {
            Banner("Money room items are checked only while it's open.", action = "Open") { nav.push(Dest.RoomList(Room.MONEY)) }
        }
        Group(app, report, Health.Issue.Weak, t("Weak passwords"), "Easy to guess. Use the generator to replace them.", Coral)
        Group(app, report, Health.Issue.Reused, t("Reused passwords"), "If one site leaks, the others fall too. Give each a different password.", Coral)
        Group(app, report, Health.Issue.Expired, t("Expired"), "Cards, documents or premiums that have passed their date.", Coral)
        Group(app, report, Health.Issue.Expiring, t("Expiring soon"), "Renew before the date to avoid trouble.", Haldi)
        Group(app, report, Health.Issue.Old, "Old banking passwords", "Banking passwords not changed in over a year.", Haldi)
        Group(app, report, Health.Issue.No2FA, "Tips", "2-step verification stops most account takeovers.", Violet)
        if (report.findings.isEmpty()) EmptyState(Icons.Filled.VerifiedUser, "All clear", if (state.items.isEmpty()) "Add some items and Tijori will keep an eye on them." else "No weak, reused or expiring items. Nice work.")

        SectionLabel("Your setup")
        AppCard {
            val s by app.prefs.settings.collectAsStateWithLifecycle()
            val host = LocalHost.current
            StatusRow("Emergency Kit saved", if (s.kitSavedAt > 0) "Saved ${relativeTime(s.kitSavedAt)}" else "Needed to restore on a new phone", s.kitSavedAt > 0,
                action = if (s.kitSavedAt > 0) null else "Fix") { nav.tab = MainTab.Settings }
            StatusRow("Recent backup", if (s.lastBackupAt > 0) "Last one ${relativeTime(s.lastBackupAt)}" else "No backup yet",
                s.lastBackupAt > 0 && System.currentTimeMillis() - s.lastBackupAt < 30L * 86_400_000L, action = "Back up") { nav.tab = MainTab.Settings }
            StatusRow("Nominee cards", if (s.cardsMadeAt > 0) "Made ${relativeTime(s.cardsMadeAt)}" else "Family can't open the Family room yet", s.cardsMadeAt > 0,
                action = if (s.cardsMadeAt > 0) null else "Set up") { nav.tab = MainTab.Family }
            StatusRow("Screenshots blocked", if (s.blockScreenshots) "Screen recorders see a black screen" else "Turned off in Settings", s.blockScreenshots)
            StatusRow("Fingerprint unlock", if (app.session.biometricReady) "Hardware-bound, per-use" else "Off (passphrase only)", if (host.biometricAvailable) app.session.biometricReady else null)
        }
    }
}

@Composable
private fun Group(app: TijoriApp, report: Health.Report, issue: Health.Issue, title: String, body: String, dot: Color) {
    val nav = LocalNav.current
    val list = report.of(issue)
    if (list.isEmpty()) return
    AppCard(title = "$title · ${list.size}") {
        Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
        list.distinctBy { it.item.id }.forEach { f ->
            ItemRow(f.item, trailing = { Chip(f.detail.take(28), CardBg2, Muted) }) { nav.push(Dest.Item(f.item.id)) }
        }
    }
}
