package com.tijoriapp.vault.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Native Android PdfDocument generator for:
 * 1. The Emergency Kit (Spec Page 5 & 7: Secret Key, Vault ID, Master Passphrase reminder, instructions).
 * 2. Nominee Kit Share Cards (Spec Page 7: k-of-n Shamir shares on individual printable cards).
 */
object EmergencyKitExporter {

    fun writeEmergencyKitPdf(
        outputStream: OutputStream,
        vaultId: String,
        secretKey128: String,
        ownerName: String,
    ) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = doc.startPage(pageInfo)
        val c: Canvas = page.canvas

        val navy = Color.parseColor("#0B1320")
        val gold = Color.parseColor("#B45309")
        val muted = Color.parseColor("#475569")

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 22f
            color = navy
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 11f
            color = muted
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
            color = gold
        }
        val monoBox = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 16f
            color = navy
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 10.5f
            color = navy
        }

        var y = 52f
        c.drawText("TIJORI — EMERGENCY KIT", 40f, y, titlePaint)
        y += 18f
        c.drawText("Chaabi sirf aapke paas · Keep this printed document in a physical bank locker or safe", 40f, y, subPaint)
        y += 26f

        // Border frame
        c.drawRect(40f, y, 555f, y + 160f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = gold
        })

        var boxY = y + 24f
        c.drawText("YOUR 128-BIT SECRET KEY (KEEP PRIVATE)", 56f, boxY, sectionPaint)
        boxY += 26f
        c.drawText(secretKey128, 56f, boxY, monoBox)
        boxY += 22f
        c.drawText("Vault ID: $vaultId   |   Owner: $ownerName", 56f, boxY, bodyPaint)
        boxY += 18f
        c.drawText("Generated on: ${LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))}", 56f, boxY, subPaint)
        boxY += 22f
        c.drawText("A stolen vault file CANNOT be cracked without this Secret Key and your Master Passphrase.", 56f, boxY, bodyPaint)

        y += 184f

        c.drawText("WHAT IS THIS DOCUMENT?", 40f, y, sectionPaint)
        y += 18f
        c.drawText("Tijori is a 100% on-device encrypted vault that never connects to the internet or any company server.", 40f, y, bodyPaint)
        y += 15f
        c.drawText("If your phone is damaged, lost, or you buy a new device, you will need two things to restore your vault:", 40f, y, bodyPaint)
        y += 15f
        c.drawText("  1. Your encrypted backup file (.tijori) from your pen drive, hard drive, or private cloud.", 40f, y, bodyPaint)
        y += 15f
        c.drawText("  2. This 128-bit Secret Key plus your Master Passphrase (remembered by you).", 40f, y, bodyPaint)
        y += 28f

        c.drawText("SECURITY WARNING", 40f, y, sectionPaint)
        y += 18f
        c.drawText("• Never upload this printed page or photograph it with unencrypted cloud photo backup.", 40f, y, bodyPaint)
        y += 15f
        c.drawText("• Do not email this Secret Key to yourself.", 40f, y, bodyPaint)
        y += 15f
        c.drawText("• For family inheritance, use the Nominee Kit shares instead of handing over this master key.", 40f, y, bodyPaint)
        y += 35f

        // Signature / Verification Block
        c.drawLine(40f, y, 260f, y, Paint().apply { strokeWidth = 1f; color = muted })
        y += 16f
        c.drawText("Owner Signature / Verification Date", 40f, y, subPaint)

        c.drawText("Generated locally on your phone by Tijori Password Vault (No INTERNET permission).", 40f, 810f, subPaint)

        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }

    fun writeNomineeSharesPdf(
        outputStream: OutputStream,
        vaultId: String,
        k: Int,
        n: Int,
        shares: List<com.tijoriapp.vault.crypto.ShamirSecretSharing.Share>,
    ) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val c: Canvas = page.canvas

        val navy = Color.parseColor("#0B1320")
        val gold = Color.parseColor("#B45309")
        val muted = Color.parseColor("#475569")

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f
            color = navy
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 10.5f
            color = muted
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
            color = gold
        }
        val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 10f
            color = navy
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 9.5f
            color = navy
        }

        var y = 46f
        c.drawText("TIJORI — NOMINEE KIT SHARE CARDS", 40f, y, titlePaint)
        y += 16f
        c.drawText("Cut along the dashed lines. Hand individual cards to family members, sibling, lawyer or CA.", 40f, y, subPaint)
        y += 14f
        c.drawText("Rule: Any $k of these $n shares combined with the encrypted vault file will unlock ONLY the Legacy Room.", 40f, y, headerPaint)
        y += 24f

        val cardHeight = 210f
        shares.forEachIndexed { idx, share ->
            val topY = y + idx * (cardHeight + 20f)
            if (topY + cardHeight < 800f) {
                // Card border
                c.drawRoundRect(RectF(40f, topY, 555f, topY + cardHeight), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.2f
                    color = gold
                })

                var cy = topY + 22f
                c.drawText("NOMINEE SHARE CARD #${share.index} OF $n (THRESHOLD: ANY $k RECOVER)", 54f, cy, headerPaint)
                cy += 16f
                c.drawText("Vault ID: $vaultId   |   Cardholder: ________________________", 54f, cy, bodyPaint)
                cy += 20f
                c.drawText("SHARE RECOVERY TOKEN:", 54f, cy, subPaint)
                cy += 15f
                val token = share.encodeWords()
                c.drawText(token.take(64), 54f, cy, monoPaint)
                if (token.length > 64) {
                    cy += 14f
                    c.drawText(token.substring(64), 54f, cy, monoPaint)
                }
                cy += 22f
                c.drawText("INSTRUCTIONS FOR HEIR / NOMINEE:", 54f, cy, headerPaint)
                cy += 14f
                c.drawText("1. Install Tijori Password Vault on any Android phone (Free, zero internet access).", 54f, cy, bodyPaint)
                cy += 13f
                c.drawText("2. Open 'Nominee Kit -> Rebuild Legacy Room' and enter any $k share tokens + encrypted backup file.", 54f, cy, bodyPaint)
                cy += 13f
                c.drawText("3. This reveals only the Legacy Room (insurance, locker, instructions). Everyday logins stay locked.", 54f, cy, bodyPaint)
            }
        }

        c.drawText("Tijori Nominee Kit — Open Source Shamir Secret Sharing over GF(256).", 40f, 810f, subPaint)

        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }
}
