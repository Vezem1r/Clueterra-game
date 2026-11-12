import { expect, type Page } from '@playwright/test'
import { test } from './support/auditTest'

interface ModalFrameSample {
  backdropOpacity: number | null
  frameIntervalMs: number
  mainOpacity: number
  panelOpacity: number | null
  panelTransform: string | null
  rootOpacity: number
  timeMs: number
}

interface ModalCaptureWindow extends Window {
  __modalFrameCapture?: {
    active: boolean
    lastFrameAt: number | null
    samples: ModalFrameSample[]
    startedAt: number
  }
}

const now = '2026-09-28T10:00:00Z'

const run = {
  id: 'run-1',
  runType: 'REVIEW',
  triggerType: 'MANUAL',
  status: 'COMPLETED',
  provider: 'google',
  model: 'gemini-2.5-flash',
  promptVersion: 'v7',
  requestedBy: 'admin@clueterra.test',
  totalStages: 18,
  preparedItems: 720,
  processedItems: 720,
  changedItems: 148,
  generatedItems: 0,
  estimatedInputTokens: 360_000,
  estimatedOutputTokens: 92_000,
  actualInputTokens: 355_000,
  actualOutputTokens: 88_000,
  toolCallCount: 42,
  configuration: { batchSize: 40, concurrency: 3 },
  summary: {
    reviewedItems: 720,
    acceptedItems: 148,
    unchangedItems: 572,
    averageConfidence: 0.91,
    elapsedSeconds: 74,
    countriesCovered: 196,
  },
  errorMessage: null,
  createdAt: now,
  startedAt: now,
  completedAt: '2026-09-28T10:01:14Z',
}

const pageResponse = <T,>(content: T[]) => ({
  content,
  number: 0,
  size: Math.max(content.length, 1),
  totalElements: content.length,
  totalPages: content.length > 0 ? 1 : 0,
})

function statistic(index: number) {
  return {
    id: `stat-${index}`,
    countryId: `country-${index}`,
    countryCode: 'US',
    countryName: `Test Country ${index + 1}`,
    source: 'World Bank',
    indicatorCode: `TEST_${index}`,
    indicatorName: `Infrastructure statistic ${index + 1}`,
    category: 'INFRASTRUCTURE',
    value: String(1000 + index),
    valueText: `${1000 + index} units`,
    unit: 'units',
    year: 2025,
    globalRank: index + 1,
    regionalRank: index + 1,
    percentile: 95 - index,
    deviationScore: 1.2,
    isCurrent: true,
    manualOverride: false,
    confidence: 0.96,
    weight: 0.8,
    fetchedAt: now,
    imageUrl: index === 0 ? '/vite.svg' : null,
    originalImageUrl: index === 0 ? '/vite.svg' : null,
    imageSourcePageUrl: 'https://example.com/source',
    imageAuthorName: 'Test Author',
    imageAttributionText: 'Synthetic motion-test fixture',
    imageLicenseLabel: 'CC BY 4.0',
    imageLicenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
  }
}

