import { beforeEach, describe, expect, it } from "vitest";
import { callApp, resetDb, seedFamily } from "./helpers.ts";

beforeEach(async () => {
  await resetDb();
});

describe("POST /kids/:id/stars", () => {
  it("awards one star and rejects duplicates for the same day", async () => {
    const fam = await seedFamily();
    const today = new Date().toISOString().slice(0, 10);

    const ok = await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: today },
    });
    expect(ok.status).toBe(201);

    const dup = await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: today },
    });
    expect(dup.status).toBe(409);
    const body = (await dup.json()) as { error: string };
    expect(body.error).toBe("star_already_awarded_for_date");
  });

  it("rejects future dates", async () => {
    const fam = await seedFamily();
    const future = new Date(Date.now() + 5 * 86400_000).toISOString().slice(0, 10);
    const res = await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: future },
    });
    expect(res.status).toBe(400);
  });

  it("rejects when caller is a kid session", async () => {
    const fam = await seedFamily();
    const today = new Date().toISOString().slice(0, 10);
    const res = await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.kidTokens[0],
      body: { date: today },
    });
    expect(res.status).toBe(403);
  });
});

describe("DELETE /kids/:id/stars/:date", () => {
  it("removes an unused star", async () => {
    const fam = await seedFamily();
    const today = new Date().toISOString().slice(0, 10);
    await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: today },
    });
    const res = await callApp("DELETE", `/kids/${fam.kidIds[0]}/stars/${today}`, {
      token: fam.parentToken,
    });
    expect(res.status).toBe(200);
  });

  it("refuses to remove an already-used star", async () => {
    const fam = await seedFamily();
    const today = new Date().toISOString().slice(0, 10);
    await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: today },
    });
    await callApp("POST", `/kids/${fam.kidIds[0]}/deductions`, {
      token: fam.parentToken,
      body: { count: 1 },
    });
    const res = await callApp("DELETE", `/kids/${fam.kidIds[0]}/stars/${today}`, {
      token: fam.parentToken,
    });
    expect(res.status).toBe(409);
  });
});

describe("POST /kids/:id/deductions (oldest-first)", () => {
  async function awardOn(fam: Awaited<ReturnType<typeof seedFamily>>, date: string) {
    const res = await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date },
    });
    expect(res.status).toBe(201);
  }

  it("consumes oldest-earned stars first, regardless of award order", async () => {
    const fam = await seedFamily();
    const dates = [0, 1, 2, 3, 4].map((d) =>
      new Date(Date.now() - d * 86400_000).toISOString().slice(0, 10),
    );
    // Insert in deliberately scrambled order: deduction must order by earned_date.
    await awardOn(fam, dates[2]!);
    await awardOn(fam, dates[0]!);
    await awardOn(fam, dates[4]!);
    await awardOn(fam, dates[1]!);
    await awardOn(fam, dates[3]!);

    const ded = await callApp("POST", `/kids/${fam.kidIds[0]}/deductions`, {
      token: fam.parentToken,
      body: { count: 3, reason: "screen time" },
    });
    expect(ded.status).toBe(201);

    const from = dates[4]!;
    const to = dates[0]!;
    const daysRes = await callApp("GET", `/kids/${fam.kidIds[0]}/days?from=${from}&to=${to}`, {
      token: fam.parentToken,
    });
    expect(daysRes.status).toBe(200);
    const daysBody = (await daysRes.json()) as {
      days: { date: string; status: "none" | "given" | "used" }[];
    };
    const statuses = daysBody.days.map((d) => d.status);
    expect(statuses).toEqual(["used", "used", "used", "given", "given"]);
  });

  it("refuses to deduct more than available", async () => {
    const fam = await seedFamily();
    const today = new Date().toISOString().slice(0, 10);
    await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: today },
    });
    const res = await callApp("POST", `/kids/${fam.kidIds[0]}/deductions`, {
      token: fam.parentToken,
      body: { count: 5 },
    });
    expect(res.status).toBe(409);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe("insufficient_stars");
  });
});

describe("GET /kids/:id/summary", () => {
  it("counts earned / used / available correctly", async () => {
    const fam = await seedFamily();
    const dates = [0, 1, 2].map((d) =>
      new Date(Date.now() - d * 86400_000).toISOString().slice(0, 10),
    );
    for (const d of dates) {
      await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
        token: fam.parentToken,
        body: { date: d },
      });
    }
    await callApp("POST", `/kids/${fam.kidIds[0]}/deductions`, {
      token: fam.parentToken,
      body: { count: 1 },
    });
    const res = await callApp("GET", `/kids/${fam.kidIds[0]}/summary`, {
      token: fam.parentToken,
    });
    const body = (await res.json()) as {
      totalEarned: number;
      totalUsed: number;
      availableStars: number;
    };
    expect(body.totalEarned).toBe(3);
    expect(body.totalUsed).toBe(1);
    expect(body.availableStars).toBe(2);
  });
});

describe("GET /kids/:id/days range expansion", () => {
  it("returns 'none' for days without a star", async () => {
    const fam = await seedFamily();
    const today = new Date();
    const ymd = (d: Date) => d.toISOString().slice(0, 10);
    const day = (offset: number) => {
      const x = new Date(today);
      x.setUTCDate(x.getUTCDate() - offset);
      return ymd(x);
    };
    // Award only on the 3rd-oldest day in a 7-day window.
    await callApp("POST", `/kids/${fam.kidIds[0]}/stars`, {
      token: fam.parentToken,
      body: { date: day(4) },
    });
    const res = await callApp("GET", `/kids/${fam.kidIds[0]}/days?from=${day(6)}&to=${day(0)}`, {
      token: fam.parentToken,
    });
    const body = (await res.json()) as {
      days: { date: string; status: string }[];
    };
    expect(body.days).toHaveLength(7);
    const givenCount = body.days.filter((d) => d.status === "given").length;
    const noneCount = body.days.filter((d) => d.status === "none").length;
    expect(givenCount).toBe(1);
    expect(noneCount).toBe(6);
  });
});
