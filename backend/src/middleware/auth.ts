import type { MiddlewareHandler } from "hono";
import { extractBearer, resolveSession } from "../auth/session.ts";
import { forbidden, unauthorized } from "../lib/errors.ts";
import type { AppVariables, Env } from "../types.ts";

type Handler = MiddlewareHandler<{ Bindings: Env; Variables: AppVariables }>;

/** Any authenticated session (parent OR kid). */
export const requireSession: Handler = async (c, next) => {
  const bearer = extractBearer(c.req.header("authorization"));
  const session = await resolveSession(c.env, bearer);
  if (!session) throw unauthorized("invalid_session");
  c.set("session", session);
  await next();
};

export const requireParent: Handler = async (c, next) => {
  const bearer = extractBearer(c.req.header("authorization"));
  const session = await resolveSession(c.env, bearer);
  if (!session) throw unauthorized("invalid_session");
  if (session.role !== "parent") throw forbidden("parent_only");
  c.set("session", session);
  await next();
};

export const requireKid: Handler = async (c, next) => {
  const bearer = extractBearer(c.req.header("authorization"));
  const session = await resolveSession(c.env, bearer);
  if (!session) throw unauthorized("invalid_session");
  if (session.role !== "kid") throw forbidden("kid_only");
  c.set("session", session);
  await next();
};
