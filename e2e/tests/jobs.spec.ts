import { expect, test, type Page } from '@playwright/test'
import { goTo } from './helpers'

async function registerAndOpenJobs(page: Page) {
  const email = `e2e-jobs-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
  await goTo(page, 'Jobs')
}

async function addJob(page: Page, company: string, title: string) {
  await page.getByRole('link', { name: 'Add job' }).click()
  await page.getByLabel('Company').fill(company)
  await page.getByLabel('Job title').fill(title)
  await page.getByLabel('Location').fill('Remote')
  await page.getByLabel('Job description').fill(`${title} at ${company}. Requires Java, Spring Boot and React.`)
  await page.getByRole('button', { name: 'Save job' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await page.getByRole('link', { name: 'All jobs' }).click()
}

test('add, search, filter, edit and delete jobs', async ({ page }) => {
  await registerAndOpenJobs(page)
  await expect(page.getByText('No jobs yet')).toBeVisible()

  await addJob(page, 'Globex', 'Platform Engineer')
  await addJob(page, 'Initech', 'Frontend Developer')
  await expect(page.getByRole('table', { name: 'Saved jobs' }).getByRole('row')).toHaveCount(3)

  // Search is applied on the server and reflected in the URL.
  await page.getByLabel('Search').fill('globex')
  await expect(page).toHaveURL(/q=globex/)
  await expect(page.getByRole('link', { name: 'Platform Engineer' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Frontend Developer' })).toHaveCount(0)

  await page.getByLabel('Search').fill('nothing-matches-this')
  await expect(page.getByText('No matching jobs')).toBeVisible()
  await page.getByRole('button', { name: 'Clear filters' }).click()
  await expect(page.getByRole('link', { name: 'Frontend Developer' })).toBeVisible()

  // Sorting by company A–Z puts Globex before Initech.
  await page.getByLabel('Sort by').selectOption('company-asc')
  await expect(page.getByRole('table').getByRole('row').nth(1)).toContainText('Globex')

  // Edit persists.
  await page.getByRole('link', { name: 'Platform Engineer' }).click()
  await page.getByRole('button', { name: 'Edit' }).click()
  await page.getByLabel('Job title').fill('Staff Platform Engineer')
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page.getByRole('heading', { name: 'Staff Platform Engineer' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Staff Platform Engineer' })).toBeVisible()

  // Delete requires confirmation.
  page.once('dialog', (dialog) => void dialog.accept())
  await page.getByRole('button', { name: 'Delete' }).click()
  await expect(page.getByRole('link', { name: 'Staff Platform Engineer' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Frontend Developer' })).toBeVisible()
})

test('jobs list paginates on the server', async ({ page, request }) => {
  await registerAndOpenJobs(page)
  const token = await page.evaluate(() => JSON.parse(localStorage.getItem('joblens.session') ?? '{}').token as string)
  for (let i = 1; i <= 12; i++) {
    const response = await request.post('http://localhost:8081/api/jobs', {
      headers: { Authorization: `Bearer ${token}` },
      data: { company: `Company ${String(i).padStart(2, '0')}`, title: 'Engineer', jobDescription: 'desc' },
    })
    expect(response.status()).toBe(201)
  }
  await page.reload()
  await page.getByLabel('Sort by').selectOption('company-asc')
  await expect(page.getByText('Page 1 of 2 · 12 total')).toBeVisible()
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(11)

  await page.getByRole('button', { name: 'Next' }).click()
  await expect(page.getByText('Page 2 of 2 · 12 total')).toBeVisible()
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(3)
  await expect(page.getByText('Company 12')).toBeVisible()
})

test("one user cannot open another user's job", async ({ page, browser }) => {
  await registerAndOpenJobs(page)
  await addJob(page, 'Secret Corp', 'Hidden Role')
  await page.getByRole('link', { name: 'Hidden Role' }).click()
  const jobUrl = page.url()

  const other = await browser.newPage()
  const email = `e2e-other-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  await other.goto('/register')
  await other.getByLabel('Email').fill(email)
  await other.getByLabel('Password').fill('correct-horse-battery')
  await other.getByRole('button', { name: 'Create account' }).click()
  await expect(other.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
  await other.goto(jobUrl)
  await expect(other.getByText('Job not found')).toBeVisible()
  await expect(other.getByText('Secret Corp')).toHaveCount(0)
  await other.close()
})
