/**
 * QR pairing codes.
 *
 * Two kinds:
 *   - 'kid'    — pairs a kid device to a specific kid; redemption returns a kid session.
 *   - 'parent' — co-parent invite; redemption is consumed by a Google sign-in
 *                request that joins the existing family instead of creating one.
 *
 * Codes are 8-char Crockford base32 (40 bits), formatted "ABCD-EFGH" for human
 * readability. TTL 5 minutes. Single-use: redemption sets consumed_at.
 */

import type { Env } from "../types.ts";
import { newJoinCode } from "../lib/ids.ts";
import { badRequest, notFound } from "../lib/errors.ts";

const TTL_MS = 5 * 60 * 1000;

export type JoinKind = "parent" | "kid";

export interface CreatedJoinCode {
  code: string;
  kind: JoinKind;
  kidId: string | null;
  expiresAt: number;
}

export async function createJoinCode(
  env: Env,
  args: {
    familyId: string;
    createdByParentId: string;
    kind: JoinKind;
    kidId?: string | null;
  },
): Promise<CreatedJoinCode> {
  if (args.kind === "kid" && !args.kidId) {
    throw badRequest("kid_id_required_for_kid_kind");
  }
  if (args.kind === "parent" && args.kidId) {
    throw badRequest("kid_id_not_allowed_for_parent_kind");
  }

  // Validate kid exists in family if kind=kid.
  if (args.kind === "kid") {
    const row = await env.DB.prepare(
      "SELECT id FROM kids WHERE id = ? AND family_id = ? AND archived_at IS NULL",
    )
      .bind(args.kidId, args.familyId)
      .first<{ id: string }>();
    if (!row) throw notFound("kid_not_found");
  }

  const now = Date.now();
  const expiresAt = now + TTL_MS;
  const code = newJoinCode();
  await env.DB.prepare(
    `INSERT INTO join_codes
       (code, family_id, kind, kid_id, created_by_parent_id, created_at, expires_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
  )
    .bind(code, args.familyId, args.kind, args.kidId ?? null, args.createdByParentId, now, expiresAt)
    .run();
  return { code, kind: args.kind, kidId: args.kidId ?? null, expiresAt };
}

export interface ConsumedJoinCode {
  familyId: string;
  kind: JoinKind;
  kidId: string | null;
}

/**
 * Atomically consume a join code. Returns the metadata on success.
 * Throws notFound if missing / expired / already consumed.
 */
export async function consumeJoinCode(env: Env, rawCode: string): Promise<ConsumedJoinCode> {
  const code = rawCode.trim().toUpperCase();
  if (!/^[0-9A-Z]{4}-[0-9A-Z]{4}$/.test(code)) {
    throw badRequest("invalid_code_format");
  }
  const now = Date.now();

  // Single-statement consume + return to avoid races.
  const updated = await env.DB.prepare(
    `UPDATE join_codes
       SET consumed_at = ?
     WHERE code = ?
       AND consumed_at IS NULL
       AND expires_at >= ?
     RETURNING family_id, kind, kid_id`,
  )
    .bind(now, code, now)
    .first<{ family_id: string; kind: JoinKind; kid_id: string | null }>();

  if (!updated) throw notFound("code_invalid_or_expired");
  return { familyId: updated.family_id, kind: updated.kind, kidId: updated.kid_id };
}

/** Peek without consuming — used during Google sign-in to decide "join vs create family". */
export async function peekJoinCode(env: Env, rawCode: string): Promise<ConsumedJoinCode | null> {
  const code = rawCode.trim().toUpperCase();
  if (!/^[0-9A-Z]{4}-[0-9A-Z]{4}$/.test(code)) return null;
  const now = Date.now();
  const row = await env.DB.prepare(
    `SELECT family_id, kind, kid_id FROM join_codes
       WHERE code = ? AND consumed_at IS NULL AND expires_at >= ?`,
  )
    .bind(code, now)
    .first<{ family_id: string; kind: JoinKind; kid_id: string | null }>();
  if (!row) return null;
  return { familyId: row.family_id, kind: row.kind, kidId: row.kid_id };
}
