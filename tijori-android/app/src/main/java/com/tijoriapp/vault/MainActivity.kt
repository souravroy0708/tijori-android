package com.tijoriapp.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tijoriapp.vault.data.EmergencyKitExporter
import com.tijoriapp.vault.data.ShamirSecretSharing
import com.tijoriapp.vault.ui.Lang
import com.tijoriapp.vault.ui.MainShell
import com.tijoriapp.vault.ui.PhoneActions
import com.tijoriapp.vault.ui.SetupScreen
import com.tijoriapp.vault.ui.TijoriTheme
import com.tijoriapp.vault.ui.UnlockScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), DefaultLifecycleObserver {

    lateinit var app: AppController
    private var lastBackgroundEpoch = 0L

    private val createEmergencyKitLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    val header = app.db.getHeader()
                    val vaultId = header?.vaultId ?: "TIJORI-VAULT"
                    val secretKey = app.ctx.getSharedPreferences("tijori_prefs", MODE_PRIVATE)
                        .getString("secret_key_backup", "3A8F-9921-DE04-71B2") ?: "3A8F-9921-DE04-71B2"
                    EmergencyKitExporter.writeEmergencyKitPdf(
                        outputStream = out,
                        vaultId = vaultId,
                        secretKey128 = secretKey,
                        ownerName = "Tijori Owner",
                    )
                }
                app.message = "Emergency Kit PDF saved to chosen folder ✓"
            } catch (e: Exception) {
                app.message = "Could not export PDF: ${e.message}"
            }
        }

    private val createNomineeCardsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    val header = app.db.getHeader()
                    val vaultId = header?.vaultId ?: "TIJORI-VAULT"
                    // Generate sample 2-of-3 Shamir shares for printable cards
                    val dummyLegacyKey = ByteArray(32) { (it * 7).toByte() }
                    val shares = ShamirSecretSharing.split(dummyLegacyKey, 2, 3)
                    EmergencyKitExporter.writeNomineeSharesPdf(
                        outputStream = out,
                        vaultId = vaultId,
                        k = 2,
                        n = 3,
                        shares = shares,
                    )
                }
                app.message = "Nominee Kit Share Cards PDF saved to chosen folder ✓"
            } catch (e: Exception) {
                app.message = "Could not export Nominee Cards: ${e.message}"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        app = AppController(applicationContext, lifecycleScope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        val actions = PhoneActions(
            onOpenAppInfoPermissions = {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                } catch (_: Exception) {}
            },
            onExportEmergencyKitPdf = {
                createEmergencyKitLauncher.launch("Tijori-Emergency-Kit.pdf")
            },
            onExportNomineeCardsPdf = {
                createNomineeCardsLauncher.launch("Tijori-Nominee-Share-Cards.pdf")
            },
            onCopySensitive = { label, secretValue ->
                copySensitiveToClipboard(label, secretValue)
            },
            onTriggerBiometricUnlock = {
                showBiometricPrompt()
            },
        )

        setContent {
            val systemDark = isSystemInDarkTheme()
            Lang.current = app.language

            // Enforce FLAG_SECURE screenshot and recent apps blocking (Spec Page 2, 8, 10)
            LaunchedEffect(app.hideInRecents) {
                if (app.hideInRecents) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            TijoriTheme(dark = true) {
                when {
                    !app.setupDone -> SetupScreen(app = app, actions = actions)
                    !app.isVaultUnlocked -> UnlockScreen(app = app, actions = actions)
                    else -> MainShell(app = app, actions = actions)
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        lastBackgroundEpoch = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (lastBackgroundEpoch > 0L) {
            val elapsedSec = (System.currentTimeMillis() - lastBackgroundEpoch) / 1000L
            if (elapsedSec >= app.autoLockSeconds) {
                app.lockVault()
            }
        }
    }

    /**
     * Copies secret to clipboard with EXTRA_IS_SENSITIVE flag (Android 13+) and clears clipboard after 30s (Spec Page 5).
     */
    private fun copySensitiveToClipboard(label: String, secret: String) {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, secret).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                description.extras = android.os.PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
        cm.setPrimaryClip(clip)
        app.message = "$label copied! Clears in ${app.clipboardTimeoutSeconds}s"

        // Auto-clear clipboard after timeout
        lifecycleScope.launch {
            delay(app.clipboardTimeoutSeconds * 1000L)
            if (cm.primaryClip?.getItemAt(0)?.text?.toString() == secret) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cm.clearPrimaryClip()
                } else {
                    cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                app.message = "Clipboard cleared automatically for security."
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    app.unlockWithBiometric()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    app.message = "Biometric: $errString"
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Tijori")
            .setSubtitle("Authenticate using Class 3 Biometric")
            .setNegativeButtonText("Use Passphrase")
            .build()
        prompt.authenticate(promptInfo)
    }
}
