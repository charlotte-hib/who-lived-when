import { expect, test, type APIRequestContext } from "@playwright/test";

/**
 * The per-visitor limit (`frontend/proxy.ts`). In production Caddy sets X-Forwarded-For; here the test sets it, with
 * addresses drawn at random from documentation and benchmarking ranges, so each run and each retry starts with full
 * buckets. Requests without the header, like every other test's, are not limited.
 */
const random = (n: number) => Math.floor(Math.random() * n);

/** Sends requests, each from [address](), until one is refused, and returns how many went through, and the refusal. */
async function exhaust(request: APIRequestContext, address: () => string) {
  let allowed = 0;
  for (let batch = 0; batch < 20; batch++) {
    const responses = await Promise.all(
      Array.from({ length: 50 }, () => request.get("/api/search?q=a", { headers: { "x-forwarded-for": address() } })),
    );
    const refused = responses.find((r) => r.status() === 429);
    allowed += responses.filter((r) => r.ok()).length;
    if (refused) return { allowed, refused };
  }
  return { allowed, refused: null };
}

const status = async (request: APIRequestContext, address: string) =>
  (await request.get("/api/search?q=a", { headers: { "x-forwarded-for": address } })).status();

test("one visitor who keeps asking is told to wait, others are not", async ({ request }) => {
  const visitor = `198.18.${random(256)}.${1 + random(254)}`;
  const { allowed, refused } = await exhaust(request, () => visitor);

  // Enough for a fast reader opening pages in a row, but not for copying the site.
  expect(allowed).toBeGreaterThanOrEqual(100);
  expect(refused).not.toBeNull();
  expect(Number(refused!.headers()["retry-after"])).toBeGreaterThanOrEqual(1);

  expect(await status(request, `198.19.${random(256)}.${1 + random(254)}`)).toBe(200);
  // Not limited: the server itself and private networks.
  expect(await status(request, "127.0.0.1")).toBe(200);
});

test("an IPv6 visitor is counted by their /64", async ({ request }) => {
  const hex = () => random(0x10000).toString(16);
  const block = `2001:db8:${hex()}:${hex()}`;
  // Every request comes from another address in the same /64, so only a shared bucket can run out.
  const { refused } = await exhaust(request, () => `${block}:${hex()}:${hex()}:${hex()}:${hex()}`);
  expect(refused).not.toBeNull();

  expect(await status(request, `2001:db8:${hex()}::1`)).toBe(200);
});
