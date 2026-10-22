# Open-Finance-AI-Rebuild
A complete, privacy-first Android personal finance app built with Kotlin and Jetpack Compose. Track income, expenses, budgets, loans, EMIs, and recurring bills with interactive dashboards, reports, financial insights, secure encrypted backups, CSV imports, reminders, and an optional AI finance coach.

## Build and test
The GitHub Actions workflow builds the Android debug APK on pushes to `main` or `master`. After a successful run, download the `open-finance-ai-debug-apk` artifact from the Actions run to install and test on an Android device.


## Live data and financial safety

- Android network permissions are enabled, but this does **not** mean a live bureau or AI provider is connected.
- Credit scores entered by the user are labelled user-reported and are not represented as verified TransUnion CIBIL data.
- Until an authorized bureau integration is configured, the credit screen explicitly reports that live bureau data is not connected.
- The finance coach currently uses local rules. It must not be described as a live AI service or a source of current lender offers.
- Any future live result must identify its source and last successful update time, handle failed/stale requests visibly, and use a trusted backend for provider credentials. Obtain user consent before sending personal financial data to an AI provider.
- Do not invent credit scores, bureau results, lender rates, or live offers.
