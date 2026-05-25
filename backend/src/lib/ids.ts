// Crockford base32 alphabet (no I, L, O, U) — readable, unambiguous.
const CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

function randomBase32(bytes: number): string {
  const buf = new Uint8Array(bytes);
  crypto.getRandomValues(buf);
  let out = "";
  for (let i = 0; i < buf.length; i++) {
    out += CROCKFORD[buf[i]! & 0x1f];
  }
  return out;
}

/** 26-char id, ~130 bits of entropy. Good for primary keys. */
export function newId(): string {
  return randomBase32(26);
}

/** Short join code like "ABCD-EFGH" — 8 chars, 40 bits. One-shot + 5-min TTL. */
export function newJoinCode(): string {
  const raw = randomBase32(8);
  return `${raw.slice(0, 4)}-${raw.slice(4, 8)}`;
}

/** 32-byte random session token, base32-encoded (52 chars). */
export function newSessionToken(): string {
  return randomBase32(32);
}

/** SHA-256 hex of a string. Used to store session tokens at rest. */
export async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", data);
  const bytes = new Uint8Array(digest);
  let hex = "";
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i]!.toString(16).padStart(2, "0");
  }
  return hex;
}
