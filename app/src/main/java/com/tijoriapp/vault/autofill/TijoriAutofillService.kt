package com.tijoriapp.vault.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import com.tijoriapp.vault.R

/**
 * Autofill without ever reading the vault in the background.
 * Every fill goes through [AutofillActivity]: the user unlocks Tijori and picks the login. Matching is exact
 * (same app package, or same website from a trusted browser), which defeats look-alike phishing apps and sites.
 */
class TijoriAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val form = runCatching { FormParser.parse(structure, packageName) }.getOrNull() ?: return callback.onSuccess(null)
        if (form.password == null && form.username == null) return callback.onSuccess(null)

        val intent = AutofillActivity.fillIntent(this, form)
        val sender = PendingIntent.getActivity(
            this, form.packageName.hashCode() xor System.nanoTime().toInt(), intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ).intentSender
        val where = form.webDomain ?: appLabel(form.packageName)
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, "Fill with Tijori")
            setTextViewText(R.id.autofill_subtitle, "Unlock to choose a login for $where")
        }
        val builder = FillResponse.Builder()
        @Suppress("DEPRECATION")
        builder.setAuthentication(form.ids.toTypedArray(), sender, presentation)
        if (form.password != null) {
            val type = SaveInfo.SAVE_DATA_TYPE_PASSWORD or (if (form.username != null) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0)
            builder.setSaveInfo(
                SaveInfo.Builder(type, arrayOf(form.password))
                    .apply { form.username?.let { setOptionalIds(arrayOf(it)) } }
                    .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                    .build()
            )
        }
        callback.onSuccess(builder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onFailure("Nothing to save")
        val form = FormParser.parse(structure, packageName)
        val password = form?.passwordValue
        if (form == null || password.isNullOrEmpty()) return callback.onFailure("No password found")
        val intent = AutofillActivity.saveIntent(this, form, form.usernameValue.orEmpty(), password)
        val pi = PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Android 9+: hand the user to Tijori to unlock and confirm the save.
        callback.onSuccess(pi.intentSender)
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
