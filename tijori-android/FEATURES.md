# Tijori Features & Specification Mapping Matrix

This document maps each requirement from the 14-page **Password Vault — Product & Build Spec** to its concrete implementation in the Tijori codebase.

---

## 1. Zero Network Access (Spec Page 1 & 10)
- **Requirement**: Physically no `android.permission.INTERNET`. Not just unused, but stripped so the OS kernel refuses network sockets.
- **Implementation**:
  - `app/src/main/AndroidManifest.xml`: `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`.
  - `.github/workflows/build-apk.yml`: Merged manifest grep CI gate that fails the build if `android.permission.INTERNET` appears anywhere in merged release artifacts.
  - `TrustPageScreen.kt`: Runtime `packageManager.getPackageInfo()` query verifying live permission absence.
  - `res/xml/data_extraction_rules.xml`: Disables cloud backups (`<exclude domain="sharedpref" path="."/>`, `<exclude domain="database" path="."/>`).

---

## 2. Three Keys Cryptography Model (Spec Page 5 & 6)
- **Key 1: Master Passphrase**:
  - 6-word Diceware generated from a curated 7,776-word dictionary (`PassphraseGenerator.kt`).
  - Delivers ~77.5 bits of cryptographic entropy.
  - Salted with 16 bytes of OS `SecureRandom` and hashed using **Argon2id** (32 MiB RAM, 3 iterations, 1 lane).
- **Key 2: 128-bit Secret Key**:
  - 16 bytes represented as 4 blocks of 4 hex characters (`XXXX-XXXX-XXXX-XXXX`).
  - Printed on the physical **Emergency Kit**.
  - Combined with the Argon2id passphrase key via **HKDF-SHA256** to create the Master Unlock Key.
- **Key 3: Device Key**:
  - Generated inside Android Keystore (`KeystoreManager.kt`) using `AES/GCM/NoPadding`.
  - Supports `PURPOSE_ENCRYPT` and `PURPOSE_DECRYPT`.
  - Authenticated via `BiometricPrompt.CryptoObject`.
  - Live inspection of hardware backing level: **StrongBox Keymaster** or **TEE (Trusted Execution Environment)**.

---

## 3. Three Rooms Model & Memory Hygiene (Spec Page 3 & 6)
- **Everyday Room**:
  - For frequent logins (Gmail, Swiggy, Netflix, Wi-Fi).
  - Unlocked by Master Passphrase or Biometric quick unlock.
- **Money Room**:
  - For high-stakes assets (HDFC NetBanking, SBI PPF, EPF/UAN, Income Tax e-filing, Credit Cards).
  - Explicit session unlock required.
  - **60-Second Idle Timeout**: `AppController.kt` runs a reactive countdown timer. If no user interaction occurs for 60s, `moneyRoomKey.fill(0)` immediately wipes the 256-bit AES key from RAM.
  - **Card PIN Policy**: Explicitly defaults to a **PIN Hint** rather than storing raw 4-digit ATM or UPI PINs.
- **Legacy Room**:
  - For life policies (LIC), family locker keys, Demat accounts, and wills.
  - Splitting the Legacy room key using **Shamir's Secret Sharing over $GF(256)$**.
  - Accessible either by the owner or by heirs combining physical Nominee Share Cards.

---

## 4. Nominee Kit & Shamir's Secret Sharing (Spec Page 3, 5, 8 & 9)
- **Mathematics**:
  - Pure Kotlin implementation of Galois Field $GF(2^8)$ arithmetic with irreducible polynomial $x^8 + x^4 + x^3 + x + 1$ (0x11B).
  - Polynomial evaluation and Lagrange interpolation over $GF(256)$ (`ShamirSecretSharing.kt`).
  - Configurable $k$-of-$n$ threshold (default 2-of-3, supporting up to 3-of-5).
  - Any $k-1$ shares reveal zero bits of information.
- **Mnemonic Share Encoding**:
  - Encodes each share byte into word tokens for human readability and physical card printing.
- **Printable Share Cards PDF**:
  - Native Android `PdfDocument` generation in `EmergencyKitExporter.kt`.
  - Generates separate printable cards with token strings, QR code placeholder box, and bilingual instructions for family heirs.
- **On-Device Practice Drill**:
  - `NomineeKitScreen.kt` allows testing the Shamir reconstruction process on-device with simulated shares to build family confidence without risking actual vault keys.

---

## 5. Emergency Kit PDF Generation (Spec Page 5 & 10)
- Single-page A4 PDF generated via native Android `android.graphics.pdf.PdfDocument`.
- Contains:
  - Header with Vault ID and Owner name.
  - 128-bit Secret Key formatted in clear monospace blocks.
  - Diceware Passphrase writing prompt.
  - Safety instructions: *"Store this document in a sealed envelope in a bank locker or fireproof safe. Never photograph or upload to cloud storage."*
  - Exported through Android Storage Access Framework (SAF).

---

## 6. Live Trust & Self-Proofs Page (Spec Page 4, 8 & 10)
- **Zero INTERNET Proof**: Queries runtime `packageManager` and shows physical permission status with a direct button to Android Settings.
- **Hardware Isolation**: Displays Keystore provider name and whether keys are backed by StrongBox, TEE, or Software fallback.
- **Cryptographic Architecture Breakdown**: Explains Argon2id parameters, AES-256-GCM cipher, and AAD binding scheme (`vaultId:itemId:room:version`).
- **Screen Protection**: Shows `FLAG_SECURE` active status.
- **APK Signing Fingerprint**: Computes SHA-256 digest of active APK signing certificate for verification against GitHub releases.
- **Password Health Audit**: Evaluates entropy bits for each password, identifies weak passwords (< 50 bits) and reused passwords.
- **Last 20 Unlock Attempts**: Persistent SQLite audit log tracking every unlock attempt and room transition.

---

## 7. Android System Integration & Autofill (Spec Page 7 & 8)
- **AutofillService**:
  - Registered in `AndroidManifest.xml` with `android.permission.BIND_AUTOFILL_SERVICE`.
  - Implements `TijoriAutofillService.kt` with heuristic web domain and Android package ID matching.
  - Sensitive Money room credentials require in-line authorization before autofill release.
- **Clipboard Hygiene**:
  - Copies sensitive fields with `ClipData.newPlainText()` and `android.content.extra.IS_SENSITIVE = true` (Android 13+).
  - Dispatches a coroutine timer to overwrite clipboard with blank text after 30 seconds.
- **Bilingual Interface**:
  - Native English and Hindi (`हिन्दी`) translation engine (`I18n.kt`) covering all screens, templates, and buttons.
