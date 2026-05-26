import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
import { logger } from "hono/logger";
import type { AppVariables, Env } from "./types.ts";
import { authRoutes } from "./routes/auth.ts";
import { kidsRoutes } from "./routes/kids.ts";
import { starsRoutes } from "./routes/stars.ts";
import { seenRoutes } from "./routes/seen.ts";
import { itemsRoutes } from "./routes/items.ts";
import { policyRoutes } from "./routes/policy.ts";

const app = new Hono<{ Bindings: Env; Variables: AppVariables }>();

app.use("*", logger());
app.use(
  "*",
  cors({
    origin: "*",
    allowMethods: ["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
    allowHeaders: ["Authorization", "Content-Type"],
    maxAge: 86400,
  }),
);

app.get("/health", (c) => c.json({ ok: true, env: c.env.ENVIRONMENT }));

app.route("/auth", authRoutes);
app.route("/", kidsRoutes);
app.route("/", starsRoutes);
app.route("/", seenRoutes);
app.route("/", itemsRoutes);
app.route("/", policyRoutes);

app.onError((err, c) => {
  if (err instanceof HTTPException) {
    return c.json({ error: err.message }, err.status);
  }
  console.error("unhandled error", err);
  return c.json({ error: "internal_error" }, 500);
});

app.notFound((c) => c.json({ error: "not_found" }, 404));

export default app;
