import { Hono } from "hono";
import type { AppVariables, Env } from "../types.ts";

export const landingRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

// App icon (512x512 PNG, ~4 KB) inlined as base64 so the landing page is a
// single self-contained response. No extra round-trip, no asset host needed.
const APP_ICON_B64 =
  "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";

/**
 * Marketing landing page for kochvaia.uk.
 *
 * The Worker is also bound to api.kochvaia.uk for the same routes; we don't
 * want the API host's root to show the marketing page, so we sniff the
 * request hostname and 404 on the API host.
 */
landingRoutes.get("/", (c) => {
  const url = new URL(c.req.url);
  if (url.hostname.startsWith("api.")) {
    return c.json({ error: "not_found" }, 404);
  }
  const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Kochvaia — Stars for great days</title>
  <meta name="description" content="Kochvaia is a tiny family app for tracking the stars kids earn for good days, and the rewards they can save up for. Private, no ads, no tracking.">
  <meta property="og:title" content="Kochvaia — Stars for great days">
  <meta property="og:description" content="A tiny family app for tracking the stars kids earn for good days, and the rewards they can save up for.">
  <meta property="og:type" content="website">
  <meta property="og:url" content="https://kochvaia.uk/">
  <link rel="icon" href="data:image/png;base64,${APP_ICON_B64}">
  <style>
    :root {
      --cream: #FFF7E6;
      --slate: #2E2A26;
      --muted: #6E665E;
      --amber: #FFB347;
      --amber-soft: #FFE6B8;
      --mint: #9FD8B7;
      --sky: #8DBEE0;
      --pink: #F4A6C0;
      --lilac: #C7B8EA;
      color-scheme: light dark;
    }
    @media (prefers-color-scheme: dark) {
      :root {
        --cream: #1d1b18;
        --slate: #e9e4dc;
        --muted: #a8a098;
        --amber-soft: #3a2e1a;
      }
    }
    * { box-sizing: border-box; }
    html, body { margin: 0; padding: 0; }
    body {
      font: 17px/1.55 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
      background: var(--cream);
      color: var(--slate);
      -webkit-font-smoothing: antialiased;
    }
    .wrap { max-width: 880px; margin: 0 auto; padding: 0 24px; }
    header { padding: 56px 0 32px; }
    .hero {
      display: flex;
      align-items: center;
      gap: 24px;
      flex-wrap: wrap;
    }
    .icon {
      width: 96px; height: 96px; border-radius: 22px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.08);
      flex-shrink: 0;
    }
    .hero-text h1 {
      margin: 0 0 6px;
      font-size: clamp(2rem, 5vw, 2.6rem);
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    .hero-text .tag {
      margin: 0;
      color: var(--muted);
      font-size: 1.15rem;
    }
    .lede {
      margin: 32px 0 0;
      font-size: 1.15rem;
      max-width: 60ch;
    }
    section { padding: 32px 0; }
    h2 {
      font-size: 1.5rem;
      margin: 0 0 16px;
      letter-spacing: -0.01em;
    }
    .features {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-top: 8px;
    }
    .feature {
      background: rgba(255, 179, 71, 0.08);
      border-radius: 16px;
      padding: 20px;
    }
    .feature .emoji { font-size: 1.8rem; line-height: 1; }
    .feature h3 {
      margin: 12px 0 4px;
      font-size: 1.05rem;
      font-weight: 600;
    }
    .feature p { margin: 0; color: var(--muted); font-size: 0.95rem; }

    .steps {
      list-style: none;
      padding: 0;
      margin: 8px 0 0;
      display: grid;
      gap: 14px;
    }
    .step {
      display: grid;
      grid-template-columns: 40px 1fr;
      gap: 14px;
      align-items: start;
    }
    .step .num {
      width: 32px; height: 32px;
      border-radius: 50%;
      background: var(--amber);
      color: var(--slate);
      font-weight: 700;
      display: grid;
      place-items: center;
    }
    .step strong { font-weight: 600; }
    .step p { margin: 0; color: var(--muted); }

    .privacy-card {
      background: rgba(159, 216, 183, 0.18);
      border-radius: 16px;
      padding: 20px 24px;
    }
    .privacy-card h3 {
      margin: 0 0 8px;
      font-size: 1.15rem;
    }
    .privacy-card p { margin: 0; color: var(--muted); }
    .privacy-card a { color: inherit; }

    footer {
      padding: 40px 0 56px;
      color: var(--muted);
      font-size: 0.92rem;
      border-top: 1px solid rgba(110, 102, 94, 0.18);
      margin-top: 32px;
    }
    footer a { color: var(--muted); margin-right: 16px; }
    footer a:hover { color: var(--slate); }
    a { color: var(--slate); text-decoration: underline; text-underline-offset: 3px; }
  </style>
</head>
<body>
  <div class="wrap">
    <header>
      <div class="hero">
        <img class="icon" src="data:image/png;base64,${APP_ICON_B64}" alt="">
        <div class="hero-text">
          <h1>Kochvaia</h1>
          <p class="tag">Stars for great days.</p>
        </div>
      </div>
      <p class="lede">
        A tiny family app for tracking the stars your kids earn for good days,
        and the rewards they can save up for. No ads, no tracking, no nagging
        notifications — just a calm shared view of how the week is going.
      </p>
    </header>

    <section>
      <h2>What it does</h2>
      <div class="features">
        <div class="feature">
          <div class="emoji">⭐</div>
          <h3>One star per great day</h3>
          <p>Parents tap to award; the calendar shows the week at a glance.</p>
        </div>
        <div class="feature">
          <div class="emoji">🎁</div>
          <h3>Save up for rewards</h3>
          <p>Set a rewards catalogue with star costs. Kids see what's within reach.</p>
        </div>
        <div class="feature">
          <div class="emoji">👨‍👩‍👧‍👦</div>
          <h3>Built for siblings</h3>
          <p>Each kid has their own profile; siblings can root for each other.</p>
        </div>
      </div>
    </section>

    <section>
      <h2>How it works</h2>
      <ol class="steps">
        <li class="step">
          <div class="num">1</div>
          <div>
            <strong>Parent signs in</strong>
            <p>Use Google sign-in, or an emailed 6-digit code. No password to remember.</p>
          </div>
        </li>
        <li class="step">
          <div class="num">2</div>
          <div>
            <strong>Add your kids and rewards</strong>
            <p>Pick names, emojis, colors. Decide what rewards are worth how many stars.</p>
          </div>
        </li>
        <li class="step">
          <div class="num">3</div>
          <div>
            <strong>Pair a kid device by QR</strong>
            <p>Optional: hand a tablet to your kid, scan the QR — they get a read-only view of their own profile and stars.</p>
          </div>
        </li>
        <li class="step">
          <div class="num">4</div>
          <div>
            <strong>Award stars at the end of the day</strong>
            <p>Tap a day, award a star. Stars stack up toward rewards. The week resets weekly; the journey continues.</p>
          </div>
        </li>
      </ol>
    </section>

    <section>
      <div class="privacy-card">
        <h3>Privacy first 🛡️</h3>
        <p>
          No ads, no analytics, no advertising IDs, no third-party tracking.
          Family data lives in a Cloudflare Worker we operate; nothing is sold or
          shared. See the <a href="/privacy">full privacy policy</a>.
        </p>
      </div>
    </section>

    <section>
      <h2>Get the app</h2>
      <p style="color: var(--muted); max-width: 60ch;">
        Kochvaia is currently in review on the Amazon Appstore (for Fire tablets)
        and we'll add a Google Play link soon. For early access on an Android
        phone, email
        <a href="mailto:meldro364@gmail.com">meldro364@gmail.com</a>.
      </p>
    </section>

    <footer>
      <a href="/privacy">Privacy</a>
      <a href="mailto:meldro364@gmail.com">Contact</a>
      <span style="float: right;">Made with ⭐ for families</span>
    </footer>
  </div>
</body>
</html>`;
  // 5-minute browser cache — the page is static today; flush by redeploy.
  c.header("Cache-Control", "public, max-age=300");
  return c.html(html);
});
