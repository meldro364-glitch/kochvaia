/**
 * Email magic-code authentication.
 *
 * Flow:
 *   1. POST /auth/email/request { email } — we generate a 6-digit code,
 *      store sha256(email + ":" + code) with a 10-minute TTL, and email
 *      the code to the user via Resend.
 *   2. POST /auth/email/verify { email, code } — we look up the row by
 *      (email, code_hash). If unconsumed and unexpired, we either find a
 *      parent with this email or create one, then mint a parent session.
 *
 * Rate limiting: at most 5 active (unconsumed, unexpired) codes per
 * email at any time. We sweep expired rows opportunistically on insert.
 *
 * The code is bound to the email so a leaked code can't be reused with a
 * different address.
 */

import type { Env } from "../types.ts";
import { sha256Hex, newId } from "../lib/ids.ts";

const CODE_TTL_MS = 10 * 60 * 1000;
const MAX_ACTIVE_CODES_PER_EMAIL = 5;

export function generateCode(): string {
  const buf = new Uint8Array(4);
  crypto.getRandomValues(buf);
  // 32 bits → ~4.3 billion; mod 1_000_000 keeps a small bias but is fine
  // for a 6-digit human-facing code that's also rate-limited.
  const n = ((buf[0]! << 24) | (buf[1]! << 16) | (buf[2]! << 8) | buf[3]!) >>> 0;
  return (n % 1_000_000).toString().padStart(6, "0");
}

export function normalizeEmail(raw: string): string {
  return raw.trim().toLowerCase();
}

export function isValidEmail(email: string): boolean {
  // Deliberately permissive — Resend will reject true junk on send.
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) && email.length <= 320;
}

export async function codeHash(email: string, code: string): Promise<string> {
  return sha256Hex(`${email}:${code}`);
}

/**
 * Records a new code in D1. Sweeps expired codes for this email first,
 * then enforces the per-email cap. Returns the inserted row's expires_at.
 */
export async function recordCode(
  env: Env,
  args: { email: string; code: string; ip: string | null },
): Promise<{ expiresAt: number }> {
  const now = Date.now();
  const expiresAt = now + CODE_TTL_MS;
  const hash = await codeHash(args.email, args.code);

  // Sweep expired or already-consumed rows for this email.
  await env.DB.prepare(
    `DELETE FROM email_login_codes
       WHERE email = ? AND (expires_at < ? OR consumed_at IS NOT NULL)`,
  )
    .bind(args.email, now)
    .run();

  // Cap active codes.
  const active = await env.DB.prepare(
    `SELECT COUNT(*) AS c FROM email_login_codes
       WHERE email = ? AND consumed_at IS NULL AND expires_at >= ?`,
  )
    .bind(args.email, now)
    .first<{ c: number }>();
  if ((active?.c ?? 0) >= MAX_ACTIVE_CODES_PER_EMAIL) {
    throw new Error("too_many_active_codes");
  }

  await env.DB.prepare(
    `INSERT INTO email_login_codes (email, code_hash, expires_at, created_at, ip)
     VALUES (?, ?, ?, ?, ?)`,
  )
    .bind(args.email, hash, expiresAt, now, args.ip)
    .run();
  return { expiresAt };
}

/**
 * Verifies + consumes a code. Returns true on success, false if no matching
 * unconsumed unexpired row exists.
 */
export async function consumeCode(
  env: Env,
  args: { email: string; code: string },
): Promise<boolean> {
  const hash = await codeHash(args.email, args.code);
  const now = Date.now();
  const res = await env.DB.prepare(
    `UPDATE email_login_codes
       SET consumed_at = ?
     WHERE email = ? AND code_hash = ?
       AND consumed_at IS NULL AND expires_at >= ?`,
  )
    .bind(now, args.email, hash, now)
    .run();
  return (res.meta.changes ?? 0) > 0;
}

/**
 * Sends the code via Resend. Throws on send failure.
 */
export async function sendCodeEmail(
  env: Env,
  args: { to: string; code: string },
): Promise<void> {
  if (!env.RESEND_API_KEY) throw new Error("resend_not_configured");
  const from = env.EMAIL_FROM || "hello@kochvaia.uk";
  const subject = `Kochvaia sign-in code: ${args.code}`;
  const text = [
    `Your Kochvaia sign-in code is: ${args.code}`,
    ``,
    `Enter this code in the app to sign in. The code expires in 10 minutes.`,
    ``,
    `If you didn't ask for this code, you can ignore this email.`,
    ``,
    `— Kochvaia`,
  ].join("\n");
  const html = `<!doctype html>
<html><body style="font:16px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#2E2A26;max-width:480px;margin:24px auto;padding:0 16px;">
  <h1 style="font-size:1.4rem;margin:0 0 12px 0;">Your Kochvaia code</h1>
  <p style="margin:0 0 16px 0;">Enter this code in the app to sign in:</p>
  <div style="font:600 2.4rem/1 'SF Mono',Menlo,monospace;letter-spacing:0.2em;background:#FFF3D6;color:#2E2A26;padding:18px 24px;border-radius:14px;text-align:center;margin:0 0 16px 0;">
    ${args.code}
  </div>
  <p style="margin:0 0 8px 0;color:#6E665E;">Expires in 10 minutes.</p>
  <p style="margin:0;color:#6E665E;">If you didn't ask for this code, you can ignore this email.</p>
  <p style="margin:24px 0 0 0;color:#6E665E;font-size:0.85rem;">— Kochvaia</p>
</body></html>`;

  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${env.RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ from, to: args.to, subject, text, html }),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`resend_send_failed_${res.status}_${body.slice(0, 200)}`);
  }
}

/**
 * Find-or-create a parent by email. If a parent exists with this email but
 * was originally email-only (synthetic google_sub), keep it. If a Google-
 * signed-in parent already has this email, return them — email sign-in is
 * the same identity.
 */
export async function findOrCreateParentByEmail(
  env: Env,
  args: { email: string; familyTz: string; displayName: string },
): Promise<{ parentId: string; familyId: string }> {
  const existing = await env.DB.prepare(
    "SELECT id, family_id FROM parents WHERE email = ?",
  )
    .bind(args.email)
    .first<{ id: string; family_id: string }>();
  if (existing) return { parentId: existing.id, familyId: existing.family_id };

  const familyId = newId();
  const parentId = newId();
  const now = Date.now();
  await env.DB.prepare(
    "INSERT INTO families (id, name, tz, created_at) VALUES (?, ?, ?, ?)",
  )
    .bind(familyId, `${args.displayName}'s family`.slice(0, 80), args.familyTz, now)
    .run();
  await env.DB.prepare(
    `INSERT INTO parents (id, family_id, google_sub, email, display_name, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  )
    .bind(parentId, familyId, `email:${args.email}`, args.email, args.displayName, now)
    .run();
  return { parentId, familyId };
}
