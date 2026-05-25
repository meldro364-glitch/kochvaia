import { beforeAll } from "vitest";
import { applyD1Migrations, env } from "cloudflare:test";

// Migrations are idempotent: applyD1Migrations skips already-applied ones,
// so it's safe to run in every test file's setup.
beforeAll(async () => {
  await applyD1Migrations(env.DB, env.TEST_MIGRATIONS);
});
