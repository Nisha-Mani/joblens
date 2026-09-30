import { defineConfig, devices } from '@playwright/test'

const backendPort = 8081
const frontendPort = 5174

// The E2E suite runs against the real backend and a dedicated database.
// AI calls are mocked via AI_PROVIDER=mock once the AI module exists.
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${frontendPort}`,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: './mvnw -q spring-boot:run',
      cwd: '../backend',
      url: `http://localhost:${backendPort}/actuator/health`,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
      env: {
        SERVER_PORT: String(backendPort),
        DB_URL: process.env.E2E_DB_URL ?? 'jdbc:postgresql://localhost:5432/joblens_e2e',
        CORS_ALLOWED_ORIGINS: `http://localhost:${frontendPort}`,
        AI_PROVIDER: 'mock', // never call a paid API from tests
        ANALYSIS_RATE_LIMIT_PER_HOUR: '1000',
        JWT_SECRET: process.env.JWT_SECRET ?? 'e2e-only-secret-key-0123456789-abcdefghij',
        POSTGRES_USER: process.env.POSTGRES_USER ?? 'joblens',
        POSTGRES_PASSWORD: process.env.POSTGRES_PASSWORD ?? '',
      },
    },
    {
      command: `npm run dev -- --port ${frontendPort} --strictPort`,
      cwd: '../frontend',
      url: `http://localhost:${frontendPort}`,
      reuseExistingServer: !process.env.CI,
      // Empty base URL so the browser calls the Vite proxy (same origin) rather than a fixed port.
      env: { VITE_PROXY_TARGET: `http://localhost:${backendPort}`, VITE_API_BASE_URL: '' },
    },
  ],
})
