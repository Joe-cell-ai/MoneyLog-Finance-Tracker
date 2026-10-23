# Cloudflare Worker AI backend setup

This is a separate backend scaffold for Open Finance AI. It does not modify the Android app or remove the existing Firebase Functions scaffold. It does not make the AI coach live until an identity provider is selected, configuration is added, the Worker is deployed, and Android is integrated and tested.

## Architecture

- Cloudflare Worker handles `POST /v1/finance-coach`.
- The Worker calls the OpenAI Responses API using the server-side `OPENAI_API_KEY` secret.
- Requests must include a valid JWT from an identity provider that publishes compatible JWKS keys. The token issuer, audience and JWKS URL are configured by the operator.
- Cloudflare's Worker rate-limit binding limits each authenticated subject to 5 requests per 60-second period. This is abuse protection, not a hard spending cap; rate-limit behavior can be approximate across Cloudflare locations.
- The endpoint accepts only a question (up to 2,000 characters). It does not automatically send transactions, income, expenses, loans or other app data.
- Prompts, bearer tokens, API keys and provider response bodies are not logged.
- Responses state that live credit-bureau data is not connected.

## Important authentication decision

The Android project currently has no complete sign-in/token flow. Do not deploy this endpoint for public use until an identity provider is chosen and the Android app obtains valid user tokens. Do not replace this requirement with a shared secret embedded in the APK: users can extract secrets from mobile apps.

The Worker expects these environment variables:

- `AUTH_JWKS_URL`: HTTPS URL for the identity provider's JSON Web Key Set.
- `AUTH_ISSUER`: exact expected JWT issuer.
- `AUTH_AUDIENCE`: exact expected JWT audience/client identifier.
- `OPENAI_API_KEY`: Cloudflare secret, not a plain-text variable.

The identity provider must issue signed JWT access/ID tokens whose issuer, audience and signing keys match the configured values. Confirm which token type the provider issues before configuring the Android client. Do not guess these values.

## Configure Cloudflare

1. Open Workers & Pages and select `open-finance-ai-api`.
2. Open Settings > Variables and Secrets.
3. Add `AUTH_JWKS_URL`, `AUTH_ISSUER` and `AUTH_AUDIENCE` as variables only after selecting an identity provider and verifying its official configuration values.
4. Add `OPENAI_API_KEY` as a Secret. Never commit it to GitHub, put it in Android resources, or paste it into chat.
5. Deploy from the `cloudflare-worker` directory using Wrangler after dependencies are installed and configuration has been checked.

## Local development and checks

From the repository root:

```sh
cd cloudflare-worker
npm install
npm run typecheck
npm run dev
```

Deploy only after auth variables and the secret are configured:

```sh
npm run deploy
```

Test the public health endpoint:

```sh
curl https://YOUR-WORKER-URL/health
```

Test the AI endpoint only with a valid JWT from the configured identity provider:

```sh
curl -X POST https://YOUR-WORKER-URL/v1/finance-coach \
  -H "Authorization: Bearer YOUR_VALID_USER_JWT" \
  -H "Content-Type: application/json" \
  -d '{"message":"How can I make a monthly budget?"}'
```

Never use a real token in source control or share it in chat. The sample placeholder above is not a working credential.

## Known remaining work

- Select an identity provider and configure issuer, audience and JWKS URL.
- Add the matching Android sign-in/token flow and securely attach the token to Worker requests.
- Configure the OpenAI secret and verify the Cloudflare rate-limit binding in the dashboard/deployment.
- Run typecheck and endpoint tests in a configured environment.
- Connect the Android AI Coach UI on a separate branch after the backend contract and auth provider are confirmed.
- Review OpenAI API usage and set account/project usage limits where available. Cloudflare's free allowance does not cover OpenAI API charges.
- Obtain explicit consent before adding any personal financial context to AI requests.
