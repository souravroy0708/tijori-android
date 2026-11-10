package com.tijoriapp.vault.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.tijoriapp.vault.R
import com.tijoriapp.vault.core.crypto.NomineeCard
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Printable kits, drawn on the phone with PdfDocument. Nothing is uploaded anywhere. Bilingual English + हिन्दी. */
class PdfKits(private val context: Context) {
    private val ink = Color.parseColor("#0F1235")
    private val muted = Color.parseColor("#4A4F6A")
    private val haldi = Color.parseColor("#E0A800")
    private val line = Color.parseColor("#DCDFEC")

    private val body: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.mukta_regular) }.getOrNull() ?: Typeface.DEFAULT
    private val bold: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.mukta_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    private val display: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.anek_extrabold) }.getOrNull() ?: Typeface.DEFAULT_BOLD

    private fun paint(tf: Typeface, size: Float, color: Int = ink) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color }

    /** Draws wrapped text and returns the new y. */
    private fun Canvas.para(text: String, x: Float, y: Float, width: Float, p: TextPaint, spacing: Float = 1.15f): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).build()
        save(); translate(x, y); layout.draw(this); restore()
        return y + layout.height
    }

    private fun today() = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))

    fun emergencyKit(out: OutputStream, owner: String, secretKey: String, vaultId: String) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        val left = 44f
        val width = 595f - left * 2

        // Header band
        c.drawRect(0f, 0f, 595f, 118f, Paint().apply { color = ink })
        c.drawText("Tijori", left, 58f, paint(display, 30f, Color.WHITE))
        c.drawText("Emergency Kit  ·  इमरजेंसी किट", left, 84f, paint(bold, 14f, haldi))
        c.drawText("${owner.ifBlank { "Vault owner" }}  ·  ${today()}", left, 104f, paint(body, 10.5f, Color.parseColor("#BCC1E0")))

        var y = 148f
        y = c.para("This page and your passphrase are the only way to open your Tijori backup on a new phone. Tijori has no servers and no account recovery: nobody, including us, can reset it for you.",
            left, y, width, paint(body, 11f, muted)) + 18f

        // Secret Key box
        val box = RectF(left, y, left + width, y + 104f)
        c.drawRoundRect(box, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF6D6") })
        c.drawRoundRect(box, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = haldi })
        c.drawText("SECRET KEY  ·  सीक्रेट की", left + 18f, y + 28f, paint(bold, 11f, muted))
        c.drawText(secretKey, left + 18f, y + 64f, paint(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD), 19f))
        c.drawText("Vault ID ${vaultId.take(8).uppercase()}", left + 18f, y + 88f, paint(body, 10f, muted))
        y += 124f

        // Passphrase write-in
        c.drawText("MASTER PASSPHRASE (optional, write by hand)  ·  मास्टर पासफ़्रेज़", left, y, paint(bold, 11f, muted))
        y += 34f
        c.drawLine(left, y, left + width, y, Paint().apply { color = muted; strokeWidth = 1f })
        y += 14f
        y = c.para("Only write it here if this page will be locked away (home safe or bank locker). Otherwise keep it in your memory.",
            left, y, width, paint(body, 10f, muted)) + 20f

        c.drawText("To restore on a new phone", left, y, paint(bold, 13f)); y += 8f
        y = c.para("1.  Install Tijori from Google Play.\n2.  Tap \"Restore from backup\" and pick your .tijori backup file (pen drive, laptop or your own cloud folder).\n3.  Type the Secret Key above and your passphrase.",
            left, y + 6f, width, paint(body, 11f)) + 16f

        c.drawText("नए फ़ोन पर वापस लाने के लिए", left, y, paint(bold, 13f)); y += 8f
        y = c.para("1.  Google Play से Tijori इंस्टॉल करें।\n2.  \"Restore from backup\" दबाएँ और अपनी .tijori बैकअप फ़ाइल चुनें।\n3.  ऊपर लिखी सीक्रेट की और अपना पासफ़्रेज़ डालें।",
            left, y + 6f, width, paint(body, 11f)) + 16f

        c.drawText("Keep it safe", left, y, paint(bold, 13f)); y += 8f
        y = c.para("•  Print it. Don't photograph it, email it or keep it in cloud photos.\n•  Store it where you keep property papers.\n•  For family inheritance, use Nominee Cards instead of sharing this page.",
            left, y + 6f, width, paint(body, 11f))

        c.drawLine(left, 790f, left + width, 790f, Paint().apply { color = line })
        c.drawText("Made on this phone by Tijori. No internet permission, nothing uploaded.", left, 810f, paint(body, 9.5f, muted))
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    fun nomineeCards(out: OutputStream, owner: String, cards: List<NomineeCard>) {
        val doc = PdfDocument()
        val who = owner.ifBlank { "the vault owner" }
        cards.forEachIndexed { i, card ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            val c = page.canvas
            val left = 44f
            val width = 595f - left * 2
            c.drawRect(0f, 0f, 595f, 118f, Paint().apply { color = ink })
            c.drawText("Tijori Family Card", left, 58f, paint(display, 28f, Color.WHITE))
            c.drawText("Card ${card.share.index} of ${card.n}  ·  any ${card.k} cards open the Family room", left, 84f, paint(bold, 13f, Color.parseColor("#5ED6A0")))
            c.drawText("Set ${card.setLabel}  ·  made ${today()}", left, 104f, paint(body, 10.5f, Color.parseColor("#BCC1E0")))

            var y = 150f
            y = c.para("Hello ${"_".repeat(22)}. $who trusts you with this card. On its own it opens nothing. Together with ${card.k - 1} other card${if (card.k > 2) "s" else ""} and the Family package file, it lets the family read the insurance, locker and other information $who left for you.",
                left, y, width, paint(body, 11.5f)) + 20f

            val codeLines = card.code().chunked(28)
            val boxH = 40f + codeLines.size * 28f
            val box = RectF(left, y, left + width, y + boxH)
            c.drawRoundRect(box, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E9FBF2") })
            c.drawText("CARD CODE  ·  कार्ड कोड", left + 18f, y + 26f, paint(bold, 11f, muted))
            codeLines.forEachIndexed { li, l -> c.drawText(l, left + 18f, y + 56f + li * 28f, paint(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD), 18f)) }
            y += boxH + 26f

            c.drawText("When it's needed", left, y, paint(bold, 13f)); y += 8f
            y = c.para("1.  Install Tijori from Google Play on any Android phone.\n2.  Tap \"I'm a nominee\".\n3.  Choose the Family package file (.tijori-family). Ask who $who gave it to.\n4.  Type the codes from any ${card.k} cards. Small typing mistakes are caught for you.",
                left, y + 6f, width, paint(body, 11f)) + 16f

            c.drawText("ज़रूरत पड़ने पर", left, y, paint(bold, 13f)); y += 8f
            y = c.para("1.  किसी भी Android फ़ोन पर Google Play से Tijori इंस्टॉल करें।\n2.  \"I'm a nominee\" दबाएँ।\n3.  फ़ैमिली पैकेज फ़ाइल (.tijori-family) चुनें।\n4.  किन्हीं ${card.k} कार्डों के कोड डालें।",
                left, y + 6f, width, paint(body, 11f)) + 16f

            c.para("Keep this card in a safe place, separate from the other cards. Don't photograph it or share the code on WhatsApp.",
                left, y, width, paint(body, 10.5f, muted))
            c.drawLine(left, 790f, left + width, 790f, Paint().apply { color = line })
            c.drawText("Shamir's secret sharing: fewer than ${card.k} cards reveal nothing at all.", left, 810f, paint(body, 9.5f, muted))
            doc.finishPage(page)
        }
        doc.writeTo(out)
        doc.close()
    }
}
