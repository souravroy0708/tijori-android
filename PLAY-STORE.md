# Play Console notes for Tijori 2.0

## Data safety form
* **Does your app collect or share any of the required user data types?** No.
* **Is all user data encrypted in transit?** Not applicable (the app has no network access).
* **Do you provide a way for users to request that their data be deleted?** Yes: Settings → Erase this vault, or uninstall.
* Permissions declared: `USE_BIOMETRIC` only. Autofill uses the `BIND_AUTOFILL_SERVICE` service permission (held by the system, not the app).

## Policy points
* **Autofill**: uses the Android Autofill Framework, no AccessibilityService, so no accessibility declaration is needed.
* **Privacy policy URL**: required for Play. Host the "Privacy policy" text from About (Settings → About) on any page you control.
* **Target audience**: 18+ (financial information). Not designed for children.

## Short description (80 chars)
Offline vault for passwords, bank details & family info. No internet, no account.

## Full description (draft)
Tijori keeps your passwords, net banking logins, cards, PPF/EPF, insurance and locker details in one encrypted vault that never leaves your phone.

• No internet permission: Android itself blocks Tijori from the network
• No account, no server: only you hold the keys
• Money room: banking needs a fresh fingerprint every time and locks itself
• Family room: nominee cards let your family open insurance, locker and will details if something happens to you
• Security check: finds weak, reused and expiring passwords, cards and documents
• Built-in 2-step codes and password generator
• Autofill that only fills on the real app or website
• English and हिन्दी
