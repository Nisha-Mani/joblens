import type { Page } from '@playwright/test'

/**
 * Click an item in the main navigation. Scoped to the nav landmark because page content can also
 * contain links with similar names (e.g. "Upload your resume"), which makes bare role queries ambiguous.
 */
export const goTo = (page: Page, name: string) =>
  page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name, exact: true }).click()
