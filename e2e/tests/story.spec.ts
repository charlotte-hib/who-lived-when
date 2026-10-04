import { expect, test, type Locator } from "@playwright/test";

type StoryCard = {
  text: string | null;
  person: { bioShort: string | null } | null;
  life: { description: string } | null;
  event: { description: string } | null;
};

/** The paragraph a card is read from, as `story-player.tsx` renders it. */
const bodyOf = (card: StoryCard) => card.event?.description ?? card.text ?? card.person?.bioShort ?? card.life?.description ?? "";

/**
 * Taps the middle of an element and lets the page decide what is hit. The card's text is meant to let taps
 * through to the page-turning zones under it, so Playwright's check that the text itself receives the tap
 * would fail: `force` skips it.
 */
async function tapThrough(target: Locator, hasTouch: boolean) {
  if (hasTouch) await target.tap({ force: true });
  else await target.click({ force: true });
}

// Regression: the card's text covers most of a phone screen and used to swallow taps, so the story did not turn.
test("the featured story turns page by page with taps on the card text", async ({ page, request, hasTouch }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Play the story", exact: true }).click();
  await expect(page).toHaveURL(/\/moment\/[^/]+\/story$/);

  const momentId = new URL(page.url()).pathname.split("/")[2];
  const response = await request.get(`/api/moments/${momentId}/story`);
  expect(response.ok()).toBe(true);
  const { cards } = (await response.json()) as { cards: StoryCard[] };
  expect(cards.length).toBeGreaterThan(1);

  // `?card=` is 1-based and the doors come after the last card.
  for (const [index, card] of cards.entries()) {
    const body = page.getByText(bodyOf(card));
    await expect(body).toBeVisible();
    await tapThrough(body, hasTouch);
    await expect(page).toHaveURL(new RegExp(`[?&]card=${index + 2}$`));
  }

  await expect(page.getByRole("heading", { name: "Step through another door" })).toBeVisible();
});
