import { expect, test } from "@playwright/test";

// A moment page is rendered on the server from the backend's moment, and its documented events open in a panel
// with everyone who took part and their role (seeded in backend/site/src/main/resources/seed/events.json).
test("a documented event shows who was there", async ({ page }) => {
  await page.goto("/moment/paris-1870s");
  const event = page.getByRole("article").filter({ has: page.getByRole("heading", { name: /The first Impressionist exhibition/ }) });
  await event.getByRole("button", { name: "Who was there" }).click();

  const panel = page.getByRole("dialog", { name: "Documented event" });
  await expect(panel.getByRole("heading", { name: "The first Impressionist exhibition" })).toBeVisible();
  await expect(panel.getByRole("listitem")).toHaveText([/Claude Monet\s*exhibitor/, /Paul Cézanne\s*exhibitor/, /Berthe Morisot\s*exhibitor/]);
});
