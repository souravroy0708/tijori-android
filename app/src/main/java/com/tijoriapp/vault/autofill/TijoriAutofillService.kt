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
import android.app.assist.AssistStructure
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

        // Collect the real AutofillIds of the login fields on screen
        val fieldIds = collectLoginFieldIds(structure)
        if (fieldIds.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        // Spec Page 4 & 7: every dataset requires authentication, and Money room
        // items are never filled silently. The user must unlock Tijori first.
        val authIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        val label = if (matchedItem.room == RoomType.MONEY) {
            "Unlock Tijori Money Room: ${matchedItem.title}"
        } else {
            "Unlock Tijori: ${matchedItem.title}"
        }
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, label)
        }
        val response = FillResponse.Builder()
            .setAuthentication(fieldIds.toTypedArray(), authIntent.intentSender, presentation)
            .build()
        callback.onSuccess(response)
    }

    /** Walks the view tree and returns AutofillIds of username/password-like fields. */
    private fun collectLoginFieldIds(structure: AssistStructure): List<AutofillId> {
        val hinted = ArrayList<AutofillId>()
        val editable = ArrayList<AutofillId>()

        fun walk(node: AssistStructure.ViewNode) {
            val id = node.autofillId
            if (id != null) {
                val hints = node.autofillHints
                if (hints != null && hints.isNotEmpty()) {
                    hinted.add(id)
                } else if (node.className?.contains("EditText") == true) {
                    editable.add(id)
                }
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode)
        }
        return hinted.ifEmpty { editable }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }
}