async function installAdminApiMocks(page: Page) {
  await page.addInitScript(() => localStorage.setItem('clu_auth_session', '1'))
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname

    if (!path.startsWith('/api/')) {
      await route.continue()
      return
    }

    if (path === '/api/auth/refresh') {
      await route.fulfill({ json: {
        email: 'admin@clueterra.test',
        username: 'motion-admin',
        role: 'ADMIN',
        expiresIn: 3600,
        totpRequired: false,
        legalAcceptanceRequired: false,
        userId: 'admin-motion-test',
      } })
      return
    }

    if (path === '/api/profile/badges/unacknowledged') {
      await route.fulfill({ json: [] })
      return
    }

    if (path === '/api/admin/ai/overview') {
      const quota = {
        requestsPerMinuteUsed: 2,
        requestsPerMinuteLimit: 60,
        requestsPerDayUsed: 20,
        requestsPerDayLimit: 1000,
        groundedPromptsPerDayUsed: 3,
        groundedPromptsPerDayLimit: 100,
        inputTokensToday: 355_000,
        outputTokensToday: 88_000,
        failedToday: 0,
        nextResetAt: now,
      }
      await route.fulfill({ json: {
        promptVersion: 'v7',
        review: { model: run.model, schedulerEnabled: true, maxRequestsPerRun: 50, quota },
        generation: { model: run.model, schedulerEnabled: true, maxRequestsPerRun: 50, quota },
        embedding: {
          enabled: true,
          available: true,
          model: 'BAAI/bge-small-en-v1.5@5c38ec7',
          dimensions: 384,
          activeHints: 109_943,
          embeddedHints: 37_480,
          missingHints: 72_463,
          coveragePercent: 34.1,
          duplicateThreshold: 0.88,
          relatedThreshold: 0.82,
        },
      } })
      return
    }

    if (path === '/api/admin/ai/runs') {
      await route.fulfill({ json: pageResponse(Array.from({ length: 20 }, (_, index) => ({
        ...run,
        id: `run-${index + 1}`,
        createdAt: new Date(Date.parse(now) - index * 60_000).toISOString(),
      }))) })
      return
    }

    if (path === '/api/admin/ai/change-sets') {
      await route.fulfill({ json: pageResponse([]) })
      return
    }

    if (path === '/api/admin/ai/runs/run-1') {
      await route.fulfill({ json: {
        run,
        stages: Array.from({ length: 18 }, (_, index) => ({
          id: `stage-${index + 1}`,
          runId: run.id,
          stageIndex: index + 1,
          stageType: `REVIEW_BATCH_${index + 1}`,
          status: 'COMPLETED',
          itemCount: 40,
          estimatedInputTokens: 20_000,
          estimatedOutputTokens: 5_000,
          actualInputTokens: 19_500,
          actualOutputTokens: 4_800,
          imageCount: 8,
          payload: { countries: 12, hints: 40, confidence: 0.92, duplicates: 3 },
          resultSummary: { reviewed: 40, changed: 8, unchanged: 32, durationSeconds: 4 },
          errorMessage: null,
          providerRequestId: `request-${index + 1}`,
          createdAt: now,
          startedAt: now,
          completedAt: now,
        })),
        changeSet: null,
        changeItems: [],
      } })
      return
    }

    if (path === '/api/admin/statistics/categories') {
      await route.fulfill({ json: ['INFRASTRUCTURE', 'ECONOMY'] })
      return
    }
    if (path === '/api/admin/statistics/sources') {
      await route.fulfill({ json: ['World Bank'] })
      return
    }
    if (path === '/api/admin/statistics/years') {
      await route.fulfill({ json: [2025, 2024] })
      return
    }
    if (path === '/api/admin/statistics/countries') {
      await route.fulfill({ json: [{ id: 'country-0', isoAlpha2: 'US', nameEn: 'Test Country 1' }] })
      return
    }
    if (path === '/api/admin/statistics') {
      await route.fulfill({ json: pageResponse(Array.from({ length: 30 }, (_, index) => statistic(index))) })
      return
    }
    if (path.startsWith('/api/assets/flags/')) {
      await route.fulfill({
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="12"><rect width="20" height="12" fill="#345"/></svg>',
        contentType: 'image/svg+xml',
      })
      return
    }

    await route.fulfill({ json: {} })
  })
}

async function beginModalFrameCapture(page: Page) {
  await page.evaluate(() => {
    const captureWindow = window as ModalCaptureWindow
    const capture: NonNullable<ModalCaptureWindow['__modalFrameCapture']> = {
      active: true,
      lastFrameAt: null,
      samples: [] as ModalFrameSample[],
      startedAt: performance.now(),
    }
    captureWindow.__modalFrameCapture = capture

    const effectiveOpacity = (element: Element | null) => {
      if (!element) return null
      let opacity = 1
      let current: Element | null = element
      while (current) {
        opacity *= Number.parseFloat(getComputedStyle(current).opacity || '1')
        current = current.parentElement
      }
      return opacity
    }

    const sample = (time: number) => {
      if (!capture.active) return
      const panel = document.querySelector('[data-modal-panel]')
      const backdrop = document.querySelector('[data-modal-backdrop]')
      capture.samples.push({
        backdropOpacity: effectiveOpacity(backdrop),
        frameIntervalMs: capture.lastFrameAt === null ? 0 : time - capture.lastFrameAt,
        mainOpacity: effectiveOpacity(document.querySelector('main')) ?? 0,
        panelOpacity: effectiveOpacity(panel),
        panelTransform: panel ? getComputedStyle(panel).transform : null,
        rootOpacity: effectiveOpacity(document.querySelector('#root')) ?? 0,
        timeMs: time - capture.startedAt,
      })
      capture.lastFrameAt = time
      requestAnimationFrame(sample)
    }
    requestAnimationFrame(sample)
  })
}

