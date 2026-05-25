import { env } from "cloudflare:test";
import app from "../src/index.ts";
import { mintKidSession, mintParentSession } from "../src/auth/session.ts";
import { newId } from "../src/lib/ids.ts";

declare module "cloudflare:test" {
  interface ProvidedEnv {
    DB: D1Database;
    GOOGLE_OAUTH_CLIENT_ID: string;
    ENVIRONMENT: string;
    TEST_MIGRATIONS: D1Migration[];
  }
}

export async function resetDb(): Promise<void> {
  // Migrations are applied via setup.ts (beforeAll).
  // Order matters: leaf tables first.
  const tables = [
    "view_checkpoints",
    "join_codes",
    "deductions",
    "stars",
    "kid_sessions",
    "parent_sessions",
    "kids",
    "parents",
    "families",
  ];
  for (const t of tables) {
    await env.DB.prepare(`DELETE FROM ${t}`).run();
  }
}

export interface SeededFamily {
  familyId: string;
  tz: string;
  parentId: string;
  parentToken: string;
  kidIds: string[];
  kidTokens: string[]; // one token per kid (paired device)
}

export async function seedFamily(opts: {
  tz?: string;
  kidCount?: number;
} = {}): Promise<SeededFamily> {
  const tz = opts.tz ?? "UTC";
  const kidCount = opts.kidCount ?? 1;
  const now = Date.now();

  const familyId = newId();
  await env.DB.prepare(
    "INSERT INTO families (id, name, tz, created_at) VALUES (?, ?, ?, ?)",
  )
    .bind(familyId, "Test Family", tz, now)
    .run();

  const parentId = newId();
  await env.DB.prepare(
    "INSERT INTO parents (id, family_id, google_sub, email, display_name, created_at) VALUES (?, ?, ?, ?, ?, ?)",
  )
    .bind(parentId, familyId, `sub_${parentId}`, "p@example.com", "Parent", now)
    .run();
  const parentSession = await mintParentSession(env, parentId);

  const kidIds: string[] = [];
  const kidTokens: string[] = [];
  for (let i = 0; i < kidCount; i++) {
    const kidId = newId();
    await env.DB.prepare(
      "INSERT INTO kids (id, family_id, display_name, avatar_emoji, avatar_color, created_at) VALUES (?, ?, ?, ?, ?, ?)",
    )
      .bind(kidId, familyId, `Kid ${i + 1}`, "⭐", "#FFB347", now + i)
      .run();
    kidIds.push(kidId);
    const ks = await mintKidSession(env, kidId, "test-device");
    kidTokens.push(ks.token);
  }

  return { familyId, tz, parentId, parentToken: parentSession.token, kidIds, kidTokens };
}

export async function callApp(
  method: string,
  path: string,
  init: { token?: string; body?: unknown } = {},
): Promise<Response> {
  const headers: Record<string, string> = {};
  if (init.token) headers.authorization = `Bearer ${init.token}`;
  if (init.body !== undefined) headers["content-type"] = "application/json";
  return app.fetch(
    new Request(`http://test.local${path}`, {
      method,
      headers,
      body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
    }),
    env,
  );
}
