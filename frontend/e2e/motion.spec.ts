import { test, expect } from './support/auditTest'

test.describe('animation performance diagnostics', () => {
  for (const [name, path] of [
    ['Clueterra guide', '/?guide=1'],
    ['BudgetGuessr guide', '/budget?guide=budget'],
  ] as const) {
    test(`@motion ${name} page transitions remain responsive`, async ({ page, runtimeAudit }, testInfo) => {
      test.skip(!['chromium-desktop', 'chromium-motion'].includes(testInfo.project.name), 'Motion budgets use one fixed desktop browser profile.')

      await page.goto(path, { waitUntil: 'domcontentloaded' })
      const dialog = page.getByRole('dialog')
      await expect(dialog).toBeVisible()
      await runtimeAudit.settle(400)
      const baseline = await runtimeAudit.measureMotionBaseline(`${name}-idle`)

      for (let index = 0; index < 3; index += 1) {
        const next = dialog.getByRole('button', { name: 'Next', exact: true })
        if (!await next.count()) break
        await runtimeAudit.beginMotionSample()
        await next.click()
        const metrics = await runtimeAudit.endMotionSample(800, `${name}-step-${index + 1}`)
        runtimeAudit.expectMotionWithinBudget(metrics, {
          maxLayoutShift: 0.03,
          maxLongestFrameMs: 140,
          maxLongestTaskMs: 140,
          maxP95FrameMs: 40,
        }, baseline)
        await runtimeAudit.expectHealthy()
      }

      const closeBaseline = await runtimeAudit.measureMotionBaseline(`${name}-close-idle`)
      await runtimeAudit.beginMotionSample()
      const closeStartedAt = performance.now()
      await page.keyboard.press('Escape')
      await expect(dialog).toBeHidden()
      const closeDurationMs = performance.now() - closeStartedAt
      testInfo.annotations.push({
        type: 'motion',
        description: `${name} close settled in ${closeDurationMs.toFixed(0)} ms`,
      })
      const closeMetrics = await runtimeAudit.endMotionSample(800, `${name}-close`)
      runtimeAudit.expectMotionWithinBudget(closeMetrics, {
        maxLayoutShift: 0.03,
        maxLongestFrameMs: 140,
        maxLongestTaskMs: 140,
        maxP95FrameMs: 40,
      }, closeBaseline)
      expect(closeDurationMs, 'Modal close animation took too long to leave the screen').toBeLessThanOrEqual(350)
      await runtimeAudit.expectHealthy()
    })
  }

  test('@motion project filter expansion does not stall or shift the page', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(!['chromium-desktop', 'chromium-motion'].includes(testInfo.project.name), 'Motion budgets use one fixed desktop browser profile.')

    await page.goto('/projects', { waitUntil: 'domcontentloaded' })
    await runtimeAudit.settle()
    const baseline = await runtimeAudit.measureMotionBaseline('project-filters-idle')
    await runtimeAudit.beginMotionSample()
    await page.getByRole('button', { name: /^Filters/ }).click()
    const metrics = await runtimeAudit.endMotionSample(900, 'project-filters-open')
    runtimeAudit.expectMotionWithinBudget(metrics, {
      maxLayoutShift: 0.05,
      maxLongestFrameMs: 140,
      maxLongestTaskMs: 140,
      maxP95FrameMs: 40,
    }, baseline)
    await runtimeAudit.expectHealthy()
  })

  test('@motion country answer reveal remains responsive', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(!['chromium-desktop', 'chromium-motion'].includes(testInfo.project.name), 'Motion budgets use one fixed desktop browser profile.')

    await page.goto('/', { waitUntil: 'domcontentloaded' })
    const playButton = page.getByRole('button', { name: /Play Today's Challenge|Continue Game/i }).first()
    await expect(playButton).toBeEnabled({ timeout: 20_000 })
    await playButton.click()
    await expect(page).toHaveURL(/\/play(?:\?|$)/, { timeout: 20_000 })

    const search = page.getByPlaceholder('Search for a country...').filter({ visible: true }).first()
    await search.fill('France')
    const france = page.getByRole('button', { name: /France/ }).filter({ visible: true }).first()
    await expect(france).toBeVisible()

    const baseline = await runtimeAudit.measureMotionBaseline('country-answer-idle')
    await runtimeAudit.beginMotionSample()
    await france.click()
    const metrics = await runtimeAudit.endMotionSample(1_200, 'country-answer-reveal')
    runtimeAudit.expectMotionWithinBudget(metrics, {}, baseline)
    await runtimeAudit.expectHealthy()
  })

  test('@motion BudgetGuessr image and answer reveals remain responsive', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(!['chromium-desktop', 'chromium-motion'].includes(testInfo.project.name), 'Motion budgets use one fixed desktop browser profile.')

    await page.goto('/budget', { waitUntil: 'domcontentloaded' })
    const playButton = page.getByRole('button', { name: /Play Today's Challenge|Continue Game/i }).first()
    const unavailable = page.getByText(/Today's challenge (?:isn't ready yet|has not been published yet)/i)
    await expect(playButton.or(unavailable).first()).toBeVisible({ timeout: 20_000 })
    if (await unavailable.isVisible()) {
      test.skip(true, 'Budget motion requires a generated daily challenge.')
      return
    }
    await expect(playButton).toBeEnabled({ timeout: 20_000 })
    await playButton.click()

    const imageButton = page.getByRole('button', { name: 'Open and zoom image' }).first()
    if (await imageButton.count()) {
      const imageBaseline = await runtimeAudit.measureMotionBaseline('budget-image-idle')
      await runtimeAudit.beginMotionSample()
      await imageButton.click()
      await expect(page.getByRole('dialog', { name: 'Image preview' })).toBeVisible()
      const openMetrics = await runtimeAudit.endMotionSample(1_000, 'budget-image-open')
      runtimeAudit.expectMotionWithinBudget(openMetrics, {}, imageBaseline)
      const closeBaseline = await runtimeAudit.measureMotionBaseline('budget-image-close-idle')
      await runtimeAudit.beginMotionSample()
      const closeStartedAt = performance.now()
      await page.getByRole('button', { name: 'Close image' }).click()
      await expect(page.getByRole('dialog', { name: 'Image preview' })).toBeHidden()
      const closeDurationMs = performance.now() - closeStartedAt
      testInfo.annotations.push({
        type: 'motion',
        description: `Budget image close settled in ${closeDurationMs.toFixed(0)} ms`,
      })
      const closeMetrics = await runtimeAudit.endMotionSample(800, 'budget-image-close')
      runtimeAudit.expectMotionWithinBudget(closeMetrics, {}, closeBaseline)
      expect(closeDurationMs, 'Image modal close animation took too long to leave the screen').toBeLessThanOrEqual(350)
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
    const revealBaseline = await runtimeAudit.measureMotionBaseline('budget-answer-idle')
    await runtimeAudit.beginMotionSample()
    await submit.click()
    const revealMetrics = await runtimeAudit.endMotionSample(1_200, 'budget-answer-reveal')
    runtimeAudit.expectMotionWithinBudget(revealMetrics, {}, revealBaseline)
    await runtimeAudit.expectHealthy()
  })
})
