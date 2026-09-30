import { expect, test, type Page } from '@playwright/test'

const nav = (page: Page, name: string) =>
  page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name, exact: true }).click()

async function register(page: Page, label: string) {
  const email = `e2e-${label}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
}

async function addJob(page: Page, company: string, title: string) {
  await nav(page, 'Jobs')
  await page.getByRole('link', { name: 'Add job' }).click()
  await page.getByLabel('Company').fill(company)
  await page.getByLabel('Job title').fill(title)
  await page.getByLabel('Job description').fill(`${title} at ${company}`)
  await page.getByRole('button', { name: 'Save job' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
}

test('track a job through the application pipeline', async ({ page }) => {
  await register(page, 'apps')
  await addJob(page, 'Globex', 'Platform Engineer')

  // Start tracking from the job page.
  await page.getByRole('link', { name: 'Track this application' }).click()
  await expect(page.getByRole('combobox', { name: 'Job', exact: true })).toHaveValue(/.+/)
  await page.getByLabel('Status').selectOption('APPLIED')
  await page.getByLabel('Notes').fill('Referred by a friend')
  await page.getByRole('button', { name: 'Save application' }).click()

  await expect(page.getByRole('heading', { name: 'Platform Engineer' })).toBeVisible()
  await expect(page.getByText('Referred by a friend')).toBeVisible()
  await expect(page.getByText('Created as Applied')).toBeVisible()

  // The job page now links to the application instead of offering to track it.
  await page.getByRole('link', { name: 'View job posting' }).click()
  await expect(page.getByRole('link', { name: 'View application' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Track this application' })).toHaveCount(0)

  // Change status inline from the list.
  await nav(page, 'Applications')
  await page.getByLabel('Change status for Platform Engineer at Globex').selectOption('SCREENING')
  await expect(page.getByLabel('Change status for Platform Engineer at Globex')).toHaveValue('SCREENING')

  // Schedule an interview and move to Interview via edit.
  await page.getByRole('link', { name: 'Platform Engineer' }).click()
  await page.getByRole('button', { name: 'Edit' }).click()
  await page.getByLabel('Status').selectOption('INTERVIEW')
  await page.getByLabel('Interview').fill('2030-05-20T10:30')
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible()
  await expect(page.getByText('May 20, 2030')).toBeVisible()

  // History lists every transition, newest first.
  const history = page.getByRole('list').last().getByRole('listitem')
  await expect(history).toHaveCount(3)
  await expect(history.first()).toContainText('Screening → Interview')
  await expect(history.last()).toContainText('Created as Applied')

  // Persisted across reload.
  await page.reload()
  await expect(page.getByText('May 20, 2030')).toBeVisible()
})

test('filter, search, sort and delete applications', async ({ page }) => {
  await register(page, 'apps-list')
  for (const [company, title, status] of [
    ['Alpha Corp', 'Frontend Dev', 'APPLIED'],
    ['Beta LLC', 'Backend Dev', 'OFFER'],
  ]) {
    await addJob(page, company, title)
    await page.getByRole('link', { name: 'Track this application' }).click()
    await page.getByLabel('Status').selectOption(status)
    await page.getByRole('button', { name: 'Save application' }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
  }

  await nav(page, 'Applications')
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(3)

  await page.getByLabel('Status', { exact: true }).selectOption('OFFER')
  await expect(page).toHaveURL(/status=OFFER/)
  await expect(page.getByRole('link', { name: 'Backend Dev' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Frontend Dev' })).toHaveCount(0)

  await page.getByLabel('Status', { exact: true }).selectOption('')
  await page.getByLabel('Search').fill('alpha')
  await expect(page).toHaveURL(/q=alpha/)
  await expect(page.getByRole('link', { name: 'Frontend Dev' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Backend Dev' })).toHaveCount(0)

  await page.getByLabel('Search').fill('')
  await page.getByLabel('Sort by').selectOption('company-asc')
  await expect(page.getByRole('table').getByRole('row').nth(1)).toContainText('Alpha Corp')

  // Deleting an application keeps the job.
  await page.getByRole('link', { name: 'Frontend Dev' }).click()
  page.once('dialog', (dialog) => void dialog.accept())
  await page.getByRole('button', { name: 'Delete' }).click()
  await expect(page.getByRole('link', { name: 'Frontend Dev' })).toHaveCount(0)
  await nav(page, 'Jobs')
  await expect(page.getByRole('link', { name: 'Frontend Dev' })).toBeVisible()
})

test("one user cannot view another user's application", async ({ page, browser }) => {
  await register(page, 'apps-owner')
  await addJob(page, 'Private Co', 'Secret Role')
  await page.getByRole('link', { name: 'Track this application' }).click()
  await page.getByRole('button', { name: 'Save application' }).click()
  await expect(page.getByRole('heading', { name: 'Secret Role' })).toBeVisible()
  const url = page.url()

  const other = await browser.newPage()
  await register(other, 'apps-other')
  await other.goto(url)
  await expect(other.getByText('Application not found')).toBeVisible()
  await expect(other.getByText('Private Co')).toHaveCount(0)
  await other.close()
})
