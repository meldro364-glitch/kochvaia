import { beforeEach, describe, expect, it } from "vitest";
import { callApp, resetDb, seedFamily } from "./helpers.ts";

beforeEach(async () => {
  await resetDb();
});

describe("GET /dashboard", () => {
  it("returns combined snapshot for a parent in a single round-trip", async () => {
    const fam = await seedFamily({ kidCount: 2 });

    // Seed: 3 stars for kid 0 (2 unused, 1 used via a deduction), 0 for kid 1.
    await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: "2026-05-20" },
    });
    await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: "2026-05-21" },
    });
    await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: "2026-05-22" },
    });
    await callApp("POST", `/kids/${fam.kidIds[0]}/deductions`, {
      token: fam.parentToken,
      body: { count: 1, reason: "candy" },
    });

    // Seed: 1 item in the rewards catalog.
    await callApp("POST", "/items", {
      token: fam.parentToken,
      body: { name: "Ice cream", costStars: 5, emoji: "🍦" },
    });

    const res = await callApp("GET", "/dashboard", { token: fam.parentToken });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      role: string;
      family: { id: string; name: string; tz: string };
      parent: { id: string; email: string | null; display_name: string };
      kid: null;
      kids: { id: string; displayName: string }[];
      items: { id: string; name: string; costStars: number }[];
      summaries: Record<
        string,
        { availableStars: number; totalEarned: number; totalUsed: number }
      >;
    };

    expect(body.role).toBe("parent");
    expect(body.family.id).toBe(fam.familyId);
    expect(body.parent.id).toBe(fam.parentId);
    expect(body.kid).toBeNull();
    expect(body.kids.map((k) => k.id).sort()).toEqual([...fam.kidIds].sort());
    expect(body.items).toHaveLength(1);
    expect(body.items[0]!.name).toBe("Ice cream");

    // Aggregate query must produce the same numbers /kids/:id/summary would.
    expect(body.summaries[fam.kidIds[0]!]).toEqual({
      availableStars: 2,
      totalEarned: 3,
      totalUsed: 1,
    });
    // Kid with no stars yet still shows up with zeros (LEFT JOIN behavior).
    expect(body.summaries[fam.kidIds[1]!]).toEqual({
      availableStars: 0,
      totalEarned: 0,
      totalUsed: 0,
    });
  });

  it("returns kid context with siblings for a kid token", async () => {
    const fam = await seedFamily({ kidCount: 3 });
    const res = await callApp("GET", "/dashboard", { token: fam.kidTokens[0] });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      role: string;
      parent: null;
      kid: { id: string; displayName: string };
      kids: { id: string }[];
      summaries: Record<string, unknown>;
    };
    expect(body.role).toBe("kid");
    expect(body.parent).toBeNull();
    expect(body.kid.id).toBe(fam.kidIds[0]);
    // Family-wide kid list is returned (siblings derived by filtering on the
    // client). All three should be present, including the caller.
    expect(body.kids).toHaveLength(3);
    // Summary keys cover every kid in the family.
    expect(Object.keys(body.summaries).sort()).toEqual([...fam.kidIds].sort());
  });

  it("401 without authorization", async () => {
    const res = await callApp("GET", "/dashboard");
    expect(res.status).toBe(401);
  });

  it("archived kids are excluded from kids list AND summaries", async () => {
    const fam = await seedFamily({ kidCount: 2 });
    await callApp("DELETE", `/kids/${fam.kidIds[0]}`, { token: fam.parentToken });

    const res = await callApp("GET", "/dashboard", { token: fam.parentToken });
    const body = (await res.json()) as {
      kids: { id: string }[];
      summaries: Record<string, unknown>;
    };
    expect(body.kids.map((k) => k.id)).toEqual([fam.kidIds[1]]);
    expect(Object.keys(body.summaries)).toEqual([fam.kidIds[1]]);
  });
});
