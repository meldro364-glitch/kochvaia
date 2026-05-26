import { Hono } from "hono";
import type { AppVariables, Env } from "../types.ts";
import { requireParent, requireSession } from "../middleware/auth.ts";
import { badRequest, forbidden, notFound } from "../lib/errors.ts";
import { newId } from "../lib/ids.ts";

export const itemsRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const MAX_NAME_LEN = 80;
const MAX_EMOJI_LEN = 8;
const MAX_COST = 10_000;

/** GET /items — every family session can see the catalog. */
itemsRoutes.get("/items", requireSession, async (c) => {
  const session = c.get("session");
  const { results } = await c.env.DB.prepare(
    `SELECT id, name, cost_stars AS costStars, emoji, created_at AS createdAt
       FROM items
       WHERE family_id = ? AND archived_at IS NULL
       ORDER BY created_at ASC`,
  )
    .bind(session.familyId)
    .all();
  return c.json({ items: results });
});

/** POST /items — parent only. */
itemsRoutes.post("/items", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");

  const body = await c.req.json<{ name?: string; costStars?: number; emoji?: string }>();
  const name = body?.name?.trim();
  if (!name) throw badRequest("name_required");
  if (name.length > MAX_NAME_LEN) throw badRequest("name_too_long");

  const cost = Number.isInteger(body?.costStars) ? Math.trunc(body!.costStars!) : NaN;
  if (!Number.isFinite(cost) || cost < 1 || cost > MAX_COST) {
    throw badRequest("invalid_cost_stars");
  }

  const emoji = body?.emoji?.trim() || "🎁";
  if (emoji.length > MAX_EMOJI_LEN) throw badRequest("emoji_too_long");

  const id = newId();
  const now = Date.now();
  await c.env.DB.prepare(
    `INSERT INTO items (id, family_id, name, cost_stars, emoji, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  )
    .bind(id, session.familyId, name, cost, emoji, now)
    .run();
  return c.json({ id, name, costStars: cost, emoji, createdAt: now }, 201);
});

/** PATCH /items/:id — parent only. Partial updates. */
itemsRoutes.patch("/items/:id", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");
  const itemId = c.req.param("id");
  const body = await c.req.json<{ name?: string; costStars?: number; emoji?: string }>();

  const existing = await c.env.DB.prepare(
    "SELECT id FROM items WHERE id = ? AND family_id = ? AND archived_at IS NULL",
  )
    .bind(itemId, session.familyId)
    .first<{ id: string }>();
  if (!existing) throw notFound("item_not_found");

  const sets: string[] = [];
  const binds: unknown[] = [];
  if (body.name !== undefined) {
    const name = body.name.trim();
    if (!name) throw badRequest("name_required");
    if (name.length > MAX_NAME_LEN) throw badRequest("name_too_long");
    sets.push("name = ?");
    binds.push(name);
  }
  if (body.costStars !== undefined) {
    const cost = Number.isInteger(body.costStars) ? Math.trunc(body.costStars) : NaN;
    if (!Number.isFinite(cost) || cost < 1 || cost > MAX_COST) {
      throw badRequest("invalid_cost_stars");
    }
    sets.push("cost_stars = ?");
    binds.push(cost);
  }
  if (body.emoji !== undefined) {
    const e = body.emoji.trim();
    if (!e || e.length > MAX_EMOJI_LEN) throw badRequest("invalid_emoji");
    sets.push("emoji = ?");
    binds.push(e);
  }
  if (sets.length === 0) return c.json({ ok: true });
  binds.push(itemId, session.familyId);
  await c.env.DB.prepare(
    `UPDATE items SET ${sets.join(", ")} WHERE id = ? AND family_id = ?`,
  )
    .bind(...binds)
    .run();
  return c.json({ ok: true });
});

/** DELETE /items/:id — parent only, soft archive. */
itemsRoutes.delete("/items/:id", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");
  const itemId = c.req.param("id");
  const now = Date.now();
  const res = await c.env.DB.prepare(
    "UPDATE items SET archived_at = ? WHERE id = ? AND family_id = ? AND archived_at IS NULL",
  )
    .bind(now, itemId, session.familyId)
    .run();
  if (!res.meta.changes) throw notFound("item_not_found");
  return c.json({ ok: true });
});
