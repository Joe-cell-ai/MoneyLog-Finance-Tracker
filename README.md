# MoneyLog: Finance Tracker

MoneyLog is an Android personal-finance tracker built with **Kotlin and Jetpack Compose**. It brings everyday income, spending, budgets, loans, and money reports together in one app. The app is designed to keep finance records on-device by default; no Cloudflare or Firebase backend is included in this repository.

## Features

### Home and money overview
- Monthly overview with month navigation, balance, income, expenses, and savings summaries
- Monthly budget progress and spending breakdowns
- Category-based charts and visual summaries
- Browse older and upcoming months where supported by the screen

### Transactions
- Add income and expense transactions
- Record amount, description, category, date, payment method, notes, and optional tag
- Browse transaction history grouped by date
- Filter transactions by all, income, or expenses
- Edit, delete, and review transaction details where supported
- Import transactions from CSV with a preview and duplicate filtering
- Export transaction records to CSV

### Categories and tags
- Manage custom transaction categories
- Manage custom tags
- Choose categories and tags while entering transactions
- Use category colours to make spending types easier to scan

### Budgets, reports, and insights
- Set and review monthly category budgets
- View monthly income, expenses, savings, and category breakdowns
- Explore spending patterns and financial insights
- View credit-health information and recommendations
- Credit scores entered by the user are user-reported; the app does not claim a live or verified CIBIL bureau connection

### Loans and EMIs
- Keep a list of loans with lender, outstanding balance, EMI amount, due date, and interest details
- Track repayment progress and payments made where recorded
- View loan details and repayment information
- Schedule local monthly EMI reminders
- Manage loan records from the app

### Calendar
- Browse transactions by month and date
- View transaction markers and selected-day income/expense totals where available
- Navigate between months

### Payment-notification capture
- Optional Android notification-listener support to detect supported payment notifications and help create transaction records
- Requires the user to explicitly enable notification access in Android settings
- Notification parsing depends on the source app and notification format; it is not direct bank-account or UPI-account access, and not every payment is guaranteed to be detected

### Backup, import, and settings
- Export local finance data to a JSON backup
- Back up transactions, loans, custom categories, custom tags, appearance preferences, reported credit score, and monthly budgets
- Configure available appearance and currency preferences
- Keep local finance data separate from third-party services

## Privacy and accuracy

- This repository does **not** include a deployed Cloudflare Worker, Firebase backend, or remote AI finance coach.
- The app does not claim to connect directly to bank accounts, CIBIL, or credit bureaus.
- Notification capture is optional and depends on Android notification access and compatible notifications.
- Do not treat user-entered credit scores or app estimates as verified financial or credit-bureau data.
- Keep release signing keys, passwords, API keys, and private configuration out of GitHub.

## Build and test

The repository includes a GitHub Actions Android build workflow. Open the **Actions** tab, choose the Android build run, and download the generated debug APK from its **Artifacts** section after a successful run. Debug APKs are intended for testing, not as a substitute for a properly signed release build.

Before publishing, build the app and test core flows on a real Android device, including transaction entry/edit/delete, category and tag selection, CSV import/export, backup/restore if available in the UI, loan and EMI reminders, and notification access.

## Project status

MoneyLog is under active development. Feature availability may vary by current build; the list above describes the app's intended and repository-supported areas, not a guarantee that every flow has been fully verified on every device. No remote AI/cloud features are included unless deliberately added in a future change.
