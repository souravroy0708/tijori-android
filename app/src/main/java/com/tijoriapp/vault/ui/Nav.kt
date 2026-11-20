package com.tijoriapp.vault.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tijoriapp.vault.core.vault.ItemType
import com.tijoriapp.vault.core.vault.Room
import android.net.Uri
import java.io.OutputStream
import javax.crypto.Cipher

enum class MainTab { Vault, Security, Family, Settings }

/** Full-screen destinations pushed on top of the tabs. Plain data: no secrets in navigation state. */
sealed interface Dest {
    data class RoomList(val room: Room) : Dest
    data class Item(val id: String) : Dest
    data class Edit(val id: String?, val type: ItemType? = null, val room: Room? = null) : Dest
    data object PickType : Dest
    data object Trust : Dest
    data object ChangePassphrase : Dest
    data object About : Dest
    data class Nominee(val practice: ByteArray? = null) : Dest
    data object DriveBackup : Dest
    data object Import : Dest
    data class Help(val topic: String? = null) : Dest
}

/** A tiny back stack. Enough for this app and easy to reason about (no reflection, no routes as strings). */
class Navigator {
    var tab by mutableStateOf(MainTab.Vault)
    val stack = mutableStateListOf<Dest>()
    val top: Dest? get() = stack.lastOrNull()

    fun push(d: Dest) { stack.add(d) }
    fun pop(): Boolean = if (stack.isEmpty()) false else { stack.removeAt(stack.lastIndex); true }
    fun replace(d: Dest) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); stack.add(d) }
    fun reset(to: MainTab = MainTab.Vault) { stack.clear(); tab = to }
}

/** Things only the Activity can do (system dialogs, file pickers, biometric prompt). */
interface Host {
    fun biometric(title: String, subtitle: String, cipher: Cipher, onSuccess: (Cipher) -> Unit, onFail: (String?) -> Unit)
    fun createFile(fileName: String, mime: String, write: suspend (OutputStream) -> Unit, onDone: (Boolean) -> Unit)
    fun openFile(mimes: Array<String>, onRead: (ByteArray?) -> Unit)
    /** "Save to…" picker for the auto-backup file. The returned link stays usable across restarts. */
    fun pickBackupDestination(onPicked: (Uri?) -> Unit) = onPicked(null)
    /** Picks an existing backup (e.g. in Google Drive) with lasting read/write access, and reads it. */
    fun openBackupFile(onPicked: (Uri?, ByteArray?) -> Unit) = onPicked(null, null)
    fun openAutofillSettings()
    fun openUrl(url: String)
    val biometricAvailable: Boolean
    val autofillEnabled: Boolean
}

val LocalHost = compositionLocalOf<Host> { error("No Host") }
val LocalNav = compositionLocalOf<Navigator> { error("No Navigator") }
