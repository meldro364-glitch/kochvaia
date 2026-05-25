/**
 * POST /kids/:id/seen   (kid session only)
 *
 * Returns events that happened since the last call from this device for this
 * kid, then advances the checkpoint. The Android app uses the response to play
 * arrival animations (new stars sliding onto their day cells, used days
 * desaturating, etc.).
 *
 * Checkpoint is a (timestamp, id) high-water-mark so events sharing the same
 * millisecond aren't lost or double-counted.
 */

import { Hono } from "hono";
import type { AppVariables, Env } from "../types.ts";
import { requireKid } from "../middleware/auth.ts";
import { forbidden, notFound } from "../lib/errors.ts";

export const seenRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

seenRoutes.post("/kids/:id/seen", requireKid, async (c) => {
  const session = c.get("session");
  if (session.role !== "kid") throw forbidden("kid_only");
  const viewedKidId = c.req.param("id");

  // Family-scope check.
  const kid = await c.env.DB.prepare(
    "SELECT id FROM kids WHERE id = ? AND family_id = ? AND archived_at IS NULL",
  )
    .bind(viewedKidId, session.familyId)
    .first<{ id: string }>();
  if (!kid) throw notFound("kid_not_found");

  const checkpoint = await c.env.DB.prepare(
    `SELECT last_seen_star_awarded_at   AS sa,
            last_seen_star_id           AS si,
            last_seen_deduction_at      AS da,
            last_seen_deduction_id      AS di
       FROM view_checkpoints
       WHERE session_token_hash = ? AND viewed_kid_id = ?`,
  )
    .bind(session.sessionTokenHash, viewedKidId)
    .first<{ sa: number; si: string; da: number; di: string }>();

  // First visit: silently catch up to the newest existing star/deduction. The
  // kid sees the current state but no animations for old events.
  if (!checkpoint) {
    const newestStar = await c.env.DB.prepare(
      `SELECT awarded_at AS ts, id FROM stars
         WHERE kid_id = ?
         ORDER BY awarded_at DESC, id DESC LIMIT 1`,
    )
      .bind(viewedKidId)
      .first<{ ts: number; id: string }>();
    const newestDed = await c.env.DB.prepare(
      `SELECT performed_at AS ts, id FROM deductions
         WHERE kid_id = ?
         ORDER BY performed_at DESC, id DESC LIMIT 1`,
    )
      .bind(viewedKidId)
      .first<{ ts: number; id: string }>();
    const sa = newestStar?.ts ?? 0;
    const si = newestStar?.id ?? "";
    const da = newestDed?.ts ?? 0;
    const di = newestDed?.id ?? "";
    await c.env.DB.prepare(
      `INSERT INTO view_checkpoints
         (session_token_hash, viewed_kid_id,
          last_seen_star_awarded_at, last_seen_star_id,
          last_seen_deduction_at, last_seen_deduction_id)
       VALUES (?, ?, ?, ?, ?, ?)`,
    )
      .bind(session.sessionTokenHash, viewedKidId, sa, si, da, di)
      .run();
    return c.json({
      newStars: [],
      newDeductions: [],
      checkpoint: { lastSeenStarAwardedAt: sa, lastSeenDeductionAt: da },
      firstVisit: true,
    });
  }

  // (awarded_at, id) > (sa, si) — lexicographic, breaks same-millisecond ties.
  const { results: newStars } = await c.env.DB.prepare(
    `SELECT id, earned_date AS date, awarded_at AS awardedAt
       FROM stars
       WHERE kid_id = ?
         AND (awarded_at > ? OR (awarded_at = ? AND id > ?))
       ORDER BY awarded_at ASC, id ASC`,
  )
    .bind(viewedKidId, checkpoint.sa, checkpoint.sa, checkpoint.si)
    .all<{ id: string; date: string; awardedAt: number }>();

  const { results: newDeductions } = await c.env.DB.prepare(
    `SELECT id, count, performed_at AS performedAt, reason
       FROM deductions
       WHERE kid_id = ?
         AND (performed_at > ? OR (performed_at = ? AND id > ?))
       ORDER BY performed_at ASC, id ASC`,
  )
    .bind(viewedKidId, checkpoint.da, checkpoint.da, checkpoint.di)
    .all<{ id: string; count: number; performedAt: number; reason: string | null }>();

  let sa = checkpoint.sa;
  let si = checkpoint.si;
  if (newStars.length) {
    const last = newStars[newStars.length - 1]!;
    sa = last.awardedAt;
    si = last.id;
  }
  let da = checkpoint.da;
  let di = checkpoint.di;
  if (newDeductions.length) {
    const last = newDeductions[newDeductions.length - 1]!;
    da = last.performedAt;
    di = last.id;
  }

  if (newStars.length || newDeductions.length) {
    await c.env.DB.prepare(
      `UPDATE view_checkpoints
         SET last_seen_star_awarded_at = ?, last_seen_star_id = ?,
             last_seen_deduction_at = ?, last_seen_deduction_id = ?
         WHERE session_token_hash = ? AND viewed_kid_id = ?`,
    )
      .bind(sa, si, da, di, session.sessionTokenHash, viewedKidId)
      .run();
  }

  return c.json({
    newStars,
    newDeductions,
    checkpoint: {
      lastSeenStarAwardedAt: sa,
      lastSeenDeductionAt: da,
    },
    firstVisit: false,
  });
});
