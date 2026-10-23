import type { D1Database, R2Bucket } from "@cloudflare/workers-types";

export interface StorageEnv {
  DB: D1Database;
  FILES: R2Bucket;
}

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const ALLOWED_FILE_TYPES = new Set([
  "application/pdf",
  "image/jpeg",
  "image/png",
  "image/webp"
]);

function response(data: unknown, status = 200, headers: HeadersInit = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      ...headers
    }
  });
}

function error(message: string, status: number): Response {
  return response({ error: message }, status);
}

async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  const length = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(length) && length > 32_000) return null;
  try {
    const value: unknown = await request.json();
    if (!value || typeof value !== "object" || Array.isArray(value)) return null;
    return value as Record<string, unknown>;
  } catch {
    return null;
  }
}

function textField(value: unknown, max = 120): string | null {
  if (typeof value !== "string") return null;
  const cleaned = value.trim();
  return cleaned.length > 0 && cleaned.length <= max ? cleaned : null;
}

function amountField(value: unknown): number | null {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0 || value > 1_000_000_000) return null;
  return value;
}

function validDate(value: unknown): string | null {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const date = new Date(value + "T00:00:00Z");
  return Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value ? null : value;
}

async function listRows(env: StorageEnv, table: string, userId: string): Promise<unknown[]> {
  // Table names are selected only from the fixed allowlist in the router below.
  const result = await env.DB.prepare(`SELECT * FROM ${table} WHERE user_id = ? ORDER BY created_at DESC LIMIT 500`)
    .bind(userId).all();
  return result.results;
}

async function createRecord(request: Request, env: StorageEnv, userId: string, kind: string): Promise<Response> {
  const body = await readJson(request);
  if (!body) return error("invalid_json", 400);
  const now = new Date().toISOString();

  if (kind === "transactions") {
    const amount = amountField(body.amount);
    const categoryId = textField(body.categoryId, 80);
    const type = body.type;
    const date = validDate(body.date);
    const note = typeof body.note === "string" ? body.note.trim().slice(0, 500) : "";
    if (amount === null || !categoryId || !date || (type !== "income" && type !== "expense")) return error("invalid_transaction", 400);
    const category = await env.DB.prepare("SELECT id FROM categories WHERE id = ? AND user_id = ?")
      .bind(categoryId, userId).first();
    if (!category) return error("category_not_found", 400);
    const id = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO transactions (id,user_id,type,amount,category_id,note,transaction_date,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)")
      .bind(id,userId,type,amount,categoryId,note,date,now,now).run();
    return response({ id, type, amount, categoryId, note, date, createdAt: now }, 201);
  }

  if (kind === "categories") {
    const name = textField(body.name, 60);
    const type = body.type;
    if (!name || (type !== "income" && type !== "expense" && type !== "both")) return error("invalid_category", 400);
    const id = crypto.randomUUID();
    try {
      await env.DB.prepare("INSERT INTO categories (id,user_id,name,type,created_at) VALUES (?,?,?,?,?)")
        .bind(id,userId,name,type,now).run();
    } catch {
      return error("category_already_exists", 409);
    }
    return response({ id, name, type, createdAt: now }, 201);
  }

  if (kind === "budgets") {
    const categoryId = textField(body.categoryId, 80);
    const amount = amountField(body.amount);
    const month = typeof body.month === "string" && /^\d{4}-\d{2}$/.test(body.month) ? body.month : null;
    if (!categoryId || amount === null || !month) return error("invalid_budget", 400);
    const category = await env.DB.prepare("SELECT id FROM categories WHERE id = ? AND user_id = ?")
      .bind(categoryId,userId).first();
    if (!category) return error("category_not_found", 400);
    const id = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO budgets (id,user_id,category_id,amount,budget_month,created_at,updated_at) VALUES (?,?,?,?,?,?,?) ON CONFLICT(user_id,category_id,budget_month) DO UPDATE SET amount=excluded.amount,updated_at=excluded.updated_at")
      .bind(id,userId,categoryId,amount,month,now,now).run();
    const saved = await env.DB.prepare("SELECT * FROM budgets WHERE user_id=? AND category_id=? AND budget_month=?")
      .bind(userId,categoryId,month).first();
    return response(saved, 201);
  }

  if (kind === "loans") {
    const name = textField(body.name, 100);
    const principal = amountField(body.principal);
    const outstanding = amountField(body.outstanding);
    const emi = amountField(body.emi);
    const dueDate = body.dueDate == null || body.dueDate === "" ? null : validDate(body.dueDate);
    if (!name || principal === null || outstanding === null || emi === null || (body.dueDate && !dueDate)) return error("invalid_loan", 400);
    const id = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO loans (id,user_id,name,principal,outstanding,emi,due_date,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)")
      .bind(id,userId,name,principal,outstanding,emi,dueDate,now,now).run();
    return response({ id, name, principal, outstanding, emi, dueDate, createdAt: now }, 201);
  }

  if (kind === "savings") {
    const name = textField(body.name, 100);
    const amount = amountField(body.amount);
    const target = body.target == null ? null : amountField(body.target);
    if (!name || amount === null || (body.target != null && target === null)) return error("invalid_savings_goal", 400);
    const id = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO savings_goals (id,user_id,name,amount,target,created_at,updated_at) VALUES (?,?,?,?,?,?,?)")
      .bind(id,userId,name,amount,target,now,now).run();
    return response({ id, name, amount, target, createdAt: now }, 201);
  }

  return error("not_found", 404);
}

