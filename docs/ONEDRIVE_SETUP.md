# OneDrive backup setup

MoneyLog uses Microsoft identity platform device-code sign-in and Microsoft Graph delegated access. No Microsoft client secret is embedded in the Android app.

## Microsoft app registration

1. Create an app registration in Microsoft Entra.
2. Choose accounts that include personal Microsoft accounts.
3. Enable **Allow public client flows**.
4. Add the delegated Microsoft Graph permission **Files.ReadWrite**.
5. Copy the Application (client) ID.

Configure the repository Actions variable named `MICROSOFT_CLIENT_ID` with that Application (client) ID. The value is a public application identifier, not a password or client secret. The Android build reads it as a Gradle property and exposes it as a BuildConfig value. If the variable is not configured, the rest of the app still builds, but OneDrive sign-in remains disabled.

## Sign-in and token storage

The app uses Microsoft's device authorization flow for personal Microsoft accounts. The user approves the sign-in in Microsoft's browser. The refresh token is encrypted with an AES-GCM key held in Android Keystore. Access tokens are requested when a backup runs and are not persisted.

## Graph operations

Backups are written to a private `MoneyLog Backups` folder in the signed-in user's OneDrive. The app uploads JSON backup files, lets the user browse and restore a selected cloud backup, and retains the newest 30 files. Graph access is limited to the delegated `Files.ReadWrite` permission.
