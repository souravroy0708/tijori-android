package com.tijoriapp.vault

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tijoriapp.vault.core.crypto.Passphrase
import com.tijoriapp.vault.data.Prefs
import com.tijoriapp.vault.data.VaultSession
import com.tijoriapp.vault.data.VaultStore
import com.tijoriapp.vault.security.Clipboard
import com.tijoriapp.vault.security.CrashLog
import com.tijoriapp.vault.security.DeviceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph: small app, no DI framework needed. One instance per process. */
class TijoriApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs by lazy { Prefs(this) }
    val store by lazy { VaultStore(this) }
    val deviceKeys by lazy { DeviceKeys(this) }
    val session by lazy { VaultSession(this, store, deviceKeys, prefs, scope) }
    val clipboard by lazy { Clipboard(this, scope) }
    val words by lazy { Passphrase(assets.open("eff_large_wordlist.txt").bufferedReader().readLines().filter { it.isNotBlank() }) }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this) // first, so even startup crashes are recorded
        instance = this
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = session.onAppForeground()
            override fun onStop(owner: LifecycleOwner) = session.onAppBackground()
        })
    }

    fun copy(label: String, value: String, sensitive: Boolean) =
        clipboard.copy(label, value, sensitive, prefs.now.clipboardSeconds)

    companion object {
        lateinit var instance: TijoriApp
            private set
    }
}
