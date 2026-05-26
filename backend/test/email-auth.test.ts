import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { callApp, resetDb } from "./helpers.ts";
import { recordCode } from "../src/auth/email.ts";

beforeEach(async () => {
  await resetDb();
});

describe("POST /auth/email/verify", () => {
  it("mints a parent session for a valid (email, code)", async () => {
    const email = "p@example.com";
    const code = "123456";
    await recordCode(env, { email, code, ip: "1.2.3.4" });

    const res = await callApp("POST", "/auth/email/verify", {
      body: { email, code, familyTz: "UTC", displayName: "Patti" },
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      sessionToken: string;
      role: string;
      parentId: string;
      familyId: string;
    };
    expect(body.role).toBe("parent");
    expect(body.sessionToken).toMatch(/^[0-9A-Z]+$/);
    expect(body.parentId).toBeTruthy();
    expect(body.familyId).toBeTruthy();

    // Token works.
    const me = await callApp("GET", "/me", { token: body.sessionToken });
    expect(me.status).toBe(200);
  });

  it("normalizes the email (case-insensitive)", async () => {
    await recordCode(env, { email: "p@example.com", code: "654321", ip: null });
    const res = await callApp("POST", "/auth/email/verify", {
      body: { email: "P@Example.COM", code: "654321" },
    });
    expect(res.status).toBe(200);
  });

  it("400 when the code is wrong", async () => {
    await recordCode(env, { email: "p@example.com", code: "111111", ip: null });
    const res = await callApp("POST", "/auth/email/verify", {
      body: { email: "p@example.com", code: "999999" },
    });
    expect(res.status).toBe(400);
    expect((await res.json()).error).toBe("invalid_or_expired_code");
  });

  it("400 when the code was already consumed", async () => {
    await recordCode(env, { email: "p@example.com", code: "424242", ip: null });
    const first = await callApp("POST", "/auth/email/verify", {
      body: { email: "p@example.com", code: "424242" },
    });
    expect(first.status).toBe(200);
    const second = await callApp("POST", "/auth/email/verify", {
      body: { email: "p@example.com", code: "424242" },
    });
    expect(second.status).toBe(400);
  });

  it("400 on malformed code", async () => {
    const res = await callApp("POST", "/auth/email/verify", {
      body: { email: "p@example.com", code: "abc" },
    });
    expect(res.status).toBe(400);
  });

  it("re-signing in with the same email returns the same parent", async () => {
    await recordCode(env, { email: "p@example.com", code: "111111", ip: null });
    const first = await callApp("POST", "/auth/email/verify", {
      body: { email: "p@example.com", code: "111111" },
    });
    const firstBody = (await first.json()) as { parentId: string; familyId: string };

    await recordCode(env, { email: "p@example.com", code: "222222", ip: null });
    const second = await callApp("POST", "/auth/email/verify", {
      body: { email: "p@example.com", code: "222222" },
    });
    const secondBody = (await second.json()) as { parentId: string; familyId: string };
    expect(secondBody.parentId).toBe(firstBody.parentId);
    expect(secondBody.familyId).toBe(firstBody.familyId);
  });
});

describe("POST /auth/email/request", () => {
  it("sends an email and stores a code", async () => {
    // Mock global fetch so Resend isn't called for real in tests.
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ id: "fake" }), { status: 200 }),
    );
    // The route reads env.RESEND_API_KEY; helpers.ts uses cloudflare:test env
    // which doesn't have one by default. Patch it inline.
    const original = (env as unknown as Record<string, string>).RESEND_API_KEY;
    (env as unknown as Record<string, string>).RESEND_API_KEY = "test-key";
    try {
      const res = await callApp("POST", "/auth/email/request", {
        body: { email: "p@example.com" },
      });
      expect(res.status).toBe(200);
      expect(fetchSpy).toHaveBeenCalledOnce();
      const call = fetchSpy.mock.calls[0]!;
      expect(call[0]).toBe("https://api.resend.com/emails");
      const sent = JSON.parse((call[1] as RequestInit).body as string);
      expect(sent.to).toBe("p@example.com");
      expect(sent.subject).toMatch(/Kochvaia sign-in code: \d{6}/);
    } finally {
      fetchSpy.mockRestore();
      (env as unknown as Record<string, string | undefined>).RESEND_API_KEY = original;
    }
  });

  it("400 on bad email", async () => {
    const res = await callApp("POST", "/auth/email/request", {
      body: { email: "not-an-email" },
    });
    expect(res.status).toBe(400);
  });
});
