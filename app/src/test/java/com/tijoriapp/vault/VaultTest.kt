package com.tijoriapp.vault

import com.tijoriapp.vault.core.crypto.Argon2
import com.tijoriapp.vault.core.crypto.NomineeCard
import com.tijoriapp.vault.core.crypto.SecretKey
import com.tijoriapp.vault.core.crypto.Shamir
import com.tijoriapp.vault.core.vault.BackupFile
import com.tijoriapp.vault.core.vault.Dates
import com.tijoriapp.vault.core.vault.Field
import com.tijoriapp.vault.core.vault.FieldKind
import com.tijoriapp.vault.core.vault.Health
import com.tijoriapp.vault.core.vault.ItemType
import com.tijoriapp.vault.core.vault.LegacyPackage
import com.tijoriapp.vault.core.vault.Room
import com.tijoriapp.vault.core.vault.SealedRow
import com.tijoriapp.vault.core.vault.Urls
import com.tijoriapp.vault.core.vault.VaultCrypto
import com.tijoriapp.vault.core.vault.VaultHeader
import com.tijoriapp.vault.core.vault.VaultItem
import com.tijoriapp.vault.core.vault.WrongPassphraseException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class VaultTest {
    private val fast = Argon2.Params(memoryKiB = 256, iterations = 1, parallelism = 1)
    private val pass = "lantern-quarry-velvet-mango-orbit-sundial"

    private fun item(room: Room, title: String, pw: String) = VaultItem(
        room = room, type = ItemType.LOGIN, title = title,
        fields = listOf(Field("Username", FieldKind.USERNAME, "sourav"), Field("Password", FieldKind.PASSWORD, pw)),
    )

    @Test fun create_unlock_and_wrong_passphrase() {
        val sk = SecretKey.generate()
        val created = VaultCrypto.create(pass, sk, fast)
        val keys = VaultCrypto.unlock(created.header, "  $pass ", sk, setOf(Room.EVERYDAY, Room.LEGACY))
        assertArrayEquals(created.keys[Room.EVERYDAY], keys[Room.EVERYDAY])
        assertFalse("Money key must stay sealed", keys.has(Room.MONEY))
        try { VaultCrypto.unlock(created.header, pass + "x", sk, setOf(Room.EVERYDAY)); fail() } catch (_: WrongPassphraseException) {}
        try { VaultCrypto.unlock(created.header, pass, SecretKey.generate(), setOf(Room.EVERYDAY)); fail() } catch (_: WrongPassphraseException) {}
    }

    @Test fun header_json_round_trip_and_passphrase_change() {
        val sk = SecretKey.generate()
        val c = VaultCrypto.create(pass, sk, fast)
        val h = VaultHeader.fromJson(c.header.toJson())
        val h2 = VaultCrypto.changePassphrase(h, pass, "new-pass-phrase-words-here-ok", sk)
        val keys = VaultCrypto.unlock(h2, "new-pass-phrase-words-here-ok", sk, Room.entries.toSet())
        assertArrayEquals(c.keys[Room.MONEY], keys[Room.MONEY])
        try { VaultCrypto.unlock(h2, pass, sk, setOf(Room.EVERYDAY)); fail() } catch (_: WrongPassphraseException) {}
    }

    @Test fun item_cannot_be_moved_between_rooms_on_disk() {
        val c = VaultCrypto.create(pass, SecretKey.generate(), fast)
        val it = item(Room.MONEY, "HDFC", "x")
        val sealed = VaultCrypto.seal(c.keys[Room.MONEY]!!, c.header.vaultId, it)
        assertEquals("HDFC", VaultCrypto.open(c.keys[Room.MONEY]!!, c.header.vaultId, it.id, Room.MONEY, sealed).title)
        try { VaultCrypto.open(c.keys[Room.MONEY]!!, c.header.vaultId, it.id, Room.EVERYDAY, sealed); fail() } catch (_: Exception) {}
    }

    @Test fun backup_round_trip() {
        val sk = SecretKey.generate()
        val c = VaultCrypto.create(pass, sk, fast)
        val items = listOf(item(Room.EVERYDAY, "Gmail", "a"), item(Room.MONEY, "SBI", "b"))
        val rows = items.map { SealedRow(it.id, it.room, VaultCrypto.seal(c.keys[it.room]!!, c.header.vaultId, it), 1L) }
        val parsed = BackupFile.read(BackupFile.write(c.header, rows))
        val keys = VaultCrypto.unlock(parsed.header, pass, sk, Room.entries.toSet())
        val back = parsed.rows.map { VaultCrypto.open(keys[it.room]!!, parsed.header.vaultId, it.id, it.room, it.sealed) }
        assertEquals(listOf("Gmail", "SBI"), back.map { it.title })
    }

    @Test fun nominee_opens_family_package_with_two_of_three_cards() {
        val c = VaultCrypto.create(pass, SecretKey.generate(), fast)
        val lic = VaultItem(room = Room.LEGACY, type = ItemType.INSURANCE, title = "LIC Jeevan Anand")
        val gmail = item(Room.EVERYDAY, "Gmail", "secret")
        val rows = listOf(
            SealedRow(lic.id, Room.LEGACY, VaultCrypto.seal(c.keys[Room.LEGACY]!!, c.header.vaultId, lic), 1),
            SealedRow(gmail.id, Room.EVERYDAY, VaultCrypto.seal(c.keys[Room.EVERYDAY]!!, c.header.vaultId, gmail), 1),
        )
        val pkg = LegacyPackage.read(LegacyPackage.write(c.header, "Sourav", 2, 3, rows))
        assertEquals("Everyday items never leave in the family package", 1, pkg.rows.size)
        val cards = NomineeCard.makeSet(c.keys[Room.LEGACY]!!, 2, 3)
        val rebuilt = Shamir.combine(listOf(cards[1].share, cards[2].share))
        assertEquals("LIC Jeevan Anand", LegacyPackage.open(pkg, rebuilt)!!.single().title)
        assertNull(LegacyPackage.open(pkg, ByteArray(32)))
    }

    @Test fun anti_phishing_host_match() {
        assertEquals("netbanking.hdfcbank.com", Urls.host("https://NetBanking.hdfcbank.com/login?x=1"))
        assertTrue(Urls.sameSite("hdfcbank.com", "netbanking.hdfcbank.com"))
        assertFalse(Urls.sameSite("hdfcbank.com", "hdfcbank.com.evil.in"))
        assertFalse(Urls.sameSite("hdfcbank.com", "myhdfcbank.com"))
        assertNull(Urls.host("swiggy://app"))
    }

    @Test fun dates_and_health() {
        assertEquals(LocalDate.of(2029, 8, 31), Dates.parse("08/29"))
        assertEquals(LocalDate.of(2027, 2, 28), Dates.parse("2027-02"))
        val a = item(Room.EVERYDAY, "A", "Sourav@123")
        val b = item(Room.EVERYDAY, "B", "Sourav@123")
        val card = VaultItem(room = Room.MONEY, type = ItemType.CARD, title = "Card", fields = listOf(Field("Expiry", FieldKind.EXPIRY, "2026-01")))
        val r = Health.analyze(listOf(a, b, card), today = LocalDate.of(2026, 1, 10))
        assertEquals(2, r.of(Health.Issue.Reused).size)
        assertEquals(2, r.of(Health.Issue.Weak).size)
        assertEquals(1, r.of(Health.Issue.Expiring).size)
        assertTrue(r.score < 40)
        assertNotNull(Health.analyze(emptyList()).score)
    }
}
