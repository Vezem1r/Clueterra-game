import { test, expect } from './support/auditTest'

const smokeId = process.env.PLAYWRIGHT_SMOKE_ID

function productionPath(path: string) {
  if (!smokeId) {
    return path
  }
  const separator = path.includes('?') ? '&' : '?'
  return `${path}${separator}deploy-smoke=${encodeURIComponent(smokeId)}`
}

test.describe('production browser smoke', () => {
  for (const [name, path, heading] of [
    ['homepage', '/', /Guess the country from real-world clues/i],
    ['BudgetGuessr', '/budget', /What did this megaproject really cost/i],
    ['projects', '/projects', /biggest projects/i],
  ] as const) {
    test(`@smoke @production ${name} is usable through the public edge`, async ({ page, runtimeAudit }) => {
      const response = await page.goto(productionPath(path), { waitUntil: 'domcontentloaded' })
      expect(response?.status()).toBe(200)
      await expect(page.getByRole('heading', { name: heading }).first()).toBeAttached()
      await runtimeAudit.settle()
      await runtimeAudit.expectHealthy()
    })
  }

  test('@smoke @production project filters update results without a browser error', async ({ page, runtimeAudit }) => {
    await page.goto(productionPath('/projects'), { waitUntil: 'domcontentloaded' })
    await page.getByLabel('Search projects by name or category').fill('bridge')
    await page.getByRole('button', { name: /^Filters/ }).click()
    await page.getByRole('button', { name: 'Bridges', exact: true }).click()
    await expect(page).toHaveURL(/q=bridge/)
    await expect(page).toHaveURL(/category=BRIDGE/)
    await runtimeAudit.settle(500)
    await runtimeAudit.expectHealthy()
  })

  for (const [name, path] of [
    ['Clueterra guide', '/?guide=1'],
    ['BudgetGuessr guide', '/budget?guide=budget'],
  ] as const) {
    test(`@smoke @production ${name} opens, advances and closes`, async ({ page, runtimeAudit }) => {
      await page.goto(productionPath(path), { waitUntil: 'domcontentloaded' })
      const dialog = page.getByRole('dialog')
      await expect(dialog).toBeVisible()
      await dialog.getByRole('button', { name: 'Next', exact: true }).click()
      await page.waitForTimeout(300)
      await runtimeAudit.expectHealthy()
      await page.keyboard.press('Escape')
      await expect(dialog).toBeHidden()
    })
  }
})
