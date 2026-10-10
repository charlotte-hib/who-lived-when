import { expect, test } from "@playwright/test";

// A person's page leads to the moment of the world around them, and each regime of their life (from
// sample/eras.jsonl) opens in the same panel as "Who governs" on a moment page.
test("a person's page leads to their moment and their eras", async ({ page }) => {
  await page.goto("/person/emile-zola?year=1875");

  await page.getByRole("button", { name: "The Third Republic begins" }).click();
  const panel = page.getByRole("dialog", { name: "Who governs" });
  await expect(panel.getByRole("heading", { name: "Third Republic", exact: true })).toBeVisible();
  await panel.getByRole("button", { name: "Close" }).click();
  await expect(panel).toBeHidden();

  await page.getByRole("link", { name: "Paris, 1870s", exact: true }).click();
  await expect(page).toHaveURL(/\/moment\/paris-1870s$/);
});

// In the panel opened from search, an era opens over the person, and closing it goes back to them.
test("an era opens over a person's panel and closes back to them", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("combobox", { name: "Search people and moments" }).fill("zola");
  await page.getByRole("option", { name: /Émile Zola/ }).click();
  const person = page.getByRole("dialog", { name: "Someone who lived it" });

  await person.getByRole("button", { name: "Born under the July Monarchy" }).click();
  const era = page.getByRole("dialog", { name: "Who governs" });
  await expect(era.getByRole("heading", { name: "July Monarchy", exact: true })).toBeVisible();

  await era.getByRole("button", { name: "Close" }).click();
  await expect(era).toBeHidden();
  await expect(person.getByRole("heading", { level: 1, name: "Émile Zola" })).toBeVisible();
});
