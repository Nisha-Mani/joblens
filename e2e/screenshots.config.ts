import { defineConfig } from '@playwright/test'
import base from './playwright.config'

// Not part of the test suite: `npx playwright test -c screenshots.config.ts` regenerates docs/screenshots
// from a fictional demo account, using the same dev servers and the free mock AI provider.
export default defineConfig({
  ...base,
  testDir: './screenshots',
  reporter: 'list',
  workers: 1,
  fullyParallel: false,
})
