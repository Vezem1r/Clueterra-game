import { test, expect } from './support/auditTest'

const emptyStorageState = { cookies: [], origins: [] }
const adminStorageState = process.env.PLAYWRIGHT_ADMIN_STORAGE_STATE
const userStorageState = process.env.PLAYWRIGHT_USER_STORAGE_STATE || adminStorageState

test.describe('authenticated account screens', () => {
  test.use({ storageState: userStorageState || emptyStorageState })
  test.skip(!userStorageState, 'Set PLAYWRIGHT_USER_STORAGE_STATE or PLAYWRIGHT_ADMIN_STORAGE_STATE to audit signed-in screens.')

  for (const [name, path] of [
    ['profile', '/profile'],
    ['legal update', '/legal/update'],
    ['results', '/results'],
    ['friends', '/friends'],
  ] as const) {
    test(`@authenticated ${name} remains healthy`, async ({ page, runtimeAudit }) => {
      const response = await page.goto(path, { waitUntil: 'domcontentloaded' })
      expect(response?.status() || 0).toBeLessThan(500)
      await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
      await runtimeAudit.settle(1_000)
      await runtimeAudit.expectHealthy()
    })
  }
})

test.describe('administrator screens', () => {
  test.use({ storageState: adminStorageState || emptyStorageState })
  test.skip(!adminStorageState, 'Set PLAYWRIGHT_ADMIN_STORAGE_STATE to audit administrator screens.')

  for (const [name, path] of [
    ['dashboard', '/admin'],
    ['users', '/admin/users'],
    ['analytics', '/admin/analytics'],
    ['operations', '/admin/operations'],
    ['AI', '/admin/ai'],
    ['logs', '/admin/logs'],
    ['hints', '/admin/hints'],
    ['countries', '/admin/countries'],
    ['challenges', '/admin/challenges'],
    ['reports', '/admin/reports'],
    ['statistics', '/admin/statistics'],
    ['badges', '/admin/badges'],
    ['indicators', '/admin/indicators'],
    ['settings', '/admin/settings'],
    ['Budget content', '/admin/content/budget'],
  ] as const) {
    test(`@authenticated admin ${name} remains healthy`, async ({ page, runtimeAudit }) => {
      const response = await page.goto(path, { waitUntil: 'domcontentloaded' })
      expect(response?.status() || 0).toBeLessThan(500)
      await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
      await expect(page).toHaveURL(new RegExp(`${path.replaceAll('/', '\\/')}(?:[/?]|$)`))
      await runtimeAudit.settle(1_000)
      await runtimeAudit.expectHealthy()
    })
  }
})
