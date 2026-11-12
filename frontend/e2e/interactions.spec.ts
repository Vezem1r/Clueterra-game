import { test, expect } from './support/auditTest'

test.describe('interactive UI regression audit', () => {
  test('@audit project filters combine and survive navigation', async ({ page, runtimeAudit }) => {
    await page.goto('/projects', { waitUntil: 'domcontentloaded' })
    await runtimeAudit.settle()

    await page.getByLabel('Search projects by name or category').fill('bridge')
    await page.getByRole('button', { name: /^Filters/ }).click()
    await page.getByRole('button', { name: 'Bridges', exact: true }).click()
    await page.getByLabel('Minimum cost').fill('500M')
    await page.getByLabel('From year').fill('2000')

    await expect(page).toHaveURL(/q=bridge/)
    await expect(page).toHaveURL(/category=BRIDGE/)
    await expect(page).toHaveURL(/minCost=500000000/)
    await expect(page).toHaveURL(/yearFrom=2000/)
    await runtimeAudit.settle(500)
    await runtimeAudit.expectHealthy()

    const firstProject = page.locator('main article a[href^="/projects/"]').first()
    if (await firstProject.count()) {
      await firstProject.click()
      await expect(page).toHaveURL(/\/projects\/[^/?]+/)
      await runtimeAudit.settle()
      await runtimeAudit.expectHealthy()
    }
  })

  for (const [name, path] of [
    ['Clueterra guide', '/?guide=1'],
    ['BudgetGuessr guide', '/budget?guide=budget'],
  ] as const) {
    test(`@audit ${name} opens, changes pages and closes cleanly`, async ({ page, runtimeAudit }) => {
      await page.goto(path, { waitUntil: 'domcontentloaded' })
      const dialog = page.getByRole('dialog')
      await expect(dialog).toBeVisible()
      await runtimeAudit.settle(400)

      const next = dialog.getByRole('button', { name: 'Next', exact: true })
      for (let index = 0; index < 2; index += 1) {
        await next.click()
        await page.waitForTimeout(300)
        await expect(dialog).toBeVisible()
      }
      await runtimeAudit.expectHealthy()

      await page.keyboard.press('Escape')
      await expect(dialog).toBeHidden()
      await runtimeAudit.settle(350)
      await runtimeAudit.expectHealthy()
    })
  }

  test('@audit guest country gameplay reaches a reveal state', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium-desktop', 'Gameplay writes one guest attempt and runs once per audit.')

    await page.goto('/', { waitUntil: 'domcontentloaded' })
    const playButton = page.getByRole('button', { name: /Play Today's Challenge|Continue Game/i }).first()
    await expect(playButton).toBeVisible()
    await expect(playButton).toBeEnabled({ timeout: 20_000 })
    await playButton.click()
    await expect(page).toHaveURL(/\/play(?:\?|$)/, { timeout: 20_000 })

    const search = page.getByPlaceholder('Search for a country...').filter({ visible: true }).first()
    await expect(search).toBeVisible({ timeout: 20_000 })
    await search.fill('France')
    const france = page.getByRole('button', { name: /France/ }).filter({ visible: true }).first()
    await expect(france).toBeVisible()

    await france.click()
    await runtimeAudit.settle(1_000)
    await runtimeAudit.expectHealthy()
  })

  test('@audit guest BudgetGuessr opens an image and submits one answer', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium-desktop', 'Gameplay writes one guest answer and runs once per audit.')

    await page.goto('/budget', { waitUntil: 'domcontentloaded' })
    const playButton = page.getByRole('button', { name: /Play Today's Challenge|Continue Game/i }).first()
    const unavailable = page.getByText(/Today's challenge (?:isn't ready yet|has not been published yet)/i)
    await expect(playButton.or(unavailable).first()).toBeVisible({ timeout: 20_000 })
    if (await unavailable.isVisible()) {
      test.skip(true, 'Budget gameplay requires a generated daily challenge.')
      return
    }
    await expect(playButton).toBeEnabled({ timeout: 20_000 })
    await playButton.click()

    const imageButton = page.getByRole('button', { name: 'Open and zoom image' }).first()
    if (await imageButton.count()) {
      await imageButton.click()
      await expect(page.getByRole('dialog', { name: 'Image preview' })).toBeVisible()
      await runtimeAudit.settle(700)
      await runtimeAudit.expectHealthy()
      await page.getByRole('button', { name: 'Close image' }).click()
    }

    const choice = page.locator('article[role="button"]').first()
    if (await choice.count()) {
      await choice.click()
    } else {
      const amount = page.locator('form input[inputmode="decimal"]:visible').first()
      await expect(amount).toBeVisible()
      await amount.fill('100000000')
    }

    const submit = page.getByRole('button', { name: 'Lock in guess' })
    await expect(submit).toBeEnabled()
    await submit.click()
    await runtimeAudit.settle(1_000)
    await runtimeAudit.expectHealthy()
  })
})
