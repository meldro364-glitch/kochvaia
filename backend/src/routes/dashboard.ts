import { Hono } from "hono";
import type { AppVariables, Env } from "../types.ts";
import { requireSession } from "../middleware/auth.ts";
import { notFound } from "../lib/errors.ts";

export const dashboardRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

/**
 * GET /dashboard — combined snapshot for the calling session.
 *
 * Replaces the N+1 fan-out the client used to do on every screen entry
 * (/me + /kids + /kids/:id/summary for each kid + /items). The shape is
 * the same as those endpoints concatenated, so client repositories can
 * just split the response and seed each per-resource cache.
 *
 * Single trip across the network = ~1 RTT instead of (2 + kidCount).
 */
dashboardRoutes.get("/dashboard", requireSession, async (c) => {
  const session = c.get("session");

  const family = await c.env.DB.prepare(
    "SELECT id, name, tz FROM families WHERE id = ?",
  )
    .bind(session.familyId)
    .first<{ id: string; name: string; tz: string }>();
  if (!family) throw notFound("family_not_found");

  // Run the three list queries in parallel; D1 multiplexes them.
  const [parentRow, kidRow, kidsRes, itemsRes, summaryRes] = await Promise.all([
    session.role === "parent"
      ? c.env.DB.prepare(
          "SELECT id, email, display_name FROM parents WHERE id = ?",
        )
          .bind(session.parentId)
          .first<{ id: string; email: string | null; display_name: string }>()
      : Promise.resolve(null),
    session.role === "kid"
      ? c.env.DB.prepare(
          `SELECT id, display_name AS displayName, avatar_emoji AS avatarEmoji,
                  avatar_color AS avatarColor
             FROM kids WHERE id = ?`,
        )
          .bind(session.kidId)
          .first<{
            id: string;
            displayName: string;
            avatarEmoji: string;
            avatarColor: string;
          }>()
      : Promise.resolve(null),
    c.env.DB.prepare(
      `SELECT id, display_name AS displayName, avatar_emoji AS avatarEmoji,
              avatar_color AS avatarColor, created_at AS createdAt
         FROM kids
         WHERE family_id = ? AND archived_at IS NULL
         ORDER BY created_at ASC`,
    )
      .bind(session.familyId)
      .all(),
    c.env.DB.prepare(
      `SELECT id, name, cost_stars AS costStars, emoji, created_at AS createdAt
         FROM items
         WHERE family_id = ? AND archived_at IS NULL
         ORDER BY created_at ASC`,
    )
      .bind(session.familyId)
      .all(),
    // One grouped aggregate query for all kid summaries — saves N round-trips.
    c.env.DB.prepare(
      // The LEFT JOIN keeps kids who have no stars, but those rows produce
      // s.id = NULL / s.used_at = NULL, which would otherwise tick the
      // availableStars counter. Guard each counter on `s.id IS NOT NULL`
      // so empty groups stay at 0.
      `SELECT
          k.id AS kidId,
          COALESCE(SUM(CASE WHEN s.id IS NOT NULL AND s.used_at IS NULL
                            THEN 1 ELSE 0 END), 0) AS availableStars,
          COUNT(s.id) AS totalEarned,
          COALESCE(SUM(CASE WHEN s.id IS NOT NULL AND s.used_at IS NOT NULL
                            THEN 1 ELSE 0 END), 0) AS totalUsed
        FROM kids k
        LEFT JOIN stars s ON s.kid_id = k.id
        WHERE k.family_id = ? AND k.archived_at IS NULL
        GROUP BY k.id`,
    )
      .bind(session.familyId)
      .all<{
        kidId: string;
        availableStars: number;
        totalEarned: number;
        totalUsed: number;
      }>(),
  ]);

  const summaries: Record<
    string,
    { availableStars: number; totalEarned: number; totalUsed: number }
  > = {};
  for (const row of summaryRes.results) {
    summaries[row.kidId] = {
      availableStars: row.availableStars,
      totalEarned: row.totalEarned,
      totalUsed: row.totalUsed,
    };
  }

  return c.json({
    role: session.role,
    family,
    parent: parentRow,
    kid: kidRow,
    kids: kidsRes.results,
    items: itemsRes.results,
    summaries,
  });
});
