# Open Finance AI

A privacy-first Android personal-finance app built with Kotlin and Jetpack Compose.

## Current milestone (0.1.0)

- Finance dashboard with balance, income, expenses, and recent activity
- Add income and expense transactions with categories
- Local transaction persistence using app-private SharedPreferences
- Transaction history filters and delete action
- Starter budget overview with category progress bars
- Interactive reducing-balance loan EMI calculator
- Basic data/privacy information screen

## Build

Requirements: Android Studio, JDK 17, Android SDK 35, and Gradle 8.11.1 or compatible.

```bash
gradle assembleDebug
gradle test
```

Open the repository root in Android Studio to sync and run the app on an emulator or Android device.

## Important notes

- The balance includes a starter opening balance of ₹85,000 plus recorded income minus recorded expenses. This is sample/demo data, not a connected bank balance.
- Budget limits are starter examples and are not yet editable.
- Transactions stay on this device. Cloud sync, encrypted backup, CSV import/export, bank integrations, reminders, and the AI finance coach are not implemented in this first milestone.
- No banking credentials or real bank account data are requested by this starter app.

## Roadmap

1. Establish and verify the Android build.
2. Add editable budgets, recurring bills, and loan records.
3. Add CSV import/export and encrypted backup/restore.
4. Add automated unit/UI tests and accessibility polish.
5. Evaluate optional AI finance coaching with explicit user consent and privacy controls.
