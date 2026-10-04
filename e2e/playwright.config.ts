import { defineConfig, devices } from "@playwright/test";

const ci = !!process.env.CI;

/**
 * End-to-end tests against a running site: `docker compose` in CI, or the dev servers locally.
 * Nothing here starts the app, so the same tests can point at any environment through BASE_URL.
 */
export default defineConfig({
  testDir: "tests",
  fullyParallel: true,
  forbidOnly: ci,
  retries: ci ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "desktop",
      // Playwright's own Chromium does not run on older macOS, so local runs use the installed Chrome.
      use: { ...devices["Desktop Chrome"], channel: ci ? undefined : "chrome" },
    },
    {
      name: "phone",
      // Chromium with a phone's viewport and a touchscreen: the story is turned by taps.
      use: { ...devices["Pixel 7"], channel: ci ? undefined : "chrome" },
    },
  ],
});
