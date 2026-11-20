package com.tijoriapp.vault.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.tijoriapp.vault.core.vault.BackupFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Automatic backup to ONE file the user picked once, usually in Google Drive.
 *
 * How it stays private and still reaches the cloud:
 * - Tijori writes the file through Android's Storage Access Framework (the same "Save to…" screen as any app).
 *   When the user picks Google Drive there, the Google Drive app uploads it. Tijori itself still has
 *   NO internet permission, and Google only ever receives the already-encrypted backup.
 * - The file survives uninstalling Tijori, because it lives in the user's Drive, not in app storage.
 *   After reinstalling, "Restore from Google Drive" opens it with the passphrase (easy restore) or
 *   passphrase + Secret Key.
 *
 * Writes are debounced, so editing ten items makes one upload.
 */
class DriveBackup(
    private val context: Context,
    private val session: VaultSession,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    sealed interface Outcome {
        data object Ok : Outcome
        data object Off : Outcome
        data class Failed(val message: String) : Outcome
    }

    private val lock = Mutex()
    private var pending: Job? = null

    /** Call after any change. Writes after a short quiet period. */
    fun schedule(delayMillis: Long = DEBOUNCE_MS) {
        if (!prefs.now.driveOn || !session.hasVault) return
        prefs.markDrivePending()
        pending?.cancel()
        pending = scope.launch { delay(delayMillis); backupNow() }
    }

    /** When the app goes to the background, don't wait: the process may be killed soon. */
    fun flushIfPending() {
        if (prefs.now.driveOn && prefs.now.drivePending) { pending?.cancel(); pending = scope.launch { backupNow() } }
    }

    suspend fun backupNow(): Outcome = lock.withLock {
        val uriText = prefs.now.driveUri
        if (uriText.isEmpty() || !session.hasVault) return Outcome.Off
        val uri = Uri.parse(uriText)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = session.backupBytes(log = false)
                write(uri, bytes)
                verify(uri, bytes)
            }
        }
        result.fold(
            onSuccess = { prefs.markDriveOk(); Outcome.Ok },
            onFailure = { e ->
                val msg = friendly(e)
                prefs.markDriveError(msg)
                Outcome.Failed(msg)
            },
        )
    }

    /**
     * Remembers the file the user picked and makes the first backup right away.
     * [uri] comes from ACTION_CREATE_DOCUMENT or ACTION_OPEN_DOCUMENT with persistable permission flags.
     */
    suspend fun connect(uri: Uri): Outcome {
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.isSuccess
        if (!persisted) return Outcome.Failed("This folder doesn't allow automatic backups. Pick a folder in Google Drive instead.")
        prefs.setDrive(uri.toString(), describe(uri))
        return backupNow()
    }

    fun disconnect() {
        val u = prefs.now.driveUri
        if (u.isNotEmpty()) runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(u), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        pending?.cancel()
        prefs.clearDrive()
    }

    /** "Google Drive · Tijori-backup.tijori" or "Downloads · Tijori-backup.tijori". */
    fun describe(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "Tijori backup"
        return "${where(uri)} · $name"
    }

    private fun write(uri: Uri, bytes: ByteArray) {
        val cr = context.contentResolver
        // "wt" truncates; some cloud providers only accept "w", which our reader tolerates (see JsonText).
        val out = runCatching { cr.openOutputStream(uri, "wt") }.getOrNull() ?: cr.openOutputStream(uri, "w")
            ?: error("Couldn't open the backup file")
        out.use { it.write(bytes); it.flush() }
    }

    /** Reads the file back and parses it, so "backed up" is only shown when it really is. */
    private fun verify(uri: Uri, written: ByteArray) {
        val back = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read the backup back")
        require(back.size >= written.size) { "The saved backup is incomplete" }
        require(BackupFile.read(back).rows.size == BackupFile.read(written).rows.size) { "The saved backup is incomplete" }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is SecurityException -> "Tijori lost access to the backup file. Choose it again."
        is java.io.FileNotFoundException -> "The backup file was moved or deleted. Choose a new one."
        else -> e.message?.takeIf { it.length < 120 } ?: "Couldn't update the backup. Tijori will try again."
    }

    companion object {
        const val DEBOUNCE_MS = 4_000L
        const val FILE_NAME = "Tijori-backup.tijori"

        fun isGoogleDrive(uri: Uri): Boolean = uri.authority?.startsWith("com.google.android.apps.docs") == true

        fun where(uri: Uri): String = when {
            isGoogleDrive(uri) -> "Google Drive"
            uri.authority == "com.android.externalstorage.documents" -> "This phone"
            uri.authority == "com.android.providers.downloads.documents" -> "Downloads"
            uri.authority?.contains("onedrive", ignoreCase = true) == true -> "OneDrive"
            uri.authority?.contains("dropbox", ignoreCase = true) == true -> "Dropbox"
            else -> "Your folder"
        }
    }
}
