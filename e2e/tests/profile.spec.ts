import { expect, test } from '@playwright/test'

test('profile and skills persist across reloads', async ({ page }) => {
  const email = `e2e-profile-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`

  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Profile' }).click()
  await expect(page.getByText('No skills yet')).toBeVisible()

  await page.getByLabel('Name').fill('Playwright Tester')
  await page.getByLabel('Headline').fill('QA engineer')
  await page.getByLabel('Years of experience').fill('4')
  await page.getByRole('button', { name: 'Save profile' }).click()
  await expect(page.getByText('Profile saved.')).toBeVisible()

  await page.getByRole('combobox', { name: 'Skill', exact: true }).fill('Playwright')
  await page.getByLabel('Category').selectOption('TESTING')
  await page.getByLabel('Proficiency').selectOption('ADVANCED')
  await page.getByRole('button', { name: 'Add skill' }).click()
  await expect(page.getByRole('list').getByText('Playwright', { exact: true })).toBeVisible()

  await page.reload()
  await expect(page.getByLabel('Name')).toHaveValue('Playwright Tester')
  await expect(page.getByLabel('Years of experience')).toHaveValue('4')
  await expect(page.getByRole('list').getByText('Playwright', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Remove Playwright' }).click()
  await expect(page.getByText('No skills yet')).toBeVisible()
})
