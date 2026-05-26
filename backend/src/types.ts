export interface Env {
  DB: D1Database;
  GOOGLE_OAUTH_CLIENT_ID: string;
  ENVIRONMENT: string;
  // Email magic-code login (Resend). Optional in tests; required in prod.
  RESEND_API_KEY?: string;
  EMAIL_FROM?: string;
  MAGIC_LINK_BASE?: string;
}

export type Role = "parent" | "kid";

export interface ParentSessionContext {
  role: "parent";
  sessionTokenHash: string;
  parentId: string;
  familyId: string;
}

export interface KidSessionContext {
  role: "kid";
  sessionTokenHash: string;
  kidId: string;
  familyId: string;
}

export type SessionContext = ParentSessionContext | KidSessionContext;

export interface AppVariables {
  session: SessionContext;
}
