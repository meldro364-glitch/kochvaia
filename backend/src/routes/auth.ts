import { Hono } from "hono";
import type { AppVariables, Env } from "../types.ts";
import { verifyGoogleIdToken } from "../auth/google.ts";
import { mintKidSession, mintParentSession } from "../auth/session.ts";
import { consumeJoinCode, createJoinCode, peekJoinCode } from "../auth/qr.ts";
import { newId } from "../lib/ids.ts";
import { badRequest, forbidden, notFound } from "../lib/errors.ts";
import { requireParent, requireSession } from "../middleware/auth.ts";

export const authRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

/**
 * POST /auth/google
 * Body: { idToken: string, inviteCode?: string, familyTz?: string }
 *
 * Behavior:
 *   - existing parent (matched by google_sub) → log them in.
 *   - new parent + valid inviteCode (kind=parent) → join that family.
 *   - new parent + no invite → create a fresh family + parent.
 */
authRoutes.post("/google", async (c) => {
  const body = await c.req.json<{
    idToken?: string;
    inviteCode?: string;
    familyTz?: string;
    familyName?: string;
    displayName?: string;
  }>();
  if (!body?.idToken) throw badRequest("missing_id_token");

  const claims = await verifyGoogleIdToken(body.idToken, c.env.GOOGLE_OAUTH_CLIENT_ID);
  const googleSub = claims.sub;
  const email = claims.email ?? null;
  const displayName = body.displayName?.trim() || claims.name || email || "Parent";

  const existing = await c.env.DB.prepare(
    "SELECT id, family_id FROM parents WHERE google_sub = ?",
  )
    .bind(googleSub)
    .first<{ id: string; family_id: string }>();

  if (existing) {
    const session = await mintParentSession(c.env, existing.id);
    return c.json({
      sessionToken: session.token,
      expiresAt: session.expiresAt,
      role: "parent" as const,
      parentId: existing.id,
      familyId: existing.family_id,
    });
  }

  // New parent. Either join via invite or create a family.
  let familyId: string;
  if (body.inviteCode) {
    const consumed = await consumeJoinCode(c.env, body.inviteCode);
    if (consumed.kind !== "parent") throw badRequest("invite_not_for_parent");
    familyId = consumed.familyId;
  } else {
    familyId = newId();
    const now = Date.now();
    const tz = (body.familyTz ?? "UTC").trim() || "UTC";
    const familyName = (body.familyName ?? `${displayName}'s family`).slice(0, 80);
    await c.env.DB.prepare(
      "INSERT INTO families (id, name, tz, created_at) VALUES (?, ?, ?, ?)",
    )
      .bind(familyId, familyName, tz, now)
      .run();
  }

  const parentId = newId();
  const now = Date.now();
  await c.env.DB.prepare(
    "INSERT INTO parents (id, family_id, google_sub, email, display_name, created_at) VALUES (?, ?, ?, ?, ?, ?)",
  )
    .bind(parentId, familyId, googleSub, email, displayName, now)
    .run();

  const session = await mintParentSession(c.env, parentId);
  return c.json({
    sessionToken: session.token,
    expiresAt: session.expiresAt,
    role: "parent" as const,
    parentId,
    familyId,
  });
});

/**
 * POST /auth/qr/create  (parent only)
 * Body: { kind: 'parent' | 'kid', kidId?: string }
 */
authRoutes.post("/qr/create", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");
  const body = await c.req.json<{ kind?: "parent" | "kid"; kidId?: string }>();
  if (body?.kind !== "parent" && body?.kind !== "kid") {
    throw badRequest("invalid_kind");
  }
  const created = await createJoinCode(c.env, {
    familyId: session.familyId,
    createdByParentId: session.parentId,
    kind: body.kind,
    kidId: body.kidId ?? null,
  });
  return c.json(created);
});

/**
 * POST /auth/qr/redeem
 * Body: { code: string, deviceLabel?: string }
 *
 * - kind=kid → mints a kid session, returns it.
 * - kind=parent → does NOT consume here; client must follow up with /auth/google
 *   including this code as `inviteCode`. We just peek for UX feedback.
 */
authRoutes.post("/qr/redeem", async (c) => {
  const body = await c.req.json<{ code?: string; deviceLabel?: string }>();
  if (!body?.code) throw badRequest("missing_code");

  const peek = await peekJoinCode(c.env, body.code);
  if (!peek) throw notFound("code_invalid_or_expired");

  if (peek.kind === "parent") {
    return c.json({
      role: "parent" as const,
      requiresGoogleSignIn: true,
      familyId: peek.familyId,
    });
  }

  // kind=kid → consume + mint session.
  const consumed = await consumeJoinCode(c.env, body.code);
  if (!consumed.kidId) throw badRequest("kid_id_missing_on_code");
  const session = await mintKidSession(c.env, consumed.kidId, body.deviceLabel ?? null);
  return c.json({
    sessionToken: session.token,
    expiresAt: session.expiresAt,
    role: "kid" as const,
    kidId: consumed.kidId,
    familyId: consumed.familyId,
  });
});

/**
 * POST /auth/logout — revokes the current session token.
 */
authRoutes.post("/logout", requireSession, async (c) => {
  const session = c.get("session");
  const now = Date.now();
  const table = session.role === "parent" ? "parent_sessions" : "kid_sessions";
  await c.env.DB.prepare(
    `UPDATE ${table} SET revoked_at = ? WHERE token_hash = ?`,
  )
    .bind(now, session.sessionTokenHash)
    .run();
  return c.json({ ok: true });
});
