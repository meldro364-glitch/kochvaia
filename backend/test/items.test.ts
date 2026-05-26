import { beforeEach, describe, expect, it } from "vitest";
import { callApp, resetDb, seedFamily } from "./helpers.ts";

beforeEach(async () => {
  await resetDb();
});

describe("items CRUD", () => {
  it("parent can add, list, edit, and delete items", async () => {
    const fam = await seedFamily();

    const add = await callApp("POST", "/items", {
      token: fam.parentToken,
      body: { name: "Pick the movie", costStars: 5, emoji: "🎬" },
    });
    expect(add.status).toBe(201);
    const created = (await add.json()) as { id: string; name: string; costStars: number };
    expect(created.costStars).toBe(5);

    const list = (await (await callApp("GET", "/items", { token: fam.parentToken })).json()) as {
      items: { id: string; name: string; costStars: number; emoji: string }[];
    };
    expect(list.items).toHaveLength(1);
    expect(list.items[0]!.name).toBe("Pick the movie");

    const patch = await callApp("PATCH", `/items/${created.id}`, {
      token: fam.parentToken,
      body: { costStars: 7 },
    });
    expect(patch.status).toBe(200);

    const list2 = (await (await callApp("GET", "/items", { token: fam.parentToken })).json()) as {
      items: { costStars: number }[];
    };
    expect(list2.items[0]!.costStars).toBe(7);

    const del = await callApp("DELETE", `/items/${created.id}`, { token: fam.parentToken });
    expect(del.status).toBe(200);

    const list3 = (await (await callApp("GET", "/items", { token: fam.parentToken })).json()) as {
      items: unknown[];
    };
    expect(list3.items).toHaveLength(0);
  });

  it("kid sessions can list but cannot mutate", async () => {
    const fam = await seedFamily();
    await callApp("POST", "/items", {
      token: fam.parentToken,
      body: { name: "Dinner of your choice", costStars: 10 },
    });
    const list = await callApp("GET", "/items", { token: fam.kidTokens[0] });
    expect(list.status).toBe(200);
    const body = (await list.json()) as { items: unknown[] };
    expect(body.items).toHaveLength(1);

    const add = await callApp("POST", "/items", {
      token: fam.kidTokens[0],
      body: { name: "Sneak attempt", costStars: 1 },
    });
    expect(add.status).toBe(403);
  });

  it("rejects invalid cost values", async () => {
    const fam = await seedFamily();
    const zero = await callApp("POST", "/items", {
      token: fam.parentToken,
      body: { name: "Bad", costStars: 0 },
    });
    expect(zero.status).toBe(400);

    const negative = await callApp("POST", "/items", {
      token: fam.parentToken,
      body: { name: "Bad", costStars: -3 },
    });
    expect(negative.status).toBe(400);

    const huge = await callApp("POST", "/items", {
      token: fam.parentToken,
      body: { name: "Bad", costStars: 1_000_000 },
    });
    expect(huge.status).toBe(400);
  });

  it("supports unicode names (Hebrew, emoji)", async () => {
    const fam = await seedFamily();
    const res = await callApp("POST", "/items", {
      token: fam.parentToken,
      body: { name: "ארוחת ערב לבחירתך", costStars: 8, emoji: "🍕" },
    });
    expect(res.status).toBe(201);
    const list = (await (await callApp("GET", "/items", { token: fam.parentToken })).json()) as {
      items: { name: string; emoji: string }[];
    };
    expect(list.items[0]!.name).toBe("ארוחת ערב לבחירתך");
    expect(list.items[0]!.emoji).toBe("🍕");
  });

  it("cross-family isolation: parent A cannot edit/delete family B's item", async () => {
    const a = await seedFamily();
    const b = await seedFamily();
    const add = await callApp("POST", "/items", {
      token: b.parentToken,
      body: { name: "B's item", costStars: 3 },
    });
    const created = (await add.json()) as { id: string };

    const patch = await callApp("PATCH", `/items/${created.id}`, {
      token: a.parentToken,
      body: { name: "hijacked" },
    });
    expect(patch.status).toBe(404);

    const del = await callApp("DELETE", `/items/${created.id}`, { token: a.parentToken });
    expect(del.status).toBe(404);
  });
});
