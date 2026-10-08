import { expect, test, type Page } from "@playwright/test";

const searchBox = (page: Page) => page.getByRole("combobox", { name: "Search people and moments" });

// Search goes through the backend (accent-insensitive), and a person opens in a panel over the page through
// Next.js route interception (app/@panel), so closing it returns to where it was opened.
test("search opens a person in a panel over the page", async ({ page }) => {
  await page.goto("/");
  await searchBox(page).fill("zola");
  await page.getByRole("option", { name: /Émile Zola/ }).click();

  await expect(page).toHaveURL(/\/person\/emile-zola$/);
  const panel = page.getByRole("dialog", { name: "Someone who lived it" });
  await expect(panel.getByRole("heading", { level: 1, name: "Émile Zola" })).toBeVisible();

  await panel.getByRole("button", { name: "Close" }).click();
  await expect(panel).toBeHidden();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("heading", { name: "Where the stories are" })).toBeVisible();
});

// The backend searches from 2 letters: until then the box says what to type, rather than showing nothing.
test("the search box says what to type, and when nothing matches", async ({ page }) => {
  await page.goto("/");
  const box = searchBox(page);
  await expect(box).toHaveAttribute("maxlength", "100");

  await box.click();
  await expect(page.getByText("Type a name, a place or a decade", { exact: true })).toBeVisible();

  await box.pressSequentially("q");
  await expect(page.getByText("Type at least 2 letters")).toBeVisible();

  await box.pressSequentially("x");
  await expect(page.getByText("No people or moments match “qx”")).toBeVisible();
  await expect(page.getByRole("option")).toHaveCount(0);
});

// Results come as you type, with the first one highlighted: Enter opens it.
test("Enter opens the first result", async ({ page }) => {
  await page.goto("/");
  const box = searchBox(page);
  await box.pressSequentially("zola");
  await expect(page.getByRole("option", { name: /Émile Zola/ })).toBeVisible();

  await box.press("Enter");
  await expect(page).toHaveURL(/\/person\/emile-zola$/);
});

test("a slow search shows that it is loading, and a failed one says so", async ({ page }) => {
  let release = () => {};
  const held = new Promise<void>((resolve) => (release = resolve));
  let failing = false;
  await page.route("**/api/search?**", async (route) => {
    if (failing) return route.fulfill({ status: 503 });
    await held;
    await route.continue();
  });

  await page.goto("/");
  const box = searchBox(page);
  await box.pressSequentially("zola");
  await expect(page.getByText("Searching…")).toBeVisible();
  await expect(page.locator("[aria-busy=true]")).toBeVisible();

  release();
  await expect(page.getByRole("option", { name: /Émile Zola/ })).toBeVisible();
  await expect(page.locator("[aria-busy=true]")).toHaveCount(0);

  failing = true;
  await box.pressSequentially("x");
  await expect(page.getByText("Search isn’t available right now")).toBeVisible();
  await expect(page.getByRole("option")).toHaveCount(0);
});
