package com.tijoriapp.vault.data

/**
 * India-first templates (Spec Page 1, 4 & 8):
 * Net banking, PPF, EPF/UAN, Income-tax portal, Insurance, Card (with PIN hint), Email, Wi-Fi, Secure Note.
 */
object IndiaTemplates {

    data class TemplateFieldDef(
        val kind: FieldKind,
        val label: String,
        val hint: String = "",
    )

    data class TemplateDef(
        val type: ItemType,
        val title: String,
        val room: RoomType,
        val fields: List<TemplateFieldDef>,
        val advice: String = "",
    )

    val ALL = listOf(
        TemplateDef(
            type = ItemType.NET_BANKING,
            title = "Net Banking (SBI / HDFC / ICICI / Axis)",
            room = RoomType.MONEY,
            fields = listOf(
                TemplateFieldDef(FieldKind.URL, "Bank Login URL", "e.g., https://netbanking.hdfcbank.com"),
                TemplateFieldDef(FieldKind.CUSTOMER_ID, "Customer ID / User ID"),
                TemplateFieldDef(FieldKind.PASSWORD, "Login Password"),
                TemplateFieldDef(FieldKind.PASSWORD, "Transaction / Profile Password"),
                TemplateFieldDef(FieldKind.ACCOUNT_NUMBER, "Savings / Current Account Number"),
                TemplateFieldDef(FieldKind.IFSC, "IFSC Code", "e.g., HDFC0000123"),
            ),
            advice = "Stored in the Money room. Requires biometric or master passphrase unlock each session. Never autofilled silently."
        ),
        TemplateDef(
            type = ItemType.PPF,
            title = "Public Provident Fund (PPF)",
            room = RoomType.MONEY,
            fields = listOf(
                TemplateFieldDef(FieldKind.ACCOUNT_NUMBER, "PPF Account Number"),
                TemplateFieldDef(FieldKind.CUSTOMER_ID, "Bank / Post Office Branch"),
                TemplateFieldDef(FieldKind.NOTE, "Nominee Name & Relationship"),
                TemplateFieldDef(FieldKind.URL, "Maturity Year / Passbook Location"),
            ),
            advice = "Important 15-year tax-free asset. Also consider duplicating the policy/account number in the Legacy room for heirs."
        ),
        TemplateDef(
            type = ItemType.EPF_UAN,
            title = "EPFO / Unified Portal (UAN)",
            room = RoomType.MONEY,
            fields = listOf(
                TemplateFieldDef(FieldKind.UAN, "12-Digit UAN Number", "Universal Account Number"),
                TemplateFieldDef(FieldKind.PASSWORD, "Unified Portal Password"),
                TemplateFieldDef(FieldKind.URL, "EPFO Member Portal URL", "https://unifiedportal-mem.epfindia.gov.in"),
                TemplateFieldDef(FieldKind.NOTE, "Linked Aadhaar Mobile Number"),
            ),
            advice = "Keep your EPFO credentials updated every year for annual interest passbook checks."
        ),
        TemplateDef(
            type = ItemType.TAX_PORTAL,
            title = "Income Tax e-Filing Portal",
            room = RoomType.MONEY,
            fields = listOf(
                TemplateFieldDef(FieldKind.PAN, "Permanent Account Number (PAN)", "10-character PAN"),
                TemplateFieldDef(FieldKind.PASSWORD, "e-Filing Portal Password"),
                TemplateFieldDef(FieldKind.URL, "e-Filing Portal URL", "https://eportal.incometax.gov.in"),
                TemplateFieldDef(FieldKind.NOTE, "AIS / Form 26AS Notes"),
            ),
            advice = "Crucial for annual ITR filing and AIS verification. Keep your PAN and login password synchronized."
        ),
        TemplateDef(
            type = ItemType.CARD,
            title = "Debit / Credit Card (Safe PIN Hint)",
            room = RoomType.MONEY,
            fields = listOf(
                TemplateFieldDef(FieldKind.ACCOUNT_NUMBER, "Card Last 4 Digits", "Never save full 16 digits"),
                TemplateFieldDef(FieldKind.USERNAME, "Name on Card"),
                TemplateFieldDef(FieldKind.NOTE, "Expiry MM/YY & Issuer Bank"),
                TemplateFieldDef(FieldKind.PIN_HINT, "ATM / POS PIN Hint", "A clue only you understand (e.g. 'Old scooter year - 4')"),
            ),
            advice = "Spec Rule: The default is a PIN Hint. If you must store a real PIN or CVV, do so only behind an Advanced toggle in the Money room."
        ),
        TemplateDef(
            type = ItemType.INSURANCE,
            title = "Life / Health Insurance (LIC / Star / HDFC)",
            room = RoomType.LEGACY,
            fields = listOf(
                TemplateFieldDef(FieldKind.POLICY_NUMBER, "Policy Number"),
                TemplateFieldDef(FieldKind.USERNAME, "Insurer Company Name"),
                TemplateFieldDef(FieldKind.NOTE, "Sum Assured & Premium Due Date"),
                TemplateFieldDef(FieldKind.USERNAME, "Nominee Name & Share %"),
                TemplateFieldDef(FieldKind.URL, "Physical Document / Policy Bond Location"),
            ),
            advice = "Stored in the Legacy room. Your family can open this room with the Nominee Kit shares even if you are unavailable."
        ),
        TemplateDef(
            type = ItemType.LOCKER,
            title = "Bank Locker & Family Instructions",
            room = RoomType.LEGACY,
            fields = listOf(
                TemplateFieldDef(FieldKind.ACCOUNT_NUMBER, "Locker Number & Bank Branch"),
                TemplateFieldDef(FieldKind.NOTE, "Locker Key Physical Location"),
                TemplateFieldDef(FieldKind.NOTE, "Joint Holder / Nominee Details"),
                TemplateFieldDef(FieldKind.NOTE, "Special Instructions for Family"),
            ),
            advice = "Everything in the Legacy room is recoverable by your family using the 2-of-3 Shamir Nominee Kit."
        ),
        TemplateDef(
            type = ItemType.EMAIL,
            title = "Email Account (Google / Outlook / Proton)",
            room = RoomType.EVERYDAY,
            fields = listOf(
                TemplateFieldDef(FieldKind.USERNAME, "Email Address"),
                TemplateFieldDef(FieldKind.PASSWORD, "Password"),
                TemplateFieldDef(FieldKind.URL, "Recovery Email / Backup Codes Note"),
            ),
            advice = "Your primary email is the key to recovery of other services. Keep password entropy above 70 bits."
        ),
        TemplateDef(
            type = ItemType.NOTE,
            title = "Encrypted Secure Note",
            room = RoomType.EVERYDAY,
            fields = listOf(
                TemplateFieldDef(FieldKind.NOTE, "Encrypted Secret Content"),
            ),
            advice = "Encrypted with AES-256-GCM. Completely private on this device."
        ),
    )
}