async function handleFiles(request: Request, env: StorageEnv, userId: string, path: string): Promise<Response> {
  const parts = path.split("/").filter(Boolean);
  if (parts.length === 2 && request.method === "GET") {
    const result = await env.DB.prepare("SELECT id,file_name,content_type,size_bytes,created_at FROM files WHERE user_id=? ORDER BY created_at DESC LIMIT 100")
      .bind(userId).all();
    return response(result.results);
  }

  if (parts.length === 2 && request.method === "POST") {
    const declaredLength = Number(request.headers.get("content-length") ?? "0");
    if (declaredLength > MAX_FILE_BYTES + 100_000) return error("file_too_large", 413);
    let form: FormData;
    try { form = await request.formData(); } catch { return error("invalid_multipart", 400); }
    const item = form.get("file");
    if (!(item instanceof File)) return error("file_required", 400);
    if (item.size < 1 || item.size > MAX_FILE_BYTES) return error("invalid_file_size", 413);
    if (!ALLOWED_FILE_TYPES.has(item.type)) return error("unsupported_file_type", 415);
    const id = crypto.randomUUID();
    const objectKey = `${userId}/${id}`;
    const safeName = item.name.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 120) || "upload";
    const bytes = await item.arrayBuffer();
    await env.FILES.put(objectKey, bytes, {
      httpMetadata: { contentType: item.type, cacheControl: "private, no-store" },
      customMetadata: { ownerId: userId, originalName: safeName }
    });
    const now = new Date().toISOString();
    try {
      await env.DB.prepare("INSERT INTO files (id,user_id,object_key,file_name,content_type,size_bytes,created_at) VALUES (?,?,?,?,?,?,?)")
        .bind(id,userId,objectKey,safeName,item.type,item.size,now).run();
    } catch {
      await env.FILES.delete(objectKey);
      return error("file_metadata_failed", 500);
    }
    return response({ id, fileName: safeName, contentType: item.type, sizeBytes: item.size, createdAt: now }, 201);
  }

  if (parts.length === 3) {
    const id = parts[2];
    const row = await env.DB.prepare("SELECT id,user_id,object_key,file_name,content_type,size_bytes FROM files WHERE id=? AND user_id=?")
      .bind(id,userId).first<{ id: string; user_id: string; object_key: string; file_name: string; content_type: string; size_bytes: number }>();
    if (!row) return error("file_not_found", 404);

    if (request.method === "GET") {
      const object = await env.FILES.get(row.object_key);
      if (!object) return error("file_not_found", 404);
      return new Response(object.body, {
        headers: {
          "content-type": row.content_type,
          "content-length": String(row.size_bytes),
          "content-disposition": `attachment; filename="${row.file_name.replace(/["\\\\]/g, "_")}"`,
          "cache-control": "private, no-store",
          "x-content-type-options": "nosniff"
        }
      });
    }
    if (request.method === "DELETE") {
      await env.FILES.delete(row.object_key);
      await env.DB.prepare("DELETE FROM files WHERE id=? AND user_id=?").bind(id,userId).run();
      return response({ deleted: true });
    }
  }
  return error("method_not_allowed_or_not_found", 404);
}

export async function handleStorage(request: Request, env: StorageEnv, userId: string): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname;
  if (path.startsWith("/v1/files")) return handleFiles(request, env, userId, path);

  const match = path.match(/^\/v1\/(transactions|categories|budgets|loans|savings)(?:\/([^/]+))?$/);
  if (!match) return error("not_found", 404);
  const kind = match[1];
  const id = match[2];

  if (request.method === "GET" && !id) {
    try {
      const rows = await listRows(env, kind === "savings" ? "savings_goals" : kind, userId);
      return response(rows);
    } catch {
      return error("storage_unavailable", 503);
    }
  }
  if (request.method === "POST" && !id) {
    try { return await createRecord(request, env, userId, kind); }
    catch { return error("storage_unavailable", 503); }
  }

  if (kind === "transactions" && id && request.method === "DELETE") {
    try {
      const result = await env.DB.prepare("DELETE FROM transactions WHERE id=? AND user_id=?").bind(id,userId).run();
      if (!result.meta.changes) return error("record_not_found", 404);
      return response({ deleted: true });
    } catch { return error("storage_unavailable", 503); }
  }

  return error("method_not_allowed_or_not_found", 404);
}
