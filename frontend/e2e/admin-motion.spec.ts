import { test, expect } from './support/auditTest'

const adminStorageState = process.env.PLAYWRIGHT_ADMIN_STORAGE_STATE

test.describe('administrator modal motion diagnostics', () => {
  test.use({ storageState: adminStorageState || { cookies: [], origins: [] } })
  test.skip(!adminStorageState, 'Set PLAYWRIGHT_ADMIN_STORAGE_STATE to measure administrator modal motion.')

  test('@motion @authenticated user editor closes promptly', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium-motion', 'Administrator motion uses the visible motion browser profile.')

    await page.goto('/admin/users', { waitUntil: 'domcontentloaded' })
    await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
    await runtimeAudit.settle(1_000)

    const edit = page.locator('button[title="Edit"]').first()
    if (!await edit.count()) {
      testInfo.annotations.push({
        type: 'precondition',
        description: 'No editable user row was available for the administrator modal measurement.',
      })
      await runtimeAudit.expectHealthy()
      return
    }

    await edit.click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    const baseline = await runtimeAudit.measureMotionBaseline('admin-user-modal-idle')

    await runtimeAudit.beginMotionSample()
    const closeStartedAt = performance.now()
    await dialog.getByRole('button', { name: 'Close' }).click()
    await expect(dialog).toBeHidden()
    const closeDurationMs = performance.now() - closeStartedAt
    testInfo.annotations.push({
      type: 'motion',
      description: `Administrator user modal close settled in ${closeDurationMs.toFixed(0)} ms`,
    })
    const metrics = await runtimeAudit.endMotionSample(800, 'admin-user-modal-close')
    runtimeAudit.expectMotionWithinBudget(metrics, {}, baseline)
    expect(closeDurationMs, 'Administrator modal close animation took too long to leave the screen').toBeLessThanOrEqual(350)
    await runtimeAudit.expectHealthy()
  })

  test('@motion @authenticated AI run detail opens without a frame stall', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium-motion', 'Administrator motion uses the visible motion browser profile.')

    await page.goto('/admin/ai', { waitUntil: 'domcontentloaded' })
    await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
    await runtimeAudit.settle(1_000)

    const openRun = page.getByRole('button', { name: /Open .* run details/i }).first()
    if (!await openRun.count()) {
      test.skip(true, 'No AI run was available for the modal opening measurement.')
      return
    }

    const baseline = await runtimeAudit.measureMotionBaseline('admin-ai-run-idle')
    await runtimeAudit.beginMotionSample()
    await openRun.click()
    await expect(page.getByRole('dialog')).toBeVisible()
    const metrics = await runtimeAudit.endMotionSample(1_000, 'admin-ai-run-open')
    runtimeAudit.expectMotionWithinBudget(metrics, {
      maxLongestFrameMs: 100,
      maxLongestTaskMs: 100,
      maxP95FrameMs: 34,
    }, baseline)
    await runtimeAudit.expectHealthy()
  })

  test('@motion @authenticated statistic editor opens without a frame stall', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium-motion', 'Administrator motion uses the visible motion browser profile.')

    await page.goto('/admin/statistics', { waitUntil: 'domcontentloaded' })
    await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
    await runtimeAudit.settle(1_000)

    const edit = page.locator('button[title="Edit"]').first()
    if (!await edit.count()) {
      test.skip(true, 'No editable statistic was available for the modal opening measurement.')
      return
    }

    const baseline = await runtimeAudit.measureMotionBaseline('admin-statistic-idle')
    await runtimeAudit.beginMotionSample()
    await edit.click()
    await expect(page.getByRole('dialog', { name: 'Edit Statistic' })).toBeVisible()
    const metrics = await runtimeAudit.endMotionSample(1_000, 'admin-statistic-open')
    runtimeAudit.expectMotionWithinBudget(metrics, {
      maxLongestFrameMs: 100,
      maxLongestTaskMs: 100,
      maxP95FrameMs: 34,
    }, baseline)
    await runtimeAudit.expectHealthy()
  })
})
