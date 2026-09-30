import { expect, test } from '@playwright/test'

const password = 'correct-horse-battery'

function uniqueEmail() {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
}

test('unauthenticated users are redirected to sign in', async ({ page }) => {
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
})

test('register, reach the dashboard, sign out and sign back in', async ({ page }) => {
  const email = uniqueEmail()

  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
  await expect(page.getByText(email)).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()

  // The protected page is no longer reachable after logout.
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()

  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
})

test('login with wrong password shows an error', async ({ page, request }) => {
  const email = uniqueEmail()
  const response = await request.post('http://localhost:8081/api/auth/register', {
    data: { email, password },
  })
  expect(response.status()).toBe(201)

  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('not-the-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toHaveText('Invalid email or password')
})

test('registering an existing email shows a conflict message', async ({ page, request }) => {
  const email = uniqueEmail()
  await request.post('http://localhost:8081/api/auth/register', { data: { email, password } })

  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('alert')).toHaveText('An account with this email already exists')
})

test.describe('session expiry', () => {
  async function plantSession(page: import('@playwright/test').Page, token: string, expiresAt: string) {
    await page.addInitScript(([t, e]) => {
      localStorage.setItem('joblens.session', JSON.stringify({
        token: t, expiresAt: e,
        user: { id: '1', email: 'stale@example.com', role: 'USER', createdAt: '2026-01-01T00:00:00Z' },
      }))
    }, [token, expiresAt])
  }

  test('a token the server rejects logs the user out and explains nothing sensitive', async ({ page }) => {
    // Looks valid client-side (expires in an hour) but the server will reject the signature.
    await plantSession(page, 'eyJhbGciOiJIUzI1NiJ9.forged.signature', new Date(Date.now() + 3600_000).toISOString())
    await page.goto('/profile')
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
    expect(await page.evaluate(() => localStorage.getItem('joblens.session'))).toBeNull()
  })

  test('a locally expired session never reaches the app', async ({ page }) => {
    await plantSession(page, 'anything', new Date(Date.now() - 1000).toISOString())
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  })

  test('after being logged out by an expired token, signing in again works', async ({ page, request }) => {
    const email = uniqueEmail()
    expect((await request.post('http://localhost:8081/api/auth/register', { data: { email, password } })).status()).toBe(201)
    await plantSession(page, 'eyJhbGciOiJIUzI1NiJ9.forged.signature', new Date(Date.now() + 3600_000).toISOString())
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()

    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible()
  })
})
