package com.tijoriapp.vault.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** App-wide snackbar messages. Never put secrets in here. */
object Toasts {
    val flow = MutableStateFlow<String?>(null)
    fun show(message: String) { flow.value = message }
}
