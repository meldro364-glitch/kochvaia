export interface Env {
  DB: D1Database;
  GOOGLE_OAUTH_CLIENT_ID: string;
  ENVIRONMENT: string;
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
