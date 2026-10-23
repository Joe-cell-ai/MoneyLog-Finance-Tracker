import { createRemoteJWKSet, jwtVerify } from "jose";

interface Env {
  OPENAI_API_KEY?: string;
  AUTH_JWKS_URL?: string;
  AUTH_ISSUER?: string;
  AUTH_AUDIENCE?: string;
  RATE_LIMITER: {
    limit(options: { key: string }): Promise<{ success: boolean }>;
  };
}

interface CoachRequest {
  message?: unknown;
}

const MAX_PROMPT_CHARS = 2_000;
const MAX_BODY_CHARS = 10_000;
const OPENAI_TIMEOUT_MS = 45_000;

const FINANCE_COACH_INSTRUCTIONS = [
  "You are the educational finance coach inside a personal finance app in India.",
  "Give general budgeting, saving, debt-management and financial-literacy guidance.",
  "Do not claim access to bank accounts, CIBIL, credit bureaus, live lender offers or current rates.",
  "Never invent user financial facts, credit scores, fees, rates or guaranteed outcomes.",
  "Clearly state uncertainty; encourage checking loan terms and official sources before decisions.",
  "Do not ask for passwords, OTPs, full card numbers, full bank-account numbers or identity documents.",
  "Do not provide instructions to evade lawful debt obligations or misrepresent information.",
  "Keep the answer practical, concise and non-judgmental."
].join("\n");

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff"
    }
  });
}

function hasAuthConfiguration(env: Env): boolean {
  return Boolean(
    env.AUTH_JWKS_URL &&
    env.AUTH_ISSUER &&
    env.AUTH_AUDIENCE
  );
}

async function authenticate(request: Request, env: Env): Promise<string | null> {
  if (!hasAuthConfiguration(env)) {
    throw new Error("AUTH_NOT_CONFIGURED");
  }

  const authorization = request.headers.get("authorization") ?? "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) return null;

  const jwksUrl = new URL(env.AUTH_JWKS_URL!);
  if (jwksUrl.protocol !== "https:") {
    throw new Error("AUTH_JWKS_URL_MUST_USE_HTTPS");
  }

  const jwks = createRemoteJWKSet(jwksUrl);
  const { payload } = await jwtVerify(match[1], jwks, {
    issuer: env.AUTH_ISSUER!,
    audience: env.AUTH_AUDIENCE!
  });

  if (typeof payload.sub !== "string" || payload.sub.length === 0) {
    return null;
  }

  return payload.sub;
}

async function handleCoach(request: Request, env: Env): Promise<Response> {
  if (!hasAuthConfiguration(env)) {
    return json({
      error: "service_not_configured",
      message: "Authentication is not configured for this service yet."
    }, 503);
  }

  if (!env.OPENAI_API_KEY) {
    return json({
      error: "service_not_configured",
      message: "The AI coach is not configured yet."
    }, 503);
  }

  let userId: string | null;
  try {
    userId = await authenticate(request, env);
  } catch (error) {
    if (error instanceof Error && error.message.startsWith("AUTH_")) {
      console.error("Authentication configuration error:", error.message);
      return json({ error: "service_not_configured", message: "Authentication is not configured correctly." }, 503);
    }
    console.warn("Bearer token verification failed.");
    return json({ error: "unauthorized", message: "Sign in to use the finance coach." }, 401);
  }

  if (!userId) {
    return json({ error: "unauthorized", message: "Sign in to use the finance coach." }, 401);
  }

  let rateLimitResult: { success: boolean };
  try {
    rateLimitResult = await env.RATE_LIMITER.limit({ key: userId });
  } catch {
    console.error("Rate limiter unavailable; rejecting request safely.");
    return json({ error: "temporarily_unavailable", message: "The finance coach is temporarily unavailable." }, 503);
  }

  if (!rateLimitResult.success) {
    return json({
      error: "rate_limited",
      message: "Too many coach requests. Please wait a minute and try again."
    }, 429);
  }

  const declaredLength = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(declaredLength) && declaredLength > MAX_BODY_CHARS) {
    return json({ error: "invalid_request", message: "The request is too large." }, 413);
  }

  let rawBody: string;
  try {
    rawBody = await request.text();
  } catch {
    return json({ error: "invalid_request", message: "Could not read the request." }, 400);
  }

  if (rawBody.length > MAX_BODY_CHARS) {
    return json({ error: "invalid_request", message: "The request is too large." }, 413);
  }

  let body: CoachRequest;
  try {
    body = JSON.parse(rawBody) as CoachRequest;
  } catch {
    return json({ error: "invalid_json", message: "Send a valid JSON request." }, 400);
  }

  if (typeof body.message !== "string" || body.message.trim().length === 0) {
    return json({ error: "invalid_request", message: "Enter a finance question." }, 400);
  }

  const message = body.message.trim();
  if (message.length > MAX_PROMPT_CHARS) {
    return json({
      error: "invalid_request",
      message: `Keep your question under ${MAX_PROMPT_CHARS} characters.`
    }, 400);
  }

  try {
    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        authorization: `Bearer ${env.OPENAI_API_KEY}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        model: "gpt-4.1-mini",
        instructions: FINANCE_COACH_INSTRUCTIONS,
        input: message,
        max_output_tokens: 500
      }),
      signal: AbortSignal.timeout(OPENAI_TIMEOUT_MS)
    });

    if (!response.ok) {
      // Do not log the prompt, token, API key, or provider response body.
      console.error("OpenAI provider request failed.", { status: response.status });
      return json({
        error: "provider_unavailable",
        message: "The finance coach is temporarily unavailable. Your local finance tracking still works."
      }, 502);
    }

    const result = await response.json() as { output_text?: unknown };
    const answer = typeof result.output_text === "string" ? result.output_text.trim() : "";
    if (!answer) {
      console.error("OpenAI provider returned no output text.");
      return json({
        error: "provider_unavailable",
        message: "I couldn't create an answer. Please try again."
      }, 502);
    }

    return json({
      answer,
      provider: "OpenAI",
      model: "gpt-4.1-mini",
      generatedAt: new Date().toISOString(),
      liveBureauConnected: false
    });
  } catch (error) {
    console.error("Finance coach request failed.", {
      errorType: error instanceof Error ? error.name : "unknown"
    });
    return json({
      error: "provider_unavailable",
      message: "The finance coach is temporarily unavailable. Your local finance tracking still works."
    }, 502);
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ status: "ok", service: "open-finance-ai-api" });
    }

    if (url.pathname !== "/v1/finance-coach") {
      return json({ error: "not_found", message: "Endpoint not found." }, 404);
    }

    if (request.method !== "POST") {
      return json({ error: "method_not_allowed", message: "Use POST for this endpoint." }, 405);
    }

    return handleCoach(request, env);
  }
};
