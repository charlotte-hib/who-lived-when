import { expect, test } from "@playwright/test";

// Search goes through the backend (accent-insensitive), and a person opens in a panel over the page through
// Next.js route interception (app/@panel), so closing it returns to where it was opened.
test("search opens a person in a panel over the page", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("combobox", { name: "Search people and moments" }).fill("zola");
  await page.getByRole("option", { name: /Émile Zola/ }).click();

  await expect(page).toHaveURL(/\/person\/emile-zola$/);
  const panel = page.getByRole("dialog", { name: "Someone who lived it" });
  await expect(panel.getByRole("heading", { level: 1, name: "Émile Zola" })).toBeVisible();

  await panel.getByRole("button", { name: "Close" }).click();
  await expect(panel).toBeHidden();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("heading", { name: "Where the stories are" })).toBeVisible();
});
