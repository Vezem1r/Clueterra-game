import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3001'
const usesExternalServer = Boolean(process.env.PLAYWRIGHT_BASE_URL)
const INTERNAL_AGENT_MARKER = 'ClueterraDev/1.0 (playwright)'

function deviceUse(name: keyof typeof devices) {
  const device = devices[name]
  return usesExternalServer ? { ...device, userAgent: `${device.userAgent} ${INTERNAL_AGENT_MARKER}` } : device
}

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results/artifacts',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 2,
  timeout: 45_000,
  expect: {
    timeout: 10_000,
  },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL,
    actionTimeout: 10_000,
    navigationTimeout: 25_000,
    colorScheme: 'dark',
    locale: 'en-US',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  webServer: usesExternalServer
    ? undefined
    : {
        command: 'npm run dev -- --host 127.0.0.1',
        url: baseURL,
        reuseExistingServer: true,
        timeout: 120_000,
      },
  projects: [
    {
      name: 'chromium-desktop',
      use: {
        ...deviceUse('Desktop Chrome'),
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      name: 'chromium-narrow',
      use: {
        ...deviceUse('Desktop Chrome'),
        viewport: { width: 920, height: 900 },
      },
    },
    {
      name: 'chromium-mobile',
      use: {
        ...deviceUse('Pixel 7'),
      },
    },
    {
      name: 'chromium-motion',
      testMatch: /motion\.spec\.ts/,
      use: {
        ...deviceUse('Desktop Chrome'),
        headless: false,
        trace: 'on',
        video: 'on',
        viewport: { width: 1440, height: 900 },
      },
    },
  ],
})
