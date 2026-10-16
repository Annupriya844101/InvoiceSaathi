# InvoiceSaathi (Android)

A starter Android invoice app built with Kotlin + Jetpack Compose.

## Features
- Create invoices with customer, item, quantity, unit price, tax rate and notes.
- Generates a printable/shareable PDF invoice.
- Appends each invoice to `Documents/InvoiceSaathi/invoices.csv` (Excel-compatible).
- Stores a local invoice history in app-private storage.
- GitHub Actions workflow builds a debug APK on pushes and manual runs.

## Open / build
1. Create a new GitHub repository and upload this project.
2. Open the repository's **Actions** tab and enable workflows if prompted.
3. Push to `main` or use **Actions → Android APK → Run workflow**.
4. Download `app-debug.apk` from the workflow run's Artifacts section.
5. Open the APK on your Android phone and allow installation from your browser/files app when prompted.

Or open this folder in Android Studio and build the `app` configuration.

## Output locations
- CSV: `Documents/InvoiceSaathi/invoices.csv`
- PDFs: `Documents/InvoiceSaathi/`
- Local history: private app storage (not synced between devices)

CSV is UTF-8 with a BOM for better Excel compatibility. On Android versions using scoped storage, the app uses the Storage Access Framework to let the user choose a folder on first export. If folder access is cancelled, the invoice still remains in local app history and its PDF can be shared.

## Notes
- This is a starter app, not an accounting/tax compliance guarantee.
- Tax is a configurable percentage and does not assume a particular country's tax rules.
- No cloud sync, login, e-invoice/GST portal integration, or payment collection is included.
