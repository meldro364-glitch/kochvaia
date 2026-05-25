import { Hono } from "hono";
import type { AppVariables, Env } from "../types.ts";
import { requireParent, requireSession } from "../middleware/auth.ts";
import { badRequest, conflict, forbidden, notFound } from "../lib/errors.ts";
import { newId } from "../lib/ids.ts";
import { isTodayOrPast, isValidDateString } from "../lib/dates.ts";

export const starsRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

/** Helper: load a kid scoped to the caller's family, with the family TZ. */
async function loadKidInFamily(
  c: { env: Env },
  familyId: string,
  kidId: string,
): Promise<{ id: string; tz: string }> {
  const row = await c.env.DB.prepare(
    `SELECT k.id, f.tz
       FROM kids k JOIN families f ON f.id = k.family_id
       WHERE k.id = ? AND k.family_id = ? AND k.archived_at IS NULL`,
  )
    .bind(kidId, familyId)
    .first<{ id: string; tz: string }>();
  if (!row) throw notFound("kid_not_found");
  return row;
}

/**
 * POST /kids/:id/stars   (parent only)
 * Body: { date: 'YYYY-MM-DD' }
 * Today or past only. Idempotent-conflict: returns 409 if that day already has a star.
 */
starsRoutes.post("/kids/:id/stars", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");
  const kidId = c.req.param("id");
  const body = await c.req.json<{ date?: string }>();
  if (!body?.date || !isValidDateString(body.date)) throw badRequest("invalid_date");
  const kid = await loadKidInFamily(c, session.familyId, kidId);
  if (!isTodayOrPast(body.date, kid.tz)) throw badRequest("date_in_future");

  const id = newId();
  const now = Date.now();
  try {
    await c.env.DB.prepare(
      `INSERT INTO stars (id, kid_id, earned_date, awarded_at, awarded_by_parent_id)
       VALUES (?, ?, ?, ?, ?)`,
    )
      .bind(id, kidId, body.date, now, session.parentId)
      .run();
  } catch (e) {
    // Unique-index violation on (kid_id, earned_date).
    const msg = e instanceof Error ? e.message : String(e);
    if (/UNIQUE/i.test(msg)) throw conflict("star_already_awarded_for_date");
    throw e;
  }
  return c.json({ id, kidId, date: body.date, awardedAt: now }, 201);
});

/**
 * DELETE /kids/:id/stars/:date   (parent only)
 * Removes an awarded star for that date, only if it hasn't been used yet.
 */
starsRoutes.delete("/kids/:id/stars/:date", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");
  const kidId = c.req.param("id");
  const date = c.req.param("date");
  if (!isValidDateString(date)) throw badRequest("invalid_date");

  await loadKidInFamily(c, session.familyId, kidId); // family scope check

  const existing = await c.env.DB.prepare(
    "SELECT id, used_at FROM stars WHERE kid_id = ? AND earned_date = ?",
  )
    .bind(kidId, date)
    .first<{ id: string; used_at: number | null }>();
  if (!existing) throw notFound("star_not_found");
  if (existing.used_at !== null) throw conflict("star_already_used");

  await c.env.DB.prepare("DELETE FROM stars WHERE id = ?").bind(existing.id).run();
  return c.json({ ok: true });
});

/**
 * POST /kids/:id/deductions   (parent only)
 * Body: { count: number, reason?: string }
 * Consumes the oldest `count` unused stars. Errors if fewer are available.
 */
