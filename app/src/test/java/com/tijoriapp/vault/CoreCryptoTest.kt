package com.tijoriapp.vault

import com.tijoriapp.vault.core.crypto.Aead
import com.tijoriapp.vault.core.crypto.Argon2
import com.tijoriapp.vault.core.crypto.Bytes
import com.tijoriapp.vault.core.crypto.Hkdf
import com.tijoriapp.vault.core.crypto.NomineeCard
import com.tijoriapp.vault.core.crypto.Passphrase
import com.tijoriapp.vault.core.crypto.SecretKey
import com.tijoriapp.vault.core.crypto.Shamir
import com.tijoriapp.vault.core.crypto.Strength
import com.tijoriapp.vault.core.crypto.Totp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Known-answer tests from the RFCs, so we know the primitives are the real thing. */
class CoreCryptoTest {

    @Test fun argon2id_rfc9106_vector() {
        val out = Argon2.derive(
            password = ByteArray(32) { 1 }, salt = ByteArray(16) { 2 },
            params = Argon2.Params(memoryKiB = 32, iterations = 3, parallelism = 4),
            outLength = 32, secret = ByteArray(8) { 3 }, additional = ByteArray(12) { 4 },
        )
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", Bytes.hex(out))
    }

    @Test fun hkdf_rfc5869_case1() {
        val prk = Hkdf.extract(Bytes.unhex("000102030405060708090a0b0c"), ByteArray(22) { 0x0b })
        assertEquals("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", Bytes.hex(prk))
        val okm = Hkdf.expand(prk, Bytes.unhex("f0f1f2f3f4f5f6f7f8f9"), 42)
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", Bytes.hex(okm))
    }

    @Test fun totp_rfc6238_sha1() {
        val t = Totp("12345678901234567890".toByteArray(), digits = 8)
        assertEquals("94287082", t.codeAt(59_000L))
        assertEquals("07081804", t.codeAt(1_111_111_109_000L))
        assertEquals("65353130", t.codeAt(20_000_000_000_000L))
    }

    @Test fun totp_parses_otpauth_uri() {
        val t = Totp.parse("otpauth://totp/Google:sourav%40gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google")
        assertNotNull(t)
        assertEquals("Google", t!!.issuer)
        assertEquals("sourav@gmail.com", t.account)
        assertEquals(6, t.codeAt(System.currentTimeMillis()).length)
        assertNull(Totp.parse("not a secret!"))
    }

    @Test fun aead_rejects_wrong_aad() {
        val key = Bytes.random(32)
        val sealed = Aead.seal(key, "hello".toByteArray(), "a".toByteArray())
        assertEquals("hello", String(Aead.open(key, sealed, "a".toByteArray())))
        try { Aead.open(key, sealed, "b".toByteArray()); fail("AAD swap must fail") } catch (_: javax.crypto.AEADBadTagException) {}
    }

    @Test fun shamir_every_k_subset_rebuilds_and_k_minus_1_does_not() {
        val secret = Bytes.random(32)
        val shares = Shamir.split(secret, 3, 5)
        for (a in 0 until 5) for (b in a + 1 until 5) for (c in b + 1 until 5) {
            assertArrayEquals(secret, Shamir.combine(listOf(shares[a], shares[b], shares[c])))
        }
        assertFalse(secret.contentEquals(Shamir.combine(listOf(shares[0], shares[1]))))
    }

    @Test fun nominee_card_code_round_trip_and_typo_detection() {
        val secret = Bytes.random(32)
        val cards = NomineeCard.makeSet(secret, 2, 3)
        val parsed = cards.map { (NomineeCard.parse(it.code()) as NomineeCard.Companion.Parsed.Ok).card }
        assertArrayEquals(secret, Shamir.combine(listOf(parsed[2].share, parsed[0].share)))
        assertEquals(3, parsed[0].n)
        val code = cards[0].code()
        val i = code.length - 5
        val typo = code.substring(0, i) + (if (code[i] == 'A') 'B' else 'A') + code.substring(i + 1)
        assertTrue(NomineeCard.parse(typo) !is NomineeCard.Companion.Parsed.Ok)
        // Lower case and spaces are fine when typing
        assertTrue(NomineeCard.parse(code.lowercase().replace("-", " ")) is NomineeCard.Companion.Parsed.Ok)
    }

    @Test fun secret_key_format_and_checksum() {
        val k = SecretKey.generate()
        val text = SecretKey.format(k)
        assertTrue(text.startsWith("TJ-"))
        assertArrayEquals(k, SecretKey.parse(text))
        assertArrayEquals(k, SecretKey.parse(text.lowercase().replace("-", " ")))
        val broken = text.replaceRange(5, 6, if (text[5] == '7') "8" else "7")
        assertNull(SecretKey.parse(broken))
    }

    @Test fun passphrase_generator_uses_full_eff_list() {
        val p = Passphrase(File("src/main/assets/eff_large_wordlist.txt").readLines().filter { it.isNotBlank() })
        assertEquals(12, p.wordsEntropy(1, false))
        val phrase = p.words(6)
        assertEquals(6, phrase.split("-").size)
        assertTrue(p.wordsEntropy(6, false) >= 77)
        assertTrue(Strength.check(phrase, p).level >= Strength.Level.Strong)
    }

    @Test fun strength_flags_common_indian_patterns() {
        assertTrue(Strength.check("Sourav@123").level <= Strength.Level.Weak)
        assertTrue(Strength.check("password123").level == Strength.Level.VeryWeak)
        assertTrue(Strength.check("9830012345").level <= Strength.Level.Weak)
        assertTrue(Strength.check(Passphrase.random(20)).level >= Strength.Level.Strong)
    }
}
