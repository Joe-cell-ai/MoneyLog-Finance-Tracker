import OpenAI from "openai";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";

initializeApp();

const db = getFirestore();
const openAiApiKey = defineSecret("OPENAI_API_KEY");
const REGION = "asia-south1";
const MAX_PROMPT_CHARS = 2_000;
const MAX_REQUESTS_PER_MINUTE = 5;

type CoachRequest = { message?: unknown };

async function enforcePerUserRateLimit(uid: string): Promise<void> {
  const now = Date.now();
  const minuteBucket = Math.floor(now / 60_000);
  const ref = db.collection("_privateRateLimits").doc(`${uid}_${minuteBucket}`);

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const count = snapshot.exists ? Number(snapshot.get("count") ?? 0) : 0;
    if (count >= MAX_REQUESTS_PER_MINUTE) {
      throw new HttpsError(
        "resource-exhausted",
        "Too many coach requests. Please wait a minute and try again.",
      );
    }
    transaction.set(ref, {
      uid,
      count: count + 1,
      bucketStart: Timestamp.fromMillis(minuteBucket * 60_000),
    }, { merge: true });
  });
}

/**
 * A secure starting point, not a deployed/connected service yet.
 * Requires Firebase Authentication, App Check, and the server-only OpenAI secret.
 */
export const financeCoach = onCall(
  {
    region: REGION,
    enforceAppCheck: true,
    secrets: [openAiApiKey],
    timeoutSeconds: 60,
    memory: "256MiB",
    maxInstances: 2,
  },
  async (request) => {
    if (!request.auth?.uid) {
      throw new HttpsError("unauthenticated", "Sign in before using the finance coach.");
    }

    const message = (request.data as CoachRequest | undefined)?.message;
    if (typeof message !== "string" || message.trim().length === 0) {
      throw new HttpsError("invalid-argument", "Enter a finance question.");
    }
    if (message.length > MAX_PROMPT_CHARS) {
      throw new HttpsError(
        "invalid-argument",
        `Keep your question under ${MAX_PROMPT_CHARS} characters.`,
      );
    }

    await enforcePerUserRateLimit(request.auth.uid);

    const apiKey = openAiApiKey.value();
    if (!apiKey) {
      logger.error("OPENAI_API_KEY secret is not configured.");
      throw new HttpsError("failed-precondition", "The AI coach is not configured yet.");
    }

    try {
      const client = new OpenAI({ apiKey, timeout: 45_000, maxRetries: 1 });
      const response = await client.responses.create({
        model: "gpt-4.1-mini",
        instructions: [
          "You are the educational finance coach inside a personal finance app in India.",
          "Give general budgeting, saving, debt-management and financial-literacy guidance.",
          "Do not claim access to bank accounts, CIBIL, credit bureaus, live lender offers or current rates.",
          "Never invent user financial facts, credit scores, fees, rates or guaranteed outcomes.",
          "Clearly state uncertainty; encourage checking loan terms and official sources before decisions.",
          "Do not ask for passwords, OTPs, full card numbers, full bank-account numbers or identity documents.",
          "Do not provide instructions to evade lawful debt obligations or misrepresent information.",
          "Keep the answer practical, concise and non-judgmental.",
        ].join("\n"),
        input: message.trim(),
        max_output_tokens: 500,
      });

      return {
        answer: response.output_text || "I couldn't create an answer. Please try again.",
        provider: "OpenAI",
        model: "gpt-4.1-mini",
        generatedAt: new Date().toISOString(),
        liveBureauConnected: false,
      };
    } catch (error) {
      logger.error("Finance coach provider request failed.", {
        uid: request.auth.uid,
        errorType: error instanceof Error ? error.name : "unknown",
      });
      throw new HttpsError(
        "unavailable",
        "The finance coach is temporarily unavailable. Your local finance tracking still works.",
      );
    }
  },
);
