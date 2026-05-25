import { beforeEach, describe, expect, it } from "vitest";
import { callApp, resetDb, seedFamily } from "./helpers.ts";

beforeEach(async () => {
  await resetDb();
});

describe("QR pairing", () => {
  it("kid-pair QR can be created, redeemed once, and yields a working kid session", async () => {
    const fam = await seedFamily();
    const create = await callApp("POST", "/auth/qr/create", {
      token: fam.parentToken,
      body: { kind: "kid", kidId: fam.kidIds[0] },
    });
    expect(create.status).toBe(200);
    const { code } = (await create.json()) as { code: string };

    const redeem = await callApp("POST", "/auth/qr/redeem", {
      body: { code, deviceLabel: "Kid Tablet" },
    });
    expect(redeem.status).toBe(200);
    const r = (await redeem.json()) as {
      role: string;
      sessionToken: string;
      kidId: string;
    };
    expect(r.role).toBe("kid");
    expect(r.kidId).toBe(fam.kidIds[0]);

    // Token works for /me.
    const me = await callApp("GET", "/me", { token: r.sessionToken });
    expect(me.status).toBe(200);

    // Second redemption fails — single-use.
    const again = await callApp("POST", "/auth/qr/redeem", { body: { code } });
    expect(again.status).toBe(404);
  });

  it("parent invite QR does NOT consume on redeem; reports requiresGoogleSignIn", async () => {
    const fam = await seedFamily();
    const create = await callApp("POST", "/auth/qr/create", {
      token: fam.parentToken,
      body: { kind: "parent" },
    });
    const { code } = (await create.json()) as { code: string };

    const redeem = await callApp("POST", "/auth/qr/redeem", { body: { code } });
    const body = (await redeem.json()) as { role: string; requiresGoogleSignIn?: boolean };
    expect(body.role).toBe("parent");
    expect(body.requiresGoogleSignIn).toBe(true);

    // Code is still valid for the actual sign-in step (peek doesn't consume).
    const again = await callApp("POST", "/auth/qr/redeem", { body: { code } });
    expect(again.status).toBe(200);
  });

  it("rejects QR creation from kid sessions", async () => {
    const fam = await seedFamily();
    const res = await callApp("POST", "/auth/qr/create", {
      token: fam.kidTokens[0],
      body: { kind: "kid", kidId: fam.kidIds[0] },
    });
    expect(res.status).toBe(403);
  });

  it("rejects kid kind without a kidId", async () => {
    const fam = await seedFamily();
    const res = await callApp("POST", "/auth/qr/create", {
      token: fam.parentToken,
      body: { kind: "kid" },
    });
    expect(res.status).toBe(400);
  });
});
