package com.tijoriapp.vault

import com.tijoriapp.vault.core.crypto.Argon2
import com.tijoriapp.vault.core.crypto.SecretKey
import com.tijoriapp.vault.core.vault.BackupFile
import com.tijoriapp.vault.core.vault.Codec
import com.tijoriapp.vault.core.vault.CsvImport
import com.tijoriapp.vault.core.vault.Field
import com.tijoriapp.vault.core.vault.FieldKind
import com.tijoriapp.vault.core.vault.ItemType
import com.tijoriapp.vault.core.vault.JsonText
import com.tijoriapp.vault.core.vault.PastPassword
import com.tijoriapp.vault.core.vault.Recovery
import com.tijoriapp.vault.core.vault.Room
import com.tijoriapp.vault.core.vault.SealedRow
import com.tijoriapp.vault.core.vault.VaultCrypto
import com.tijoriapp.vault.core.vault.VaultItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DriveBackupTest {
    private val fast = Argon2.Params(memoryKiB = 256, iterations = 1, parallelism = 1)
    private val pass = "lantern quarry velvet mango orbit"

    @Test fun easy_restore_needs_only_the_passphrase() {
        val sk = SecretKey.generate()
        val c = VaultCrypto.create(pass, sk, fast)
        val gmail = VaultItem(room = Room.EVERYDAY, type = ItemType.EMAIL, title = "Gmail",
            fields = listOf(Field("Email", FieldKind.EMAIL, "sourav@gmail.com"), Field("Password", FieldKind.PASSWORD, "x")))
        val rows = listOf(SealedRow(gmail.id, Room.EVERYDAY, VaultCrypto.seal(c.keys[Room.EVERYDAY]!!, c.header.vaultId, gmail), 1L))
        val env = Recovery.seal(pass, sk, c.header.vaultId, Recovery.kdfFor(fast))
        val parsed = BackupFile.read(BackupFile.write(c.header, rows, env, owner = "Sourav"))
        assertTrue(parsed.easyRestore)
        assertEquals("Sourav", parsed.owner)

        // Reinstall: no Emergency Kit, only the passphrase (spacing differences still work).
        val recovered = Recovery.open(parsed.recovery!!, "  lantern  quarry velvet mango orbit ", parsed.header.vaultId)!!
        assertArrayEquals(sk, recovered)
        val keys = VaultCrypto.unlock(parsed.header, pass, recovered, setOf(Room.EVERYDAY))
        assertEquals("Gmail", VaultCrypto.open(keys[Room.EVERYDAY]!!, parsed.header.vaultId, gmail.id, Room.EVERYDAY, parsed.rows[0].sealed).title)

        assertNull("Wrong passphrase must not open it", Recovery.open(parsed.recovery!!, "lantern quarry velvet mango", parsed.header.vaultId))
        assertNull("Envelope is bound to its vault", Recovery.open(parsed.recovery!!, pass, "another-vault"))
    }

    @Test fun backup_without_easy_restore_stays_two_secret() {
        val c = VaultCrypto.create(pass, SecretKey.generate(), fast)
        val parsed = BackupFile.read(BackupFile.write(c.header, emptyList()))
        assertFalse(parsed.easyRestore)
    }

    @Test fun overwritten_cloud_file_with_old_tail_still_reads() {
        val c = VaultCrypto.create(pass, SecretKey.generate(), fast)
        val fresh = String(BackupFile.write(c.header, emptyList()))
        val withTail = fresh + "\"}],\"exportedAt\":1}} leftover {\"a\":\"}\"}"
        assertEquals(c.header.vaultId, BackupFile.read(withTail.toByteArray()).header.vaultId)
        assertEquals("{\"a\":\"x}\\\"y\"}", JsonText.firstObject("junk {\"a\":\"x}\\\"y\"} more}"))
        assertNull(JsonText.firstObject("{\"a\":1"))
        try { BackupFile.read("hello".toByteArray()); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun password_history_survives_encoding() {
        val item = VaultItem(room = Room.EVERYDAY, type = ItemType.LOGIN, title = "Amazon",
            fields = listOf(Field("Password", FieldKind.PASSWORD, "new")), history = listOf(PastPassword("old", 42L)))
        val back = Codec.decode(item.id, item.room, Codec.encode(item))
        assertEquals(listOf(PastPassword("old", 42L)), back.history)
    }

    @Test fun imports_google_password_manager_csv() {
        val csv = "name,url,username,password,note\r\n" +
            "accounts.google.com,https://accounts.google.com/signin,sourav@gmail.com,\"pa,ss\"\"word\",\r\n" +
            "IRCTC,https://www.irctc.co.in/nget,sroy,Train#2026,\"line1\nline2\"\r\n" +
            ",android://AbC123==@in.amazon.mShop.android.shopping/,9876543210,amz-pass,\r\n" +
            "empty,https://x.com,,,\r\n" +
            "IRCTC again,https://www.irctc.co.in/nget,sroy,Train#2026,\r\n"
        val r = CsvImport.parse(csv)
        assertEquals(CsvImport.Source.Google, r.source)
        assertEquals(3, r.items.size)
        assertEquals(1, r.duplicates)
        assertEquals(1, r.skipped)
        val g = r.items[0]
        assertEquals(ItemType.EMAIL, g.type)
        assertEquals("pa,ss\"word", g.password)
        assertEquals("sourav@gmail.com", g.username)
        assertEquals("line1\nline2", r.items[1].notes)
        assertEquals(listOf("in.amazon.mShop.android.shopping"), r.items[2].apps)
        assertEquals("Shopping", r.items[2].title)

        // Importing the same file again adds nothing.
        assertEquals(0, CsvImport.parse(csv, existing = r.items).items.size)
    }

    @Test fun imports_bitwarden_csv_and_skips_cards() {
        val csv = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n" +
            ",1,login,Netflix,,,0,https://www.netflix.com/login,me@x.in,nf-pass,JBSWY3DPEHPK3PXP\n" +
            ",,card,My Visa,,,0,,,,\n"
        val r = CsvImport.parse(csv)
        assertEquals(CsvImport.Source.Bitwarden, r.source)
        assertEquals(1, r.items.size)
        assertEquals(1, r.skipped)
        assertTrue(r.items[0].favorite)
        assertEquals("JBSWY3DPEHPK3PXP", r.items[0].first(FieldKind.TOTP)?.value)
    }

    @Test fun rejects_files_that_are_not_exports() {
        try { CsvImport.parse("date,amount,merchant\n1,2,3\n"); fail() } catch (_: IllegalArgumentException) {}
    }
}
