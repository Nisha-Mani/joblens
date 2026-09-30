import path from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { API_URL } from './helpers'

const fixture = path.join(__dirname, '..', 'fixtures', 'resume.pdf')

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

async function addJob(page: Page, description: string) {
  await nav(page, 'Jobs')
  await page.getByRole('link', { name: 'Add job' }).click()
  await page.getByLabel('Company').fill('Globex')
  await page.getByLabel('Job title').fill('Platform Engineer')
  await page.getByLabel('Job description').fill(description)
  await page.getByRole('button', { name: 'Save job' }).click()
  await expect(page.getByRole('heading', { name: 'Platform Engineer' })).toBeVisible()
}

// AI runs against the deterministic mock provider (AI_PROVIDER=mock in playwright.config.ts).
test('analyze a job against an uploaded resume', async ({ page }) => {
  await register(page, 'analysis')
  await nav(page, 'Resume')
  await page.getByLabel(/upload a resume/i).setInputFiles(fixture)
  await expect(page.getByLabel('Name')).toHaveValue('Jane Developer')

  await addJob(page, '5+ years of experience. You will use Java, Spring Boot, Kubernetes and Docker.')
  await page.getByRole('button', { name: 'Analyze match' }).click()

  // Resume has Java and Spring Boot; Docker and Kubernetes are missing → 2 of 4 = 50%.
  await expect(page.getByText('Partial match')).toBeVisible()
  await expect(page.getByRole('meter', { name: 'Overall match score' })).toHaveAttribute('aria-valuenow', '50')
  const matching = page.getByRole('region', { name: 'Resume match' })
  const block = (title: string) => matching.locator('section', { has: page.getByRole('heading', { name: title }) })
  await expect(block('Matching skills').getByRole('listitem')).toHaveText(['Java', 'Spring Boot'])
  await expect(block('Missing skills').getByRole('listitem')).toHaveText(['Docker', 'Kubernetes'])
  await expect(matching.getByText(/5 years/)).toBeVisible()
  await expect(matching.getByText(/AI-generated; verify before acting on it/)).toBeVisible()

  // A second run is added to the history, and the result survives a reload.
  await page.getByRole('button', { name: 'Analyze again' }).click()
  await expect(page.getByText('Previous analyses')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('meter', { name: 'Overall match score' })).toHaveAttribute('aria-valuenow', '50')
  await expect(page.getByText('Previous analyses')).toBeVisible()
})

test('analysis asks for a resume when none is uploaded', async ({ page }) => {
  await register(page, 'analysis-empty')
  await addJob(page, 'We use Java.')
  await expect(page.getByText('Upload a resume first')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Analyze match' })).toHaveCount(0)
  await page.getByRole('link', { name: 'Go to Resume' }).click()
  await expect(page.getByRole('heading', { name: 'Resume', exact: true })).toBeVisible()
})

test("one user cannot read another user's analysis", async ({ page, browser, request }) => {
  await register(page, 'analysis-owner')
  await nav(page, 'Resume')
  await page.getByLabel(/upload a resume/i).setInputFiles(fixture)
  await expect(page.getByLabel('Name')).toHaveValue('Jane Developer')
  await addJob(page, 'Java and Docker role.')
  await page.getByRole('button', { name: 'Analyze match' }).click()
  await expect(page.getByRole('meter', { name: 'Overall match score' })).toBeVisible()

  const ownerToken = await page.evaluate(() => JSON.parse(localStorage.getItem('joblens.session') ?? '{}').token as string)
  const jobId = page.url().split('/').pop()
  const list = await request.get(`${API_URL}/api/jobs/${jobId}/analyses`, {
    headers: { Authorization: `Bearer ${ownerToken}` },
  })
  const analysisId = (await list.json())[0].id as string

  const other = await browser.newPage()
  await register(other, 'analysis-other')
  const otherToken = await other.evaluate(() => JSON.parse(localStorage.getItem('joblens.session') ?? '{}').token as string)
  const response = await request.get(`${API_URL}/api/analyses/${analysisId}`, {
    headers: { Authorization: `Bearer ${otherToken}` },
  })
  expect(response.status()).toBe(404)
  await other.close()
})
