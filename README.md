# MoneyLog: Finance Tracker

**MoneyLog: Finance Tracker** is a privacy-first Android personal finance app built with Kotlin and Jetpack Compose. Track income, expenses, budgets, loans, EMIs, and recurring bills with dashboards, reports, financial insights, secure backups, CSV imports, reminders, and an optional finance coach.

## Features

- Income and expense tracking
- Monthly overview, reports, and category breakdowns
- Budgets, savings, and loan/EMI tracking
- Transaction reminders and supported notification-based payment capture
- CSV import and local transaction storage
- Credit-health information and finance insights
- Privacy-conscious design and secure-backend setup guidance

## Build and test

GitHub Actions builds the Android debug APK on pushes to `main` or `master`, and for pull requests. After a successful run, open the workflow run's **Artifacts** section and download `MoneyLog-android-debug-apk` to install and test on an Android device. Debug builds are for testing, not public release distribution.

## Live data and financial safety

- Android network permissions do **not** mean a live credit bureau or AI provider is connected.
- Credit scores entered by the user are labelled user-reported and are not represented as verified TransUnion CIBIL data.
- Until an authorized bureau integration is configured, the credit screen must clearly report that live bureau data is not connected.
- The finance coach must not be described as a live AI service or a source of current lender offers unless a real provider integration has been deployed and tested.
- Live results should identify their source and last successful update time, handle failed or stale requests visibly, and use a trusted backend for provider credentials.
- Obtain explicit user consent before sending personal financial data to an AI provider. Never invent credit scores, bureau results, lender rates, or live offers.

## Security and privacy

Review [Secure backend setup](docs/SECURE_BACKEND_SETUP.md) before configuring any provider integration. Never commit API keys, signing keys, passwords, service-account files, or private configuration.

## Project status

This repository is under active development. Verify the Android build and test core flows on a device before relying on the app for financial records.
