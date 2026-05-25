import { beforeEach, describe, expect, it } from "vitest";
import { callApp, resetDb, seedFamily } from "./helpers.ts";

beforeEach(async () => {
  await resetDb();
});

describe("POST /kids/:id/seen", () => {
  it("first visit returns empty + marks caught up", async () => {
    const fam = await seedFamily();
    const today = new Date().toISOString().slice(0, 10);
    // Award one star BEFORE the kid has ever opened the profile.
    await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: today },
    });
    const res = await callApp("POST", `/kids/${fam.kidIds[0]}/seen`, {
      token: fam.kidTokens[0],
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      firstVisit: boolean;
      newStars: unknown[];
      newDeductions: unknown[];
    };
    expect(body.firstVisit).toBe(true);
    expect(body.newStars).toHaveLength(0);
    expect(body.newDeductions).toHaveLength(0);
  });

  it("returns only stars/deductions awarded since previous call, then advances", async () => {
    const fam = await seedFamily();
    const today = new Date().toISOString().slice(0, 10);

    // First open — establishes checkpoint.
    await callApp("POST", `/kids/${fam.kidIds[0]}/seen`, { token: fam.kidTokens[0] });

    // Award a star + deduct — these should both show up on next /seen.
    await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: today },
    });
    await callApp("POST", `/kids/${fam.kidIds[0]}/deductions`, {
      token: fam.parentToken,
      body: { count: 1, reason: "treat" },
    });

    const res = await callApp("POST", `/kids/${fam.kidIds[0]}/seen`, { token: fam.kidTokens[0] });
    const body = (await res.json()) as {
      firstVisit: boolean;
      newStars: { date: string }[];
      newDeductions: { count: number; reason: string | null }[];
    };
    expect(body.firstVisit).toBe(false);
    expect(body.newStars).toHaveLength(1);
    expect(body.newStars[0]!.date).toBe(today);
    expect(body.newDeductions).toHaveLength(1);
    expect(body.newDeductions[0]!.count).toBe(1);
    expect(body.newDeductions[0]!.reason).toBe("treat");

    // Third call — nothing new since.
    const res2 = await callApp("POST", `/kids/${fam.kidIds[0]}/seen`, { token: fam.kidTokens[0] });
    const body2 = (await res2.json()) as { newStars: unknown[]; newDeductions: unknown[] };
    expect(body2.newStars).toHaveLength(0);
    expect(body2.newDeductions).toHaveLength(0);
  });

  it("checkpoints are per-(device, viewed-kid) so siblings track independently", async () => {
    const fam = await seedFamily({ kidCount: 2 });
    const today = new Date().toISOString().slice(0, 10);

    // Device 0 opens its OWN profile (catch up).
    await callApp("POST", `/kids/${fam.kidIds[0]}/seen`, { token: fam.kidTokens[0] });

    // A star is given to SIBLING (kid 1).
    await callApp("POST", `/kids/${fam.kidIds[1]}/stars`, {
      token: fam.parentToken,
      body: { date: today },
    });

    // Device 0 opens sibling profile — first visit there, so no delta.
    const first = await callApp("POST", `/kids/${fam.kidIds[1]}/seen`, {
      token: fam.kidTokens[0],
    });
    const firstBody = (await first.json()) as { firstVisit: boolean; newStars: unknown[] };
    expect(firstBody.firstVisit).toBe(true);
    expect(firstBody.newStars).toHaveLength(0);

    // Another star to sibling, then re-open.
    const y = new Date(Date.now() - 86400_000).toISOString().slice(0, 10);
    await callApp("POST", `/kids/${fam.kidIds[1]}/stars`, {
      token: fam.parentToken,
      body: { date: y },
    });
    const second = await callApp("POST", `/kids/${fam.kidIds[1]}/seen`, {
      token: fam.kidTokens[0],
    });
    const secondBody = (await second.json()) as { newStars: { date: string }[] };
    expect(secondBody.newStars).toHaveLength(1);
    expect(secondBody.newStars[0]!.date).toBe(y);
  });

  it("rejects parent tokens", async () => {
    const fam = await seedFamily();
    const res = await callApp("POST", `/kids/${fam.kidIds[0]}/seen`, { token: fam.parentToken });
    expect(res.status).toBe(403);
  });
});
