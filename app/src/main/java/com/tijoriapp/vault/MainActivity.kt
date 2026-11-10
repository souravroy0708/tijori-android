package com.tijoriapp.vault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.view.autofill.AutofillManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tijoriapp.vault.ui.Host
import com.tijoriapp.vault.ui.Toasts
import com.tijoriapp.vault.ui.screens.AppRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.crypto.Cipher

class MainActivity : FragmentActivity(), Host {
    private val app get() = application as TijoriApp

    private var pendingWrite: (suspend (OutputStream) -> Unit)? = null
    private var pendingWriteDone: ((Boolean) -> Unit)? = null
    private var pendingRead: ((ByteArray?) -> Unit)? = null

    private val createBinary = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { onCreated(it) }
    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { onCreated(it) }
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = pendingRead; pendingRead = null
        if (uri == null) { cb?.invoke(null); return@registerForActivityResult }
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytesCapped(MAX_FILE) } }.getOrNull()
            }
            cb?.invoke(bytes)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Block screenshots, screen recording and the recent-apps thumbnail (on by default).
        applySecureFlag(app.prefs.now.blockScreenshots)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.prefs.settings.collect { applySecureFlag(it.blockScreenshots) }
            }
        }
        setContent { AppRoot(app, this) }
    }

    private fun applySecureFlag(on: Boolean) {
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!on)
    }

    // ------------------------------------------------------------------ Host

    override val biometricAvailable: Boolean
        get() = BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    override val autofillEnabled: Boolean
        get() = runCatching { getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true }.getOrDefault(false)

    override fun biometric(title: String, subtitle: String, cipher: Cipher, onSuccess: (Cipher) -> Unit, onFail: (String?) -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c != null) onSuccess(c) else onFail("Fingerprint check didn't return a key. Use your passphrase.")
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val silent = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onFail(if (silent) null else errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Use passphrase")
            .setConfirmationRequired(false)
            .build()
        runCatching { prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) }
            .onFailure { onFail("Fingerprint isn't available right now.") }
    }

    override fun createFile(fileName: String, mime: String, write: suspend (OutputStream) -> Unit, onDone: (Boolean) -> Unit) {
        pendingWrite = write
        pendingWriteDone = onDone
        app.session.expectExternalScreen()
        if (mime == "application/pdf") createPdf.launch(fileName) else createBinary.launch(fileName)
    }

    private fun onCreated(uri: Uri?) {
        val write = pendingWrite; val done = pendingWriteDone
        pendingWrite = null; pendingWriteDone = null
        if (uri == null || write == null) { done?.invoke(false); return }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri, "wt")?.use { write(it); true } ?: false }.getOrDefault(false)
            }
            done?.invoke(ok)
            if (!ok) Toasts.show("Couldn't save the file. Try another folder.")
        }
    }

    override fun openFile(mimes: Array<String>, onRead: (ByteArray?) -> Unit) {
        pendingRead = onRead
        app.session.expectExternalScreen()
        openDoc.launch(mimes)
    }

    override fun openAutofillSettings() {
        app.session.expectExternalScreen()
        val i = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(i) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    override fun openUrl(url: String) {
        val full = if (url.contains("://")) url else "https://$url"
        val uri = Uri.parse(full)
        if (uri.scheme != "https" && uri.scheme != "http") return
        app.session.expectExternalScreen()
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    companion object {
        private const val MAX_FILE = 64L * 1024 * 1024
    }
}

private fun java.io.InputStream.readBytesCapped(max: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        require(total <= max) { "File too large" }
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