function expectNoVisualBlink(samples: ModalFrameSample[]) {
  const panelSamples = samples.filter((sample) => sample.panelOpacity !== null)
  const firstVisible = panelSamples.findIndex((sample) => (sample.panelOpacity ?? 0) >= 0.05)
  const visibleSamples = firstVisible < 0 ? [] : panelSamples.slice(firstVisible)
  const opacityDrops = visibleSamples.slice(1).filter((sample, index) => (
    (sample.panelOpacity ?? 0) + 0.02 < (visibleSamples[index].panelOpacity ?? 0)
  ))

  expect(panelSamples.length, 'The modal was never sampled').toBeGreaterThan(3)
  expect(opacityDrops, 'The modal opacity dropped again after it became visible').toEqual([])
  expect(Math.min(...samples.map((sample) => sample.rootOpacity)), 'The app root blinked while opening the modal').toBeGreaterThan(0.98)
  expect(Math.min(...samples.map((sample) => sample.mainOpacity)), 'The admin content blinked while opening the modal').toBeGreaterThan(0.98)
}

test.describe('mocked administrator modal frame diagnostics', () => {
  test.beforeEach(async ({ page }) => installAdminApiMocks(page))

  test('@motion mocked AI run modal remains visible on every opening frame', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium-motion', 'Frame diagnostics use the visible motion browser profile.')

    await page.goto('/admin/ai', { waitUntil: 'domcontentloaded' })
    const openRun = page.getByRole('button', { name: 'Open review run details' }).first()
    await expect(openRun).toBeVisible()
    await runtimeAudit.settle(500)
    const baseline = await runtimeAudit.measureMotionBaseline('mocked-admin-ai-idle')

    await beginModalFrameCapture(page)
    await runtimeAudit.beginMotionSample()
    await openRun.click()
    await expect(page.getByRole('dialog')).toBeVisible()
    const metrics = await runtimeAudit.endMotionSample(1_000, 'mocked-admin-ai-open')
    const frames = await page.evaluate(() => {
      const capture = (window as ModalCaptureWindow).__modalFrameCapture
      if (!capture) return []
      capture.active = false
      return capture.samples
    })
    await testInfo.attach('mocked-admin-ai-modal-frames.json', {
      body: Buffer.from(JSON.stringify(frames, null, 2)),
      contentType: 'application/json',
    })

    expectNoVisualBlink(frames)
    runtimeAudit.expectMotionWithinBudget(metrics, { maxLongestFrameMs: 100, maxLongestTaskMs: 100, maxP95FrameMs: 34 }, baseline)
    await runtimeAudit.expectHealthy()
  })

  test('@motion mocked statistic modal remains visible on every opening frame', async ({ page, runtimeAudit }, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium-motion', 'Frame diagnostics use the visible motion browser profile.')

    await page.goto('/admin/statistics', { waitUntil: 'domcontentloaded' })
    const edit = page.locator('button[title="Edit"]').first()
    await expect(edit).toBeVisible()
    await runtimeAudit.settle(500)
    const baseline = await runtimeAudit.measureMotionBaseline('mocked-admin-statistic-idle')

    await beginModalFrameCapture(page)
    await runtimeAudit.beginMotionSample()
    await edit.click()
    await expect(page.getByRole('dialog', { name: 'Edit Statistic' })).toBeVisible()
    const metrics = await runtimeAudit.endMotionSample(1_000, 'mocked-admin-statistic-open')
    const frames = await page.evaluate(() => {
      const capture = (window as ModalCaptureWindow).__modalFrameCapture
      if (!capture) return []
      capture.active = false
      return capture.samples
    })
    await testInfo.attach('mocked-admin-statistic-modal-frames.json', {
      body: Buffer.from(JSON.stringify(frames, null, 2)),
      contentType: 'application/json',
    })

    expectNoVisualBlink(frames)
    runtimeAudit.expectMotionWithinBudget(metrics, { maxLongestFrameMs: 100, maxLongestTaskMs: 100, maxP95FrameMs: 34 }, baseline)
    await runtimeAudit.expectHealthy()
  })
})
