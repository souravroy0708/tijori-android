# Tijori (तिजोरी) — Native Android Password, Banking & Document Vault
> *"Chaabi sirf aapke paas"* (The keys stay only with you)

Tijori is a 100% on-device, offline-first native Android password, banking, and document vault designed specifically for Indian families and privacy purists.

---

## 🛡️ Core Guarantees

1. **Zero Network Access (`android.permission.INTERNET` Physically Stripped)**:
   - Tijori has **no Internet permission**. The Android OS physically denies the app socket creation or network packet transmission.
   - Enforced by `tools:node="remove"` in the merged manifest and guarded by a strict CI gate in GitHub Actions.
   - Live verifiable in the app's **Trust & Proofs** tab and in Android Settings (`Settings -> Apps -> Tijori -> Permissions`).
2. **Three-Key Cryptographic Architecture**:
   - **Key 1 (Master Passphrase)**: 6-word Diceware (~77.5 bits entropy), remembered by you.
   - **Key 2 (128-bit Secret Key)**: Emergency Kit on paper (`XXXX-XXXX-XXXX-XXXX`), wrapped in Android Keystore.
   - **Key 3 (Device Key)**: Stored in Android Keystore with StrongBox/TEE hardware backing and BiometricPrompt CryptoObject authentication.
   - Root keys are derived via **Argon2id** (32 MiB RAM, 3 iterations) and **HKDF-SHA256**.
3. **Three-Room Compartmentalization**:
   - **Everyday Room**: Email, streaming, shopping, Wi-Fi. Quick biometric unlock.
   - **Money Room**: Net banking, PPF, EPF/UAN, Income Tax portal, credit cards. **Drops key material in memory after 60s idle**. Requires re-authentication for every session.
   - **Legacy Room**: Insurance policies, bank locker instructions, family will copy. Protected by **Shamir's Secret Sharing over $GF(2^8)$** (default 2-of-3 scheme) for family heirs.
4. **Nominee Kit & Printable Cards**:
   - Splitting the Legacy room key into $n$ physical shares ($k$-of-$n$, default 2-of-3).
   - Generates an official printable PDF with individual share cards and plain-language Hindi/English instructions.
   - Any $k-1$ shares give mathematically zero information.
   - Includes an interactive on-device practice drill.
5. **Printable Emergency Kit**:
   - Single-page printable PDF generated via native Android `PdfDocument` containing the 128-bit Secret Key, Vault ID, QR code payload, and locker storage advice.
6. **Hardware Isolation & Memory Hygiene**:
   - StrongBox Keymaster / TEE hardware isolation check.
   - Active secret zeroing (`key.fill(0)`) on lock, background, or Money room timeout.
   - WindowManager `FLAG_SECURE` blocks screenshots, screen recording, and blanks recent apps switcher previews.
   - Clipboard auto-clear timer with `EXTRA_IS_SENSITIVE` flag (Android 13+).
7. **Native Android Autofill Framework**:
   - Fully implements `android.service.autofill.AutofillService` with strict domain and package signature matching.
   - Money room entries require in-line authentication before autofilling.

---

## 🏛️ Architecture Overview

