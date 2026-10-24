# MoneyLog: Finance Tracker

**MoneyLog: Finance Tracker** is an Android personal finance app built with Kotlin and Jetpack Compose. Track income, expenses, budgets, loans, EMIs, and recurring bills with dashboards, reports, financial insights, backups, CSV imports, and reminders.

## Features

- Income and expense tracking
- Monthly overview, reports, and category breakdowns
- Budgets, savings, and loan/EMI tracking
- Transaction reminders and supported notification-based payment capture
- CSV import and local transaction storage
- Credit-health information and finance insights
- Privacy-conscious design

## Build and test

GitHub Actions builds the Android debug APK on pushes to `main` or `master`, and for pull requests. After a successful run, open the workflow run's **Artifacts** section and download `MoneyLog-android-debug-apk` to install and test on an Android device. Debug builds are for testing, not public release distribution.

## Live data and financial safety

- MoneyLog does not currently include a deployed cloud backend or connected remote AI finance coach.
- Credit scores entered by the user must be labelled user-reported, not represented as verified TransUnion CIBIL data.
- The credit screen must clearly indicate that live bureau data is not connected unless an authorized integration is added and tested.
- Do not present lender rates, live offers, or AI-generated responses as live data unless a real provider integration is intentionally added and verified.
- Never invent credit scores, bureau results, lender rates, or live offers.

## Security and privacy

Never commit API keys, signing keys, passwords, service-account files, or private configuration. Keep financial data handling transparent and obtain explicit consent before adding any future external provider integration.

## Project status

This repository is under active development. Verify the Android build and test core flows on a device before relying on the app for financial records.
