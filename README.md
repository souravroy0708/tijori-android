# Tijori (तिजोरी) 2.0 — offline password, banking & family vault

> *Chaabi sirf aapke paas* — the keys stay only with you.

Tijori is a native Android vault for Indian families. No account, no server, no sync, and the app
ships **without the INTERNET permission**, so Android itself blocks it from the network.

## What 2.0 changes (and why)

The 1.x prototype had security bugs that made it unsafe to publish. 2.0 is a rewrite of the vault engine:

| 1.x problem | 2.0 fix |
|---|---|
| "Argon2id" was a home-made function, not Argon2 | Real Argon2id (Bouncy Castle), checked against the RFC 9106 test vector |
| Passphrase list had 100 words (~40 bits for 6 words) | Full EFF 7,776-word list (~77.5 bits for 6 words) |
| Secret Key stored in plain SharedPreferences, with a hard-coded fallback | Secret Key wrapped by an Android Keystore key (StrongBox/TEE) |
| Fingerprint unlock used a dummy passphrase and couldn't work after locking | RSA-OAEP Keystore key, per-use Class 3 biometric, crypto-bound BiometricPrompt, invalidated on new enrolment |
| Nominee cards were made from a dummy key | Cards split the real Family key; nominees open a real Family package |
| Money-room lock was cosmetic (key re-derivable in memory) | Three independent random room keys; Money key only enters memory after fresh auth and is wiped on close |
| Titles stored in plain text; demo data with personal details seeded | Whole item (title, fields, notes) is one AES-256-GCM blob; no demo data |
| Autofill matched by title substring | Exact package match, or exact domain/subdomain from a trusted-browser list |

**Old vaults are not migrated.** Their key derivation was broken, so 2.0 removes the prototype's
database and settings when you create or restore a vault.

## Key design

```
passphrase ──Argon2id(64 MiB, t=2)──┐
                                    ├─HKDF-SHA256──► Account Unlock Key (AUK)
Secret Key (128-bit, Emergency Kit)─┘                       │ wraps (AES-GCM)
                                  ┌─────────────────────────┼──────────────────┐
                           Everyday key              Money key            Family key
                         (opens on unlock)    (fresh auth each time,   (also split into
                                               auto-closes when idle)   k-of-n nominee cards)
```

* Each item is sealed with its room key; associated data binds `vaultId/itemId/room`, so rows can't be swapped or moved.
* Fingerprint: the Keystore RSA public key seals (Everyday + Family) and (Money) separately; every decrypt needs its own biometric.
* Brute-force throttling (persisted): 5 wrong tries → 30 s, then 1 min, 5 min, 15 min.
* `FLAG_SECURE` on by default, clipboard copies marked sensitive and auto-cleared, `allowBackup=false`, cloud and device-transfer backup excluded.

## Features

* **Three rooms**: Everyday, Money, Family (Legacy). India-first templates: net banking, UPI (PIN *hint* only), cards (last 4 + PIN hint), PPF/EPF/NPS, income-tax portal, Aadhaar/PAN/passport, insurance, locker & will, letter to family, Wi-Fi.
* **Security check**: weak (pattern-aware: `Name@123`, years, phone numbers), reused, old banking passwords, expiring cards/documents, score ring.
* **2-step codes (TOTP)** built in, from a setup key or `otpauth://` link.
* **Generator**: EFF words, random characters, PIN.
* **Autofill**: fill + save prompts, Money logins need a fresh check, phishing warnings for unlinked apps/sites.
* **Backup / restore**: `.tijori` file (already-encrypted rows + header). Restore needs file + Secret Key + passphrase.
* **Family**: k-of-n nominee cards (2/3, 3/5, 2/2) as bilingual PDFs, encrypted `.tijori-family` package, practice drill, "cancel all cards" (rotates the Family key).
* **Trust page**: live checks for no INTERNET, backup flag, Keystore level, screen lock, debuggable, signing-certificate SHA-256, local activity log.
* English / हिन्दी, light / dark / system theme. Design language shared with Bas 1 Minute.

## Project layout

```
app/src/main/java/com/tijoriapp/vault/
├── core/crypto/   Pure Kotlin, unit-tested: Argon2, Aead, Hkdf, Base32, SecretKey, Shamir + NomineeCard, Passphrase, Strength, Totp
├── core/vault/    Models, Templates, Codec, VaultCrypto (key hierarchy), Files (backup + family package), Health
├── data/          Prefs, VaultStore (SQLite, secure_delete), VaultSession (state machine, in-memory keys), PdfKits
├── security/      DeviceKeys (Keystore), Clipboard
├── autofill/      FormParser, TijoriAutofillService, AutofillActivity
├── ui/            Theme, components, Nav, I18n, screens/*
├── TijoriApp.kt   Application + manual DI + process auto-lock
└── MainActivity.kt
app/src/main/assets/eff_large_wordlist.txt   (EFF, CC BY 3.0)
app/src/test/…     CoreCryptoTest, VaultTest
```

## Build

```bash
./gradlew testDebugUnitTest   # RFC vectors + vault tests
./gradlew assembleDebug       # installs as com.tijoriapp.vault.debug, next to the Play build
bash scripts/setup-signing.sh && bash scripts/get-play-bundle.sh
```

CI (`.github/workflows/build-apk.yml`) runs the tests, builds signed APK/AAB, and **fails if
`android.permission.INTERNET` appears in the merged release manifest**.

## Before the first Play release

1. Install a debug build on 2–3 real phones (one low-RAM, one Samsung) and time unlock. If Argon2 takes > 2 s, lower `DEFAULT_KDF` iterations in `VaultCrypto` (stored per vault, so it can change later).
2. Test autofill in Chrome (Settings → Autofill services → "Autofill using another service"), Firefox and 3–4 Indian banking apps.
3. Publish your signing-certificate SHA-256 (shown on the Trust page) on your site so users can verify their copy.
4. See `PLAY-STORE.md` for Data safety answers and listing text.