starsRoutes.post("/kids/:id/deductions", requireParent, async (c) => {
  const session = c.get("session");
  if (session.role !== "parent") throw forbidden("parent_only");
  const kidId = c.req.param("id");
  const body = await c.req.json<{ count?: number; reason?: string }>();
  const count = Number.isInteger(body?.count) ? Math.trunc(body!.count!) : NaN;
  if (!Number.isFinite(count) || count < 1 || count > 1000) {
    throw badRequest("invalid_count");
  }

  await loadKidInFamily(c, session.familyId, kidId);

  const now = Date.now();
  const batchId = newId();

  // D1 supports batch (atomic) and prepared parameter binding.
  // Step 1: pick the oldest N unused stars.
  const { results } = await c.env.DB.prepare(
    `SELECT id FROM stars
       WHERE kid_id = ? AND used_at IS NULL
       ORDER BY awarded_at ASC, id ASC
       LIMIT ?`,
  )
    .bind(kidId, count)
    .all<{ id: string }>();

  if (results.length < count) {
    throw conflict("insufficient_stars");
  }

  const ids = results.map((r) => r.id);
  const reason = body?.reason?.toString().slice(0, 200) ?? null;

  // Step 2: atomically (a) insert the deduction row, (b) mark exactly those star
  // ids as used in this batch IFF they're still unused. We require the update to
  // affect exactly `count` rows; if not, another deduction beat us — abort.
  const placeholders = ids.map(() => "?").join(",");
  const batchStmts: D1PreparedStatement[] = [
    c.env.DB.prepare(
      `INSERT INTO deductions (id, kid_id, count, reason, performed_at, performed_by_parent_id)
       VALUES (?, ?, ?, ?, ?, ?)`,
    ).bind(batchId, kidId, count, reason, now, session.parentId),
    c.env.DB.prepare(
      `UPDATE stars SET used_at = ?, used_in_batch_id = ?
         WHERE id IN (${placeholders}) AND used_at IS NULL`,
    ).bind(now, batchId, ...ids),
  ];
  const batchResults = await c.env.DB.batch(batchStmts);
  const updateRes = batchResults[1]!;
  if (updateRes.meta.changes !== count) {
    // Race: someone else consumed some of these between SELECT and UPDATE.
    // Roll back the deduction row, then surface a conflict so client can retry.
    await c.env.DB.prepare("DELETE FROM deductions WHERE id = ?").bind(batchId).run();
    await c.env.DB.prepare("UPDATE stars SET used_at = NULL, used_in_batch_id = NULL WHERE used_in_batch_id = ?")
      .bind(batchId)
      .run();
    throw conflict("concurrent_deduction_retry");
  }

  return c.json({ id: batchId, kidId, count, reason, performedAt: now }, 201);
});

/**
 * GET /kids/:id/days?from=YYYY-MM-DD&to=YYYY-MM-DD
 * Returns per-day status across the inclusive range. Status:
 *   'none'  — no star earned that day
 *   'given' — star earned, not yet used
 *   'used'  — star earned and consumed by a deduction
 *
 * Visible to parents and to kid sessions in the same family (any kid can see siblings).
 */
starsRoutes.get("/kids/:id/days", requireSession, async (c) => {
  const session = c.get("session");
  const kidId = c.req.param("id");
  const from = c.req.query("from") ?? "";
  const to = c.req.query("to") ?? "";
  if (!isValidDateString(from) || !isValidDateString(to)) throw badRequest("invalid_range");
  if (from > to) throw badRequest("from_after_to");

  await loadKidInFamily(c, session.familyId, kidId);

  const { results } = await c.env.DB.prepare(
    `SELECT earned_date AS date, used_at AS usedAt
       FROM stars
       WHERE kid_id = ? AND earned_date BETWEEN ? AND ?
       ORDER BY earned_date ASC`,
  )
    .bind(kidId, from, to)
    .all<{ date: string; usedAt: number | null }>();

  // Expand into one row per day in [from, to].
  const map = new Map<string, "given" | "used">();
  for (const r of results) {
    map.set(r.date, r.usedAt === null ? "given" : "used");
  }
  const days: { date: string; status: "none" | "given" | "used" }[] = [];
  const start = new Date(`${from}T00:00:00Z`);
  const end = new Date(`${to}T00:00:00Z`);
  for (let d = start; d <= end; d.setUTCDate(d.getUTCDate() + 1)) {
    const ymd = d.toISOString().slice(0, 10);
    days.push({ date: ymd, status: map.get(ymd) ?? "none" });
  }
  return c.json({ kidId, from, to, days });
});

/**
 * GET /kids/:id/summary
 * { availableStars, totalEarned, totalUsed }
 */
starsRoutes.get("/kids/:id/summary", requireSession, async (c) => {
  const session = c.get("session");
  const kidId = c.req.param("id");
  await loadKidInFamily(c, session.familyId, kidId);

  const row = await c.env.DB.prepare(
    `SELECT
        COUNT(*)                                                AS totalEarned,
        SUM(CASE WHEN used_at IS NULL THEN 1 ELSE 0 END)        AS availableStars,
        SUM(CASE WHEN used_at IS NOT NULL THEN 1 ELSE 0 END)    AS totalUsed
     FROM stars WHERE kid_id = ?`,
  )
    .bind(kidId)
    .first<{ totalEarned: number; availableStars: number; totalUsed: number }>();
  return c.json({
    kidId,
    availableStars: row?.availableStars ?? 0,
    totalEarned: row?.totalEarned ?? 0,
    totalUsed: row?.totalUsed ?? 0,
  });
});
