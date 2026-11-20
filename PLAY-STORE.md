# Play Console notes for Tijori 2.1

## Data safety form
* **Does your app collect or share any of the required user data types?** No.
  The optional Google Drive backup is written by the user, through Android's file picker, to a file in the user's own
  Drive; the Google Drive app uploads it. The developer never receives it, and the file is end-to-end encrypted.
  Google's guidance: data transferred by the user to a destination they choose, and not to the developer, is not
  "collected" or "shared". If a reviewer asks, describe it exactly like this.
* **Is all user data encrypted in transit?** Not applicable: the app itself has no network access. (The backup file is
  encrypted before it leaves the app.)
* **Do you provide a way for users to request that their data be deleted?** Yes: Settings → Erase this vault, or
  uninstall; delete `Tijori-backup.tijori` from Drive to remove the backup.
* Permissions declared: `USE_BIOMETRIC` only. Autofill uses the `BIND_AUTOFILL_SERVICE` service permission (held by the
  system, not the app). Storage Access Framework needs no permission.

## Policy points
* **Autofill**: uses the Android Autofill Framework, no AccessibilityService, so no accessibility declaration is needed.
* **Privacy policy URL**: https://souravroy0708.github.io/tijori-android/privacy.html (docs/privacy.html in this repo).
* **Target audience**: 18+ (financial information). Not designed for children.

## Short description (80 chars)
Password vault with Google Drive backup. Restores after reinstall. No account.

## Full description (draft)
Saare password. Ek chaabi.

Tijori remembers your Gmail, banking, shopping and Wi-Fi passwords and fills them in apps and Chrome. An encrypted copy
lives in your own Google Drive, so uninstalling the app or losing your phone never means losing your passwords.

• Google Drive backup: one encrypted file, updated automatically after every change
• Reinstalled? Restore from Google Drive and type your passphrase. Everything comes back
• Import all your passwords from Google Password Manager, Chrome, Bitwarden, 1Password or LastPass
• Autofill that only fills on the real app or website, never on look-alike phishing pages
• Money room: banking needs a fresh fingerprint every time and locks itself
• Family room: nominee cards let your family open insurance, locker and will details if something happens to you
• Security check: weak, reused and expiring passwords, cards and documents
• Password history, built-in 2-step codes and a password generator
• No internet permission, no account, no ads, no tracking. Free
• English and हिन्दी

## What's new (2.1)
Automatic Google Drive backup and passphrase-only restore after reinstall, import from Google Password Manager and other
apps, password history, one-tap copy, new welcome screens and an in-app help guide.

## Test before release (real phones)
1. Turn on Drive backup during setup → check `Tijori-backup.tijori` appears in Drive (drive.google.com).
2. Add an item → wait ~10 s → the Drive file's "modified" time changes.
3. Uninstall Tijori → reinstall → Restore from Google Drive → passphrase only → all items back, status shows "Backed up".
4. Same with "Restore with passphrase only" OFF → restore must ask for the Secret Key.
5. Airplane mode → add an item → Tijori says "Backing up…"; turn network on → Drive app uploads.
6. Delete the Drive file → Tijori shows "Backup needs attention" → Change → pick a new file.
7. Import a Google Password Manager CSV export with ~50 rows; import it twice → second time adds 0.
