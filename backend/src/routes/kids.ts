import { Hono } from "hono";
import type { AppVariables, Env } from "../types.ts";
import { requireParent, requireSession } from "../middleware/auth.ts";
import { badRequest, forbidden, notFound } from "../lib/errors.ts";
import { newId } from "../lib/ids.ts";

export const kidsRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

/** GET /me — session info. */
kidsRoutes.get("/me", requireSession, async (c) => {
  const session = c.get("session");
  const family = await c.env.DB.prepare(
    "SELECT id, name, tz FROM families WHERE id = ?",
  )
    .bind(session.familyId)
    .first<{ id: string; name: string; tz: string }>();
  if (!family) throw notFound("family_not_found");

  if (session.role === "parent") {
    const parent = await c.env.DB.prepare(
      "SELECT id, email, display_name FROM parents WHERE id = ?",
    )
      .bind(session.parentId)
      .first<{ id: string; email: string | null; display_name: string }>();
    return c.json({
      role: "parent" as const,
      family,
      parent,
    });
  }
  const kid = await c.env.DB.prepare(
    "SELECT id, display_name, avatar_emoji, avatar_color FROM kids WHERE id = ?",
  )
    .bind(session.kidId)
    .first<{ id: string; display_name: string; avatar_emoji: string; avatar_color: string }>();
  return c.json({
    role: "kid" as const,
    family,
    kid,
  });
});

/** GET /kids — visible to all parents + kids in the family. */
kidsRoutes.get("/kids", requireSession, async (c) => {
  const session = c.get("session");
  const { results } = await c.env.DB.prepare(
    `SELECT id, display_name AS displayName, avatar_emoji AS avatarEmoji,
            avatar_color AS avatarColor, created_at AS createdAt
       FROM kids
       WHERE family_id = ? AND archived_at IS NULL
       ORDER BY created_at ASC`,
  )
    .bind(session.familyId)
    .all();
  return c.json({ kids: results });
});

/** POST /kids — add a kid (parent only). */
kidsRoutes.post("/kids", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");
  const body = await c.req.json<{
    displayName?: string;
    avatarEmoji?: string;
    avatarColor?: string;
  }>();
  const displayName = body?.displayName?.trim();
  if (!displayName) throw badRequest("display_name_required");
  if (displayName.length > 40) throw badRequest("display_name_too_long");

  const id = newId();
  const now = Date.now();
  const emoji = body.avatarEmoji?.trim() || "⭐";
  const color = body.avatarColor?.trim() || "#FFB347";
  if (emoji.length > 8) throw badRequest("emoji_too_long");
  if (!/^#[0-9A-Fa-f]{6}$/.test(color)) throw badRequest("invalid_color");

  await c.env.DB.prepare(
    `INSERT INTO kids (id, family_id, display_name, avatar_emoji, avatar_color, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  )
    .bind(id, session.familyId, displayName, emoji, color, now)
    .run();
  return c.json({ id, displayName, avatarEmoji: emoji, avatarColor: color, createdAt: now }, 201);
});

/** PATCH /kids/:id — rename / recolor (parent only). */
kidsRoutes.patch("/kids/:id", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");
  const kidId = c.req.param("id");
  const body = await c.req.json<{
    displayName?: string;
    avatarEmoji?: string;
    avatarColor?: string;
  }>();

  const existing = await c.env.DB.prepare(
    "SELECT id FROM kids WHERE id = ? AND family_id = ? AND archived_at IS NULL",
  )
    .bind(kidId, session.familyId)
    .first<{ id: string }>();
  if (!existing) throw notFound("kid_not_found");

  const sets: string[] = [];
  const binds: unknown[] = [];
  if (body.displayName !== undefined) {
    const name = body.displayName.trim();
    if (!name) throw badRequest("display_name_required");
    if (name.length > 40) throw badRequest("display_name_too_long");
    sets.push("display_name = ?");
    binds.push(name);
  }
  if (body.avatarEmoji !== undefined) {
    const e = body.avatarEmoji.trim();
    if (!e || e.length > 8) throw badRequest("invalid_emoji");
    sets.push("avatar_emoji = ?");
    binds.push(e);
  }
  if (body.avatarColor !== undefined) {
    if (!/^#[0-9A-Fa-f]{6}$/.test(body.avatarColor)) throw badRequest("invalid_color");
    sets.push("avatar_color = ?");
    binds.push(body.avatarColor);
  }
  if (sets.length === 0) return c.json({ ok: true });
  binds.push(kidId, session.familyId);
  await c.env.DB.prepare(
    `UPDATE kids SET ${sets.join(", ")} WHERE id = ? AND family_id = ?`,
  )
    .bind(...binds)
    .run();
  return c.json({ ok: true });
});

/** DELETE /kids/:id — soft-archive + revoke that kid's sessions. */
kidsRoutes.delete("/kids/:id", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");
  const kidId = c.req.param("id");
  const now = Date.now();
  const res = await c.env.DB.prepare(
    "UPDATE kids SET archived_at = ? WHERE id = ? AND family_id = ? AND archived_at IS NULL",
  )
    .bind(now, kidId, session.familyId)
    .run();
  if (!res.meta.changes) throw notFound("kid_not_found");
  await c.env.DB.prepare(
    "UPDATE kid_sessions SET revoked_at = ? WHERE kid_id = ? AND revoked_at IS NULL",
  )
    .bind(now, kidId)
    .run();
  return c.json({ ok: true });
});
