/**
 * Google ID token verification.
 *
 * We fetch Google's JWKS, cache it in module-global memory (lives until the
 * isolate recycles — Google's keys rotate slowly), and verify the JWT signature
 * + standard claims. We DO NOT use a third-party JWT library: WebCrypto + a
 * manual decode is small, auditable, and avoids node-only deps in the Worker.
 */

import { badRequest, unauthorized } from "../lib/errors.ts";

const JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const VALID_ISSUERS = new Set(["accounts.google.com", "https://accounts.google.com"]);
// Allow some clock skew vs Google.
const CLOCK_SKEW_SEC = 60;

interface Jwk {
  kid: string;
  kty: string;
  n: string;
  e: string;
  alg: string;
  use: string;
}

interface JwksCache {
  keys: Map<string, CryptoKey>;
  fetchedAt: number;
  expiresAt: number;
}

let jwksCache: JwksCache | null = null;

async function getJwks(): Promise<Map<string, CryptoKey>> {
  const now = Date.now();
  if (jwksCache && now < jwksCache.expiresAt) {
    return jwksCache.keys;
  }
  const res = await fetch(JWKS_URL);
  if (!res.ok) throw new Error(`jwks fetch failed: ${res.status}`);
  // Honor Cache-Control max-age, default 1h.
  const cacheControl = res.headers.get("cache-control") ?? "";
  const m = /max-age=(\d+)/.exec(cacheControl);
  const maxAgeMs = (m ? parseInt(m[1]!, 10) : 3600) * 1000;

  const body = (await res.json()) as { keys: Jwk[] };
  const keys = new Map<string, CryptoKey>();
  for (const jwk of body.keys) {
    if (jwk.kty !== "RSA") continue;
    const key = await crypto.subtle.importKey(
      "jwk",
      jwk as JsonWebKey,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    keys.set(jwk.kid, key);
  }
  jwksCache = { keys, fetchedAt: now, expiresAt: now + maxAgeMs };
  return keys;
}

function b64urlToBytes(s: string): Uint8Array {
  const pad = "=".repeat((4 - (s.length % 4)) % 4);
  const b64 = (s + pad).replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlToString(s: string): string {
  return new TextDecoder().decode(b64urlToBytes(s));
}

export interface GoogleClaims {
  sub: string;          // Google user id (stable)
  email?: string;
  email_verified?: boolean;
  name?: string;
  picture?: string;
  aud: string;
  iss: string;
  exp: number;
  iat: number;
}

/**
 * Verify a Google ID token. Throws HTTPException on any failure.
 * Returns the validated claims on success.
 */
export async function verifyGoogleIdToken(
  idToken: string,
  expectedAudience: string,
): Promise<GoogleClaims> {
  const parts = idToken.split(".");
  if (parts.length !== 3) throw badRequest("invalid_id_token");
  const [headerB64, payloadB64, sigB64] = parts as [string, string, string];

  let header: { alg?: string; kid?: string; typ?: string };
  let payload: GoogleClaims;
  try {
    header = JSON.parse(b64urlToString(headerB64));
    payload = JSON.parse(b64urlToString(payloadB64));
  } catch {
    throw badRequest("invalid_id_token_encoding");
  }

  if (header.alg !== "RS256") throw unauthorized("unsupported_alg");
  if (!header.kid) throw unauthorized("missing_kid");

  const keys = await getJwks();
  const key = keys.get(header.kid);
  if (!key) {
    // Key rotation: bust cache and retry once.
    jwksCache = null;
    const refreshed = await getJwks();
    const k2 = refreshed.get(header.kid);
    if (!k2) throw unauthorized("unknown_signing_key");
    return verifyWithKey(idToken, k2, payload, headerB64, payloadB64, sigB64, expectedAudience);
  }
  return verifyWithKey(idToken, key, payload, headerB64, payloadB64, sigB64, expectedAudience);
}

async function verifyWithKey(
  _idToken: string,
  key: CryptoKey,
  payload: GoogleClaims,
  headerB64: string,
  payloadB64: string,
  sigB64: string,
  expectedAudience: string,
): Promise<GoogleClaims> {
  const signed = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const sig = b64urlToBytes(sigB64);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, sig, signed);
  if (!ok) throw unauthorized("bad_signature");

  if (!VALID_ISSUERS.has(payload.iss)) throw unauthorized("bad_issuer");
  if (payload.aud !== expectedAudience) throw unauthorized("bad_audience");

  const nowSec = Math.floor(Date.now() / 1000);
  if (payload.exp + CLOCK_SKEW_SEC < nowSec) throw unauthorized("token_expired");
  if (payload.iat - CLOCK_SKEW_SEC > nowSec) throw unauthorized("token_not_yet_valid");

  return payload;
}
