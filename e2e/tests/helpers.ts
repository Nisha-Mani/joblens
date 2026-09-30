import type { Page } from '@playwright/test'

/**
 * Click an item in the main navigation. Scoped to the nav landmark because page content can also
 * contain links with similar names (e.g. "Upload your resume"), which makes bare role queries ambiguous.
 */
export const goTo = (page: Page, name: string) =>
  page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name, exact: true }).click()

/** Base URL for direct API calls in tests. Defaults to the local E2E backend; override to test a deployment. */
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8081'
