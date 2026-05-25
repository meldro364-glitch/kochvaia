import { beforeEach, describe, expect, it } from "vitest";
import { callApp, resetDb, seedFamily } from "./helpers.ts";

beforeEach(async () => {
  await resetDb();
});

describe("GET /me", () => {
  it("returns parent context for a parent token", async () => {
    const fam = await seedFamily();
    const res = await callApp("GET", "/me", { token: fam.parentToken });
    expect(res.status).toBe(200);
    const body = (await res.json()) as { role: string; family: { id: string } };
    expect(body.role).toBe("parent");
    expect(body.family.id).toBe(fam.familyId);
  });

  it("returns kid context for a kid token", async () => {
    const fam = await seedFamily();
    const res = await callApp("GET", "/me", { token: fam.kidTokens[0] });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      role: string;
      kid: { id: string; displayName: string; avatarEmoji: string; avatarColor: string };
    };
    expect(body.role).toBe("kid");
    expect(body.kid.id).toBe(fam.kidIds[0]);
    // Wire format must be camelCase so KidDto on Android parses without aliasing.
    expect(body.kid.displayName).toBeTruthy();
    expect(body.kid.avatarEmoji).toBeTruthy();
    expect(body.kid.avatarColor).toBeTruthy();
  });

  it("401 without authorization", async () => {
    const res = await callApp("GET", "/me");
    expect(res.status).toBe(401);
  });
});

describe("kids CRUD", () => {
  it("parent can add and list kids; archived kids are hidden", async () => {
    const fam = await seedFamily({ kidCount: 0 });
    const add = await callApp("POST", "/kids", {
      token: fam.parentToken,
      body: { displayName: "Ada", avatarEmoji: "🦄", avatarColor: "#AABBCC" },
    });
    expect(add.status).toBe(201);
    const created = (await add.json()) as { id: string };

    const list1 = (await (await callApp("GET", "/kids", { token: fam.parentToken })).json()) as {
      kids: { id: string }[];
    };
    expect(list1.kids.map((k) => k.id)).toContain(created.id);

    const del = await callApp("DELETE", `/kids/${created.id}`, { token: fam.parentToken });
    expect(del.status).toBe(200);

    const list2 = (await (await callApp("GET", "/kids", { token: fam.parentToken })).json()) as {
      kids: { id: string }[];
    };
    expect(list2.kids.map((k) => k.id)).not.toContain(created.id);
  });

  it("kids can list but cannot add", async () => {
    const fam = await seedFamily();
    const list = await callApp("GET", "/kids", { token: fam.kidTokens[0] });
    expect(list.status).toBe(200);
    const add = await callApp("POST", "/kids", {
      token: fam.kidTokens[0],
      body: { displayName: "Bob" },
    });
    expect(add.status).toBe(403);
  });

  it("rejects invalid color", async () => {
    const fam = await seedFamily({ kidCount: 0 });
    const res = await callApp("POST", "/kids", {
      token: fam.parentToken,
      body: { displayName: "Cat", avatarColor: "not-a-color" },
    });
    expect(res.status).toBe(400);
  });

  it("cross-family isolation: parent cannot see another family's kid", async () => {
    const a = await seedFamily();
    const b = await seedFamily();
    const res = await callApp("GET", `/kids/${b.kidIds[0]}/summary`, {
      token: a.parentToken,
    });
    expect(res.status).toBe(404);
  });
});
