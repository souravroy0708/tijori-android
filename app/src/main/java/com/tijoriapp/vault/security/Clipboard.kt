package com.tijoriapp.vault.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies secrets marked sensitive (Android 13+ hides them from the clipboard preview and keyboard
 * suggestions) and clears them after the chosen time.
 */
class Clipboard(private val context: Context, private val scope: CoroutineScope) {
    private var clearJob: Job? = null
    private var lastCopied: String? = null

    fun copy(label: String, value: String, sensitive: Boolean, clearAfterSeconds: Int) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, value)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                else putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)
        lastCopied = value
        clearJob?.cancel()
        if (sensitive && clearAfterSeconds > 0) clearJob = scope.launch {
            delay(clearAfterSeconds * 1000L)
            clearIfOurs()
        }
    }

    fun clearIfOurs() {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        // In the background Android 10+ won't let us read the clipboard; then we clear it anyway,
        // because the user asked us to and our copy is the most likely content.
        val current = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (current == null || current == lastCopied) cm.clearPrimaryClip()
        lastCopied = null
    }
}
