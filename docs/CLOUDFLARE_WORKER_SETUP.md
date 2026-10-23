# Open Finance AI — Cloudflare backend setup

This backend is being implemented on `feature/cloudflare-ai-backend`. The `main` branch is not changed. Do not consider the backend production-ready until D1/R2 resources are created, migrations are applied, Supabase Auth is configured, secrets are set, and integration tests pass.

## Architecture

- **Cloudflare Worker**: authenticated API and AI coach.
- **Cloudflare D1**: SQL storage for user-owned transactions, categories, budgets, loans, savings goals and file metadata.
- **Cloudflare R2**: private object storage for receipts and documents. Objects are accessed only through the authenticated Worker; do not make the bucket public.
- **Supabase Auth**: planned identity provider. The Worker validates signed access tokens and uses the verified `sub` claim as the owner ID.
- **OpenAI API**: used by the finance coach through a server-side Worker secret.

Every data query must scope records to the verified user's identity. Never accept a `user_id` from request JSON as proof of ownership. Do not embed an OpenAI key, Supabase service-role key, database credential, or other server secret in Android or source control.

## Storage API implemented in the Worker

All endpoints below require a valid bearer access token after Supabase JWT configuration is complete.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET, POST | `/v1/transactions` | List or create transactions |
| DELETE | `/v1/transactions/:id` | Delete a transaction owned by the signed-in user |
| GET, POST | `/v1/categories` | List or create categories |
| GET, POST | `/v1/budgets` | List or create/update a category budget for a month |
| GET, POST | `/v1/loans` | List or create loan records |
| GET, POST | `/v1/savings` | List or create savings goals |
| GET, POST | `/v1/files` | List or upload private files |
| GET, DELETE | `/v1/files/:id` | Download or delete a file owned by the signed-in user |

File uploads currently accept PDF, JPEG, PNG and WebP, with a 10 MiB per-file limit. File objects use generated private keys; client-supplied paths are never used as object keys. The API returns downloads with private/no-store cache headers. Add further file types only after reviewing the security implications.

The first storage API iteration does not yet provide transaction update, category update/delete, budget delete, loan update/delete or savings-goal update/delete endpoints. Android must not be wired to assume those operations exist until implemented and tested.

## 1. Create Cloudflare resources

1. In Cloudflare Dashboard, open **Workers & Pages** and create/select the Worker named `open-finance-ai-api`.
2. Open **Storage & databases → D1 SQL Database** (dashboard labels may vary), create a database named `open-finance-ai-db`, and copy its database ID into `cloudflare-worker/wrangler.toml` in place of `REPLACE_WITH_D1_DATABASE_ID`. The ID is configuration, not a secret.
3. Create an R2 bucket named `open-finance-ai-files`. Keep it private; do not enable public bucket access or publish a custom public domain.
4. Before deploying, confirm the D1 binding is named `DB` and the R2 binding is named `FILES`, matching the Worker config.

Do not deploy while the D1 database ID is still the placeholder.

## 2. Apply the database schema

From the repository root, after installing Node.js and Wrangler:

```sh
cd cloudflare-worker
npm install
npx wrangler d1 migrations apply open-finance-ai-db --remote
npm run typecheck
```

Review the migration in `cloudflare-worker/migrations/0001_initial_schema.sql` before applying it. It creates the initial schema for categories, transactions, budgets, loans, savings goals and private-file metadata. Do not put real personal financial data into test records.

## 3. Configure Supabase Auth (after D1 and R2)

1. Create/select the Supabase project and enable the sign-in method you intend to support.
2. Configure Supabase to use an asymmetric JWT signing key compatible with its published JWKS endpoint. Do not share or copy signing private keys into Cloudflare or chat.
3. Verify the official values in the Supabase dashboard for the project URL, JWKS URL, JWT issuer and access-token audience. Do not guess them; older projects using symmetric HS256 signing may require a signing-key migration before JWKS validation works.
4. Set `AUTH_JWKS_URL`, `AUTH_ISSUER` and `AUTH_AUDIENCE` as Worker variables using the verified values. Store `OPENAI_API_KEY` as a Worker **Secret**, not a plain variable.
5. Do not use the Supabase service-role/secret key in the Android app. The app should use the public publishable/anon key only if the selected Supabase client needs it; the user access token is what authorizes Worker requests.

The Android app still needs a Supabase sign-in/token flow and integration with these endpoints. Until that work is completed, the backend API is not ready for the app.

## 4. Security and testing checklist

- Keep the R2 bucket private.
- Ensure all protected endpoints reject requests without a valid token (401).
- Confirm one user's token cannot read, download, update or delete another user's records/files.
- Test invalid JSON, invalid amounts/dates, unsupported file types and files larger than 10 MiB.
- Verify rate-limit binding behavior in a deployed Worker; rate limits are abuse protection and not a hard OpenAI spending cap.
- Do not log bearer tokens, prompts, personal financial records, API keys or provider response bodies.
- Use synthetic test records only, and delete them after testing.
- Configure OpenAI usage alerts/limits where available; Cloudflare usage does not cover OpenAI API charges.

Health check:

```sh
curl https://YOUR-WORKER-URL/health
```

Unauthenticated protected-route check (expected 503 until auth configuration exists, then 401):

```sh
curl -i https://YOUR-WORKER-URL/v1/transactions
```

Deploy only after resource IDs, bindings, migrations, authentication settings and secrets have been checked:

```sh
npm run deploy
```

## Remaining work

- Replace the D1 placeholder with the actual database ID and apply migrations.
- Create the private R2 bucket and confirm bindings in the deployed Worker.
- Configure Supabase JWT verification and the OpenAI secret.
- Complete and test Supabase sign-in/token integration in Android.
- Add remaining CRUD operations and connect app screens to the API.
- Run automated and live integration tests, including cross-user access tests.