```
tijori-android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Zero INTERNET, AutofillService, FileProvider
│   │   ├── java/com/tijoriapp/vault/
│   │   │   ├── MainActivity.kt          # Lifecycle auto-lock, BiometricPrompt, SAF PDF exporter
│   │   │   ├── AppController.kt         # Reactive state, in-memory key hygiene, 60s idle timer
│   │   │   ├── autofill/
│   │   │   │   └── TijoriAutofillService.kt # Android Autofill Framework integration
│   │   │   ├── crypto/
│   │   │   │   ├── Argon2id.kt          # RFC 9106 memory-hard KDF
│   │   │   │   ├── Hkdf.kt              # RFC 5869 HMAC-SHA256 extract & expand
│   │   │   │   ├── AesGcm.kt            # AES-256-GCM with 96-bit random IV and AAD
│   │   │   │   ├── ShamirSecretSharing.kt # Galois Field GF(2^8) polynomial k-of-n secret sharing
│   │   │   │   ├── KeystoreManager.kt   # Android Keystore StrongBox/TEE & Biometric CryptoObject
│   │   │   │   └── PassphraseGenerator.kt # 7,776-word Diceware, entropy estimator, 128-bit keys
│   │   │   ├── data/
│   │   │   │   ├── Models.kt            # VaultItem, VaultField, AuditEvent, RoomType
│   │   │   │   ├── LocalVaultDb.kt      # Encrypted SQLite database with zero plaintext secrets
│   │   │   │   ├── IndiaTemplates.kt    # NetBanking, PPF, EPF/UAN, Income Tax, Cards with PIN hint
│   │   │   │   └── EmergencyKitExporter.kt # Native Android PdfDocument Emergency & Nominee Kit exporter
│   │   │   └── ui/
│   │   │       ├── Theme.kt             # Tijori Brass Safe Palette (Navy, Gold, Emerald, Coral)
│   │   │       ├── I18n.kt              # English + Hindi (हिन्दी) translation engine
│   │   │       ├── Components.kt        # AppCard, BigButton, MaskedFieldRow (30s auto-hide)
│   │   │       ├── MainShell.kt         # Safe lock bar, 4 bottom tabs, dynamic screen routing
│   │   │       ├── SetupAndUnlock.kt    # 3-step setup wizard & Biometric/Passphrase unlock
│   │   │       ├── RoomsHome.kt         # 3 rooms switcher, search, password health alerts
│   │   │       ├── ItemDetailAndEdit.kt # Decrypted detail view, template editor, password generator
│   │   │       ├── NomineeKitScreen.kt  # Shamir config, printable PDF cards, practice drill
│   │   │       ├── TrustPageScreen.kt   # Live proofs: zero INTERNET, Keystore level, cert fingerprint
│   │   │       └── SettingsScreen.kt    # Auto-lock sliders, clipboard timeout, wipe vault
│   │   ├── res/                         # Vector safe icons, brass safe drawables, Anek/Mukta fonts
│   │   └── test/
│   │       └── java/com/tijoriapp/vault/
│   │           └── CryptoTest.kt        # Unit tests for HKDF, AES-GCM, Shamir, Diceware, Argon2id
│   └── build.gradle.kts
├── .github/workflows/
│   └── build-apk.yml                    # CI pipeline with zero INTERNET check, APK & AAB artifact builds
└── scripts/
    ├── setup-signing.sh                 # Generates release keystore
    ├── get-play-bundle.sh               # Local build of signed APK & AAB
    ├── ship-to-github.sh                # Push to GitHub repository
    └── update-from-zip.sh               # In-place code updates
```

---

## 🚀 Building & Running

### Prerequisites
- JDK 17 or higher
- Android SDK 36 (minSdk 28, compileSdk 36)
- Gradle 8.14.3 (included via wrapper)

### Build Debug APK & Run Unit Tests
```bash
./gradlew test
./gradlew assembleDebug
```

### Build Production Release APK and Play Bundle (.aab)
```bash
bash scripts/setup-signing.sh
bash scripts/get-play-bundle.sh
```
Output files will be generated in:
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

---

## 🔒 Security Best Practices

- **Never share your 6-word Master Passphrase**.
- **Print your Emergency Kit** and store it in a physical home safe or bank locker. Do not upload it to Google Drive or iCloud.
- **For Credit/Debit Cards**: Store only a **PIN Hint** (e.g. *"Year of graduation minus 5"*), never your actual 4-digit PIN or CVV.
- **For Nominee Cards**: Distribute physical printed cards to separate trusted persons (e.g., spouse and legal guardian). No individual card can open the vault alone.

---

## 📜 License
Apache 2.0. Clean-room implementation with zero telemetry or third-party trackers.
