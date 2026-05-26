import { Hono } from "hono";
import type { AppVariables, Env } from "../types.ts";

export const policyRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

/**
 * Plain-HTML privacy policy. Served from the Worker so we don't need a
 * separate static host. Kept short, plain English, and accurate to the
 * actual data the app handles. URL: /privacy
 */
policyRoutes.get("/privacy", (c) => {
  const lastUpdated = "May 25, 2026";
  const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Kochvaia — Privacy Policy</title>
  <style>
    :root { color-scheme: light dark; }
    body {
      font: 16px/1.55 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      max-width: 720px; margin: 2rem auto; padding: 0 1rem; color: #2E2A26;
      background: #FFF7E6;
    }
    @media (prefers-color-scheme: dark) {
      body { background: #1d1b18; color: #e9e4dc; }
      a { color: #FFB347; }
      h1, h2 { color: #FFB347; }
    }
    h1 { font-size: 1.9rem; margin-bottom: 0.2rem; }
    h2 { font-size: 1.2rem; margin-top: 2rem; }
    .updated { color: #6E665E; font-size: 0.9rem; margin-top: 0; }
    ul { padding-left: 1.25rem; }
    code { background: rgba(127,127,127,0.12); padding: 0 0.3em; border-radius: 4px; }
  </style>
</head>
<body>
  <h1>Kochvaia · Privacy Policy</h1>
  <p class="updated">Last updated: ${lastUpdated}</p>

  <p>
    Kochvaia is a family app for tracking stars kids earn and the rewards they
    can save up for. This policy explains what data the app stores, who can
    see it, and how to delete it. It applies to the Kochvaia mobile app and
    the Kochvaia backend service.
  </p>

  <h2>Who runs Kochvaia</h2>
  <p>
    Kochvaia is operated personally by the developer. There is no company.
    Questions about data or this policy:
    <a href="mailto:meldro364@gmail.com">meldro364@gmail.com</a>.
  </p>

  <h2>What we collect</h2>
  <p>When a parent signs in or uses the app, we store:</p>
  <ul>
    <li>A stable identifier from Google Sign-In (Google's <code>sub</code> id) and the email address tied to it. Used to recognize you across sessions.</li>
    <li>Family timezone, family name, and parent display name.</li>
    <li>Each kid you add: a display name, an emoji, and a colour. Kid names are visible only to people in your family group.</li>
    <li>Each star you award: the kid, the date, when it was awarded.</li>
    <li>Each star deduction: count, optional reason, when it was performed.</li>
    <li>Each reward (catalogue item): name, cost, emoji.</li>
    <li>Session tokens (random opaque strings, stored as hashes) for parent and kid devices.</li>
    <li>For each kid device: a "last seen" timestamp per kid profile they view, so we can show new stars as animations.</li>
  </ul>
  <p>We do <strong>not</strong> collect: precise location, contacts, photos, browsing history, advertising identifiers, or any biometric data.</p>

  <h2>What we share</h2>
  <p>
    Nothing. Kochvaia does not sell or share data with third parties. We do
    not use advertising networks. The only third party that sees any data is:
  </p>
  <ul>
    <li>
      <strong>Cloudflare Inc.</strong> — runs the Worker that serves API
      requests and the D1 database that stores the records above. Data is
      hosted in Cloudflare's global network (primary region: ENAM / US).
      Cloudflare may process this data on our behalf under their
      <a href="https://www.cloudflare.com/privacypolicy/">privacy policy</a>.
    </li>
    <li>
      <strong>Google</strong> — verifies the parent's identity when signing in
      with Google. We receive a Google identifier and email; Google does not
      receive your family data.
    </li>
  </ul>

  <h2>How kids' data is protected</h2>
  <p>
    Kochvaia is a parental tool: only a parent can create, rename, or remove
    a kid; only a parent can award or deduct stars; only a parent can add
    rewards. Kids' devices receive a read-only session that lets them view
    the family's current state. Kid sessions are paired by scanning a
    short-lived QR code generated on the parent's device — implicit parental
    consent for that device.
  </p>

  <h2>Retention and deletion</h2>
  <p>
    Data is kept as long as the family account is active. To delete a kid,
    use the trash icon in the parent app — that hides the kid from the
    family view and revokes any paired kid sessions. To delete an entire
    family (all parents, kids, stars, rewards, and sessions), email
    <a href="mailto:meldro364@gmail.com">meldro364@gmail.com</a> from the
    Google address you signed in with. We delete within 30 days and
    confirm by reply.
  </p>

  <h2>Security</h2>
  <p>
    Session tokens are random 32-byte values; we store SHA-256 hashes at
    rest so a database leak would not expose live tokens. Tokens are
    transmitted only over HTTPS. The Android app stores its session token
    in EncryptedSharedPreferences (AES-GCM, Android Keystore). Google ID
    tokens are verified server-side against Google's public keys before any
    session is issued.
  </p>

  <h2>Children's privacy</h2>
  <p>
    Kochvaia is designed for parents to use with their family. Kids do not
    enter any personal information into the app — a parent sets up the kid
    profile (name, emoji, colour) and pairs the kid device. We do not
    knowingly collect personal information from children. If you believe a
    child's information was collected without parental consent, email
    <a href="mailto:meldro364@gmail.com">meldro364@gmail.com</a> and we
    will delete it.
  </p>

  <h2>Changes to this policy</h2>
  <p>
    If we make a material change, we'll update the "Last updated" date at
    the top of this page. The current version is always at
    <a href="/privacy">/privacy</a> on the Kochvaia API host.
  </p>
</body>
</html>`;
  return c.html(html);
});
