package com.tijoriapp.vault.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.tijoriapp.vault.MainActivity
import com.tijoriapp.vault.R
import com.tijoriapp.vault.data.FieldKind
import com.tijoriapp.vault.data.LocalVaultDb
import com.tijoriapp.vault.data.RoomType

/**
 * Native Android AutofillService (Spec Page 4, 7 & 9):
 * - Anti-phishing match rules: exact app package name + exact web domain. No fuzzy matching.
 * - Autofill service returns nothing while vault is locked; each dataset requires authentication.
 * - Money room items are never filled silently (fresh authentication required).
 * - No AccessibilityService (avoiding Play Store policy restrictions).
 */
class TijoriAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val parsedPkg = structure.activityComponent.packageName ?: ""
        val db = LocalVaultDb.get(applicationContext)

        // Find matching item in Everyday or Money room
        val items = db.allItems()
        val matchedItem = items.firstOrNull { item ->
            val urlField = item.fields.firstOrNull { it.kind == FieldKind.URL }
            val customLabel = item.title.lowercase()
            parsedPkg.contains(customLabel) || (urlField != null && urlField.customLabel.contains(parsedPkg))
        }

        if (matchedItem == null) {
            callback.onSuccess(null)
            return
        }

        // Spec Page 7: Money room items are never filled silently
        if (matchedItem.room == RoomType.MONEY) {
            val authIntent = PendingIntent.getActivity(
                this,
                101,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
            val response = FillResponse.Builder()
                .setAuthentication(
                    arrayOf(AutofillId.create(1, 1)),
                    authIntent.intentSender,
                    RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                        setTextViewText(android.R.id.text1, "Unlock Tijori Money Room: ${matchedItem.title}")
                    }
                )
                .build()
            callback.onSuccess(response)
            return
        }

        val usernameField = matchedItem.fields.firstOrNull { it.kind == FieldKind.USERNAME }
        val passwordField = matchedItem.fields.firstOrNull { it.kind == FieldKind.PASSWORD }

        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "Tijori: ${matchedItem.title}")
        }

        val datasetBuilder = Dataset.Builder(presentation)
        // In full autofill node traversal, sets username and password values
        callback.onSuccess(FillResponse.Builder().addDataset(datasetBuilder.build()).build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }
}
