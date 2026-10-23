# MoneyLog secure backend setup

This repository now contains an **undeployed backend scaffold**. MoneyLog does not call this function yet. Do not describe the AI coach as live until Firebase is configured, the function is deployed, App Check is enforced, and the Android client is integrated and tested.

## Security decisions

- OpenAI requests run only on Firebase Cloud Functions; the OpenAI key is a Firebase Secret Manager secret, never an Android resource, Gradle property, GitHub file, or chat message.
- The callable requires Firebase Authentication and Firebase App Check.
- Input is limited to 2,000 characters and 5 requests per authenticated user per minute.
- Prompts are not written to application logs or stored in Firestore by this function. A minimal per-user rate-limit counter is stored in `_privateRateLimits`.
- This is a personal-use starter, not a complete production abuse-prevention or financial-advice system. Configure Firestore TTL for old `_privateRateLimits` documents or add scheduled cleanup before long-term use.
- The function does not connect to CIBIL or a bank. Do not submit PAN, OTPs, passwords, account/card numbers, or documents to the coach.

## 1. Create your Firebase project

1. Open [Firebase Console](https://console.firebase.google.com/) and sign in with your own Google account.
2. Choose **Create a project**. Give it a project ID you control; the ID must be globally unique. You do not need Google Analytics for this scaffold.
3. In the project, enable **Authentication** and choose a sign-in method. For a personal app, email/password or Google sign-in is preferable to an unauthenticated public endpoint.
4. Create a **Cloud Firestore** database. Keep it in a region appropriate for your users and configure restrictive rules. Do not make the database public.
5. Cloud Functions deployment may require the Firebase **Blaze** billing plan and a linked billing account. Review current pricing and set budget alerts before enabling paid services.

## 2. Set up the OpenAI API account

1. Open the [OpenAI API platform](https://platform.openai.com/) and sign in/create an account.
2. Configure API billing separately from any ChatGPT subscription, review current model pricing, and set usage/spend limits or alerts where available.
3. Create a dedicated API project/key for MoneyLog. Never paste the key into ChatGPT, GitHub issues, Android code, or this MoneyLog repository.

## 3. Deploy from your own computer

Install Node.js 22 and the [Firebase CLI](https://firebase.google.com/docs/cli). Clone this MoneyLog repository and run the following from its root:

```sh
npm --prefix functions install
npm --prefix functions run build
firebase login
firebase use --add
```

Choose the Firebase project you just created when prompted. This writes a local `.firebaserc`; do not commit it if you prefer to keep your project ID out of the repository.

Set the key interactively in your own terminal. The CLI prompts for the value; it should not appear in command history:

```sh
firebase functions:secrets:set OPENAI_API_KEY
```

Then deploy:

```sh
firebase deploy --only functions
```

If the project has not enabled required APIs or billing, Firebase CLI will explain what needs enabling. Review the prompt and console before approving charges.

## 4. Finish app security before calling it

- Register the Android app in Firebase using the exact application ID from this MoneyLog repository, then download `google-services.json` to the Android app module locally.
- Add/configure the Firebase Android SDKs for Authentication, App Check (Play Integrity for a Play-distributed app; use the appropriate debug provider only for local development), and Cloud Functions.
- Sign in before calling `financeCoach`; the callable automatically carries Firebase Auth and App Check tokens through the Firebase SDK.
- Keep App Check enforcement on. Do not weaken the backend to accept anonymous, unverified requests just to make a test pass.
- Connect the app UI only after the SDK integration compiles and you have verified authenticated, unauthenticated, missing-App-Check, rate-limited, and provider-error behavior.
- Never send all transactions by default. If adding user financial context later, disclose exactly what is sent and obtain explicit consent first.

## 5. Cost and privacy checks

- Start with manual test questions that contain no personal identifiers.
- Check Firebase Functions, Firestore, Secret Manager, and OpenAI usage after tests.
- Keep the function's maximum instance count and per-user limit conservative; add global budget/abuse controls before public distribution.
- The rate-limit collection contains user identifiers and timestamps. Set a Firestore TTL policy for `bucketStart` or implement cleanup after validating the policy.
- If you no longer need the integration, revoke the OpenAI key and delete the Firebase resources you created.

## Current status

- Backend source scaffold: committed to the repository when this document is added.
- Firebase project / API key / deployment: **not configured**.
- Android Firebase SDK and callable integration: **not implemented yet**.
- CIBIL integration: **not connected**; use the official consumer report process until an authorized provider integration is available.
