import { defineWorkersConfig, readD1Migrations } from "@cloudflare/vitest-pool-workers/config";
import path from "node:path";

export default defineWorkersConfig(async () => {
  const migrationsPath = path.join(__dirname, "migrations");
  const migrations = await readD1Migrations(migrationsPath);

  return {
    test: {
      setupFiles: ["./test/setup.ts"],
      poolOptions: {
        workers: {
          singleWorker: true,
          isolatedStorage: false,
          wrangler: { configPath: "./wrangler.toml" },
          miniflare: {
            d1Databases: ["DB"],
            bindings: {
              GOOGLE_OAUTH_CLIENT_ID: "test-client-id.apps.googleusercontent.com",
              ENVIRONMENT: "test",
              TEST_MIGRATIONS: migrations,
            },
          },
        },
      },
    },
  };
});
