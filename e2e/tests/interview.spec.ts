import { expect, test, type Page } from '@playwright/test'
import { goTo } from './helpers'

// The description below names four technologies → 4 technical + 3 behavioral + 2 project + 2 role-specific.
const GENERATED = 11

async function registerWithJob(page: Page, label: string) {
  const email = `e2e-${label}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()

  await goTo(page, 'Jobs')
  await page.getByRole('link', { name: 'Add job' }).click()
  await page.getByLabel('Company').fill('Globex')
  await page.getByLabel('Job title').fill('Platform Engineer')
  await page.getByLabel('Job description').fill('5+ years. You will use Java, Spring Boot, Kubernetes and Docker.')
  await page.getByRole('button', { name: 'Save job' }).click()
  await expect(page.getByRole('heading', { name: 'Platform Engineer' })).toBeVisible()
}

test('generate interview questions from a job, prepare notes and track progress', async ({ page }) => {
  await registerWithJob(page, 'interview')

  await page.getByRole('link', { name: 'Prepare for the interview' }).click()
  await expect(page.getByRole('heading', { name: 'Interview prep' })).toBeVisible()
  await expect(page.getByText('No questions yet')).toBeVisible()

  await page.getByRole('button', { name: 'Generate questions' }).click()
  await expect(page.getByText(/Added \d+ new questions?\./)).toBeVisible()
  const questions = page.getByRole('list', { name: 'Interview questions' }).getByRole('listitem')
  await expect(questions).toHaveCount(GENERATED)
  await expect(page.getByText('Describe a production problem you solved with Kubernetes')).toBeVisible()

  // Regenerating never duplicates.
  await page.getByRole('button', { name: 'Generate questions' }).click()
  await expect(page.getByText(/No new questions/)).toBeVisible()
  await expect(questions).toHaveCount(GENERATED)

  // Filter by category (server side, reflected in the URL).
  await page.getByLabel('Category').selectOption('BEHAVIORAL')
  await expect(page).toHaveURL(/category=BEHAVIORAL/)
  await expect(questions).toHaveCount(3)
  await page.getByLabel('Category').selectOption('')
  // Wait for the full list to come back before choosing a card, then pin it by its text, not its position.
  await expect(questions).toHaveCount(GENERATED)

  // Notes and progress persist across a reload.
  const first = questions.filter({ hasText: 'first 90 days' })
  await expect(first).toHaveCount(1)
  await first.getByLabel('Your notes').fill('Lead with the incident timeline and the trade-off.')
  await first.getByLabel('Preparation').selectOption('PREPARED')
  await first.getByRole('button', { name: /^Save notes for/ }).click()
  await expect(first.getByText('Saved')).toBeVisible()

  await page.reload()
  await page.getByLabel('Progress').selectOption('PREPARED')
  await expect(questions).toHaveCount(1)
  await expect(questions.first().getByLabel('Your notes')).toHaveValue('Lead with the incident timeline and the trade-off.')
  await page.getByLabel('Progress').selectOption('')

  // Custom question, then delete it.
  await page.getByLabel('Question', { exact: true }).fill('Why do you want to join Globex?')
  await page.getByRole('button', { name: 'Add question' }).click()
  await expect(page.getByText('Why do you want to join Globex?')).toBeVisible()
  await expect(questions).toHaveCount(GENERATED + 1)

  page.once('dialog', (dialog) => void dialog.accept())
  await page.getByRole('button', { name: /Delete question: Why do you want to join Globex\?/ }).click()
  await expect(page.getByText('Why do you want to join Globex?')).toHaveCount(0)
  await expect(questions).toHaveCount(GENERATED)
})

test('interview prep asks for a job when none exist', async ({ page }) => {
  const email = `e2e-interview-empty-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await goTo(page, 'Interview prep')
  await expect(page.getByText('Add a job first')).toBeVisible()
})

test("one user cannot read another user's interview questions", async ({ page, browser, request }) => {
  await registerWithJob(page, 'interview-owner')
  await page.getByRole('link', { name: 'Prepare for the interview' }).click()
  await page.getByRole('button', { name: 'Generate questions' }).click()
  await expect(page.getByRole('list', { name: 'Interview questions' }).getByRole('listitem')).toHaveCount(GENERATED)
  const jobId = new URL(page.url()).searchParams.get('jobId')

  const other = await browser.newPage()
  await other.goto('/register')
  await other.getByLabel('Email').fill(`e2e-interview-other-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`)
  await other.getByLabel('Password').fill('correct-horse-battery')
  await other.getByRole('button', { name: 'Create account' }).click()
  await expect(other.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
  const token = await other.evaluate(() => JSON.parse(localStorage.getItem('joblens.session') ?? '{}').token as string)

  const list = await request.get(`http://localhost:8081/api/interviews/questions?jobId=${jobId}`, { headers: { Authorization: `Bearer ${token}` } })
  expect((await list.json()).totalElements).toBe(0)
  const generate = await request.post(`http://localhost:8081/api/jobs/${jobId}/interview-questions/generate`, { headers: { Authorization: `Bearer ${token}` } })
  expect(generate.status()).toBe(404)
  await other.close()
})
