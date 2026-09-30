import path from 'node:path'
import { expect, test } from '@playwright/test'

const fixture = path.join(__dirname, '..', 'fixtures', 'resume.pdf')

test('upload a PDF resume, review parsed data, edit it and delete the version', async ({ page }) => {
  const email = `e2e-resume-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Resume' }).click()
  await expect(page.getByText('No resume yet')).toBeVisible()

  await page.getByLabel(/upload a resume/i).setInputFiles(fixture)

  // Deterministic parsing extracted the structured fields.
  await expect(page.getByLabel('Name')).toHaveValue('Jane Developer')
  await expect(page.getByLabel('Email')).toHaveValue('jane.dev@example.com')
  await expect(page.getByLabel('Skills')).toHaveValue(/Java/)
  await expect(page.getByRole('button', { name: /v1 · resume\.pdf/ })).toBeVisible()

  // Corrections persist across a reload.
  await page.getByLabel('Name').fill('Jane Q. Developer')
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page.getByText('Changes saved.')).toBeVisible()
  await page.reload()
  await expect(page.getByLabel('Name')).toHaveValue('Jane Q. Developer')

  // A second upload creates version 2.
  await page.getByLabel(/upload a resume/i).setInputFiles(fixture)
  await expect(page.getByRole('button', { name: /v2 · resume\.pdf/ })).toBeVisible()

  page.once('dialog', (dialog) => void dialog.accept())
  await page.getByRole('button', { name: 'Delete version 2' }).click()
  await expect(page.getByRole('button', { name: /v2 · resume\.pdf/ })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /v1 · resume\.pdf/ })).toBeVisible()
})

test('rejects a non-PDF file with a clear message', async ({ page }) => {
  const email = `e2e-resume-bad-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await page.getByRole('link', { name: 'Resume' }).click()

  await page.getByLabel(/upload a resume/i).setInputFiles({
    name: 'notes.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('this is not really a pdf'),
  })
  await expect(page.getByRole('alert')).toHaveText('The file does not look like a valid PDF')
})
