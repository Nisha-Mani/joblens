import path from 'node:path'
import { expect, test, type Page } from '@playwright/test'

const fixture = path.join(__dirname, '..', 'fixtures', 'resume.pdf')
const nav = (page: Page, name: string) =>
  page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name, exact: true }).click()

async function register(page: Page, label: string) {
  await page.goto('/register')
  await page.getByLabel('Email').fill(`e2e-${label}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
}

async function trackJob(page: Page, company: string, description: string, status: string) {
  await nav(page, 'Jobs')
  await page.getByRole('link', { name: 'Add job' }).click()
  await page.getByLabel('Company').fill(company)
  await page.getByLabel('Job title').fill(`Engineer at ${company}`)
  await page.getByLabel('Job description').fill(description)
  await page.getByRole('button', { name: 'Save job' }).click()
  await expect(page.getByRole('heading', { name: `Engineer at ${company}` })).toBeVisible()
  await page.getByRole('link', { name: 'Track this application' }).click()
  await page.getByLabel('Status', { exact: true }).selectOption(status)
  await page.getByRole('button', { name: 'Save application' }).click()
  await expect(page.getByRole('heading', { name: `Engineer at ${company}` })).toBeVisible()
}

test('a new user sees an empty state, not empty charts', async ({ page }) => {
  await register(page, 'dash-empty')
  await expect(page.getByText('Your dashboard is waiting for data')).toBeVisible()
  await expect(page.getByText('Applications by status')).toHaveCount(0)
  await nav(page, 'Analytics')
  await expect(page.getByText('Nothing to analyze yet')).toBeVisible()
})

test('dashboard and analytics reflect real tracked data', async ({ page }) => {
  await register(page, 'dash')
  await nav(page, 'Resume')
  await page.getByLabel(/upload a resume/i).setInputFiles(fixture)
  await expect(page.getByLabel('Name')).toHaveValue('Jane Developer')

  await trackJob(page, 'Globex', 'Java, Docker and Kubernetes required.', 'APPLIED')
  // Analyze this job (from its job page) so the dashboard has skill gaps to report.
  await page.getByRole('link', { name: 'View job posting' }).click()
  await page.getByRole('button', { name: 'Analyze match' }).click()
  await expect(page.getByRole('meter', { name: 'Overall match score' })).toBeVisible()
  await trackJob(page, 'Initech', 'We need Docker and Terraform.', 'APPLIED')
  await trackJob(page, 'Umbrella', 'Any role.', 'SAVED')

  // Move Initech through to an interview via the inline status control.
  await nav(page, 'Applications')
  const initech = page.getByLabel('Change status for Engineer at Initech at Initech')
  await initech.selectOption('SCREENING')
  await expect(initech).toHaveValue('SCREENING')
  await initech.selectOption('INTERVIEW')
  await expect(initech).toHaveValue('INTERVIEW')

  await nav(page, 'Dashboard')
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
  // Stat tiles are the <div>s inside the metrics <dl>.
  const tile = (name: string) => page.locator('dl > div').filter({ has: page.getByText(name, { exact: true }) })
  await expect(tile('Applications')).toContainText('2')          // applied: Globex + Initech
  await expect(tile('Applications')).toContainText('3 tracked')  // Umbrella is only saved
  await expect(tile('Interviews')).toContainText('1')
  await expect(tile('Offers')).toContainText('0')
  await expect(tile('Response rate')).toContainText('50%')       // Initech responded, Globex has not
  await expect(tile('Applied this month')).toContainText('2')

  const status = page.getByRole('region', { name: 'Applications by status' })
  await expect(status.getByRole('listitem').filter({ hasText: 'Applied' })).toContainText('1')
  await expect(status.getByRole('listitem').filter({ hasText: 'Interview' })).toContainText('1')
  await expect(status.getByRole('listitem').filter({ hasText: 'Saved' })).toContainText('1')

  // Only Globex was analysed: Docker and Kubernetes are missing from the resume, each counted once.
  const gaps = page.getByRole('region', { name: 'Top missing skills' })
  await expect(gaps.getByRole('listitem').first()).toContainText('Docker')

  await expect(page.getByRole('img', { name: /Applications per month over 12 months, 2 in total/ })).toBeVisible()
  await page.screenshot({ path: 'test-results/dashboard.png', fullPage: true })

  await nav(page, 'Analytics')
  await expect(tile('Interview rate')).toContainText('50%')
  const funnel = page.getByRole('region', { name: 'Application funnel' })
  await expect(funnel.getByRole('listitem').filter({ hasText: 'Interviewed' })).toContainText('1')
  await page.getByLabel('Time range').selectOption('6')
  await expect(page.getByRole('img', { name: /over 6 months/ })).toBeVisible()
  await page.screenshot({ path: 'test-results/analytics.png', fullPage: true })
})

test("dashboards are per user: a second account sees none of this data", async ({ browser }) => {
  const owner = await browser.newPage()
  await register(owner, 'dash-owner')
  await trackJob(owner, 'Private', 'Java.', 'APPLIED')
  const other = await browser.newPage()
  await register(other, 'dash-other')
  await expect(other.getByText('Your dashboard is waiting for data')).toBeVisible()
  await expect(other.getByText('Private')).toHaveCount(0)
  await owner.close()
  await other.close()
})
