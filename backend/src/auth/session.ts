/**
 * Session token issuance + lookup.
 * Tokens are random 32-byte base32. We store SHA-256(token) at rest — the raw
 * token only ever leaves the server in the issuing response.
 */

import { newSessionToken, sha256Hex } from "../lib/ids.ts";
import type { Env, SessionContext } from "../types.ts";
import { unauthorized } from "../lib/errors.ts";

const PARENT_TTL_DAYS = 30;
const KID_TTL_DAYS = 90;

export interface IssuedSession {
  token: string;
  tokenHash: string;
  expiresAt: number;
}

export async function mintParentSession(
  env: Env,
  parentId: string,
): Promise<IssuedSession> {
  const token = newSessionToken();
  const tokenHash = await sha256Hex(token);
  const now = Date.now();
  await env.DB.prepare(
    "INSERT INTO parent_sessions (token_hash, parent_id, created_at, last_seen_at) VALUES (?, ?, ?, ?)",
  )
    .bind(tokenHash, parentId, now, now)
    .run();
  return { token, tokenHash, expiresAt: now + PARENT_TTL_DAYS * 86400_000 };
}

export async function mintKidSession(
  env: Env,
  kidId: string,
  deviceLabel: string | null,
): Promise<IssuedSession> {
  const token = newSessionToken();
  const tokenHash = await sha256Hex(token);
  const now = Date.now();
  await env.DB.prepare(
    "INSERT INTO kid_sessions (token_hash, kid_id, device_label, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)",
  )
    .bind(tokenHash, kidId, deviceLabel, now, now)
    .run();
  return { token, tokenHash, expiresAt: now + KID_TTL_DAYS * 86400_000 };
}

/**
 * Resolves a bearer token to a SessionContext. Returns null if not found /
 * expired / revoked. Also bumps last_seen_at (fire-and-forget).
 */
export async function resolveSession(
  env: Env,
  bearer: string,
): Promise<SessionContext | null> {
  const tokenHash = await sha256Hex(bearer);
  const now = Date.now();

  const parent = await env.DB.prepare(
    `SELECT ps.token_hash, ps.parent_id, ps.created_at, ps.revoked_at, p.family_id
     FROM parent_sessions ps
     JOIN parents p ON p.id = ps.parent_id
     WHERE ps.token_hash = ?`,
  )
    .bind(tokenHash)
    .first<{
      token_hash: string;
      parent_id: string;
      created_at: number;
      revoked_at: number | null;
      family_id: string;
    }>();
  if (parent) {
    if (parent.revoked_at !== null) return null;
    if (now - parent.created_at > PARENT_TTL_DAYS * 86400_000) return null;
    // Best-effort last_seen bump.
    env.DB.prepare("UPDATE parent_sessions SET last_seen_at = ? WHERE token_hash = ?")
      .bind(now, tokenHash)
      .run();
    return {
      role: "parent",
      sessionTokenHash: tokenHash,
      parentId: parent.parent_id,
      familyId: parent.family_id,
    };
  }

  const kid = await env.DB.prepare(
    `SELECT ks.token_hash, ks.kid_id, ks.created_at, ks.revoked_at, k.family_id, k.archived_at
     FROM kid_sessions ks
     JOIN kids k ON k.id = ks.kid_id
     WHERE ks.token_hash = ?`,
  )
    .bind(tokenHash)
    .first<{
      token_hash: string;
      kid_id: string;
      created_at: number;
      revoked_at: number | null;
      family_id: string;
      archived_at: number | null;
    }>();
  if (kid) {
    if (kid.revoked_at !== null) return null;
    if (kid.archived_at !== null) return null;
    if (now - kid.created_at > KID_TTL_DAYS * 86400_000) return null;
    env.DB.prepare("UPDATE kid_sessions SET last_seen_at = ? WHERE token_hash = ?")
      .bind(now, tokenHash)
      .run();
    return {
      role: "kid",
      sessionTokenHash: tokenHash,
      kidId: kid.kid_id,
      familyId: kid.family_id,
    };
  }

  return null;
}

/** Extracts the bearer token from an Authorization header. */
export function extractBearer(authHeader: string | null | undefined): string {
  if (!authHeader) throw unauthorized("missing_authorization");
  const m = /^Bearer (.+)$/i.exec(authHeader.trim());
  if (!m) throw unauthorized("invalid_authorization");
  return m[1]!;
}
