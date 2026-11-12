import { expect, type Page, type TestInfo } from '@playwright/test'

interface BrowserPerformanceState {
  active: boolean
  frameIntervals: number[]
  lastFrameAt: number | null
  layoutShifts: number[]
  longTasks: number[]
  missingFramesBySelector: Record<string, number>
  opacitySamplesBySelector: Record<string, number[]>
  sampleStartedAt: number | null
  watchedSelectors: string[]
}

interface AuditedWindow extends Window {
  __clueterraE2ePerformance?: BrowserPerformanceState
}

export interface DomHealth {
  bodyTextLength: number
  brokenImages: string[]
  horizontalOverflow: number
  overflowElements: string[]
  suspiciousOpacity: string[]
}

export interface MotionMetrics {
  documentHidden: boolean
  expectedFrameCount: number
  frameCount: number
  frameCoverage: number
  framesOver32Ms: number
  framesOver50Ms: number
  layoutShift: number
  longTaskCount: number
  longestFrameMs: number
  longestTaskMs: number
  minimumOpacityBySelector: Record<string, number>
  missingFramesBySelector: Record<string, number>
  p95FrameMs: number
  sampleDurationMs: number
  transparentFramesBySelector: Record<string, number>
}

export interface MotionBudget {
  maxLayoutShift?: number
  maxLongestFrameMs?: number
  maxLongestTaskMs?: number
  maxP95FrameMs?: number
}

export interface MotionBaseline {
  metrics: MotionMetrics
  valid: boolean
}

const DEFAULT_MOTION_BUDGET: Required<MotionBudget> = {
  maxLayoutShift: 0.05,
  maxLongestFrameMs: 180,
  maxLongestTaskMs: 180,
  maxP95FrameMs: 50,
}

export class RuntimeAudit {
  private readonly consoleErrors: string[] = []
  private readonly pageErrors: string[] = []
  private readonly requestFailures: string[] = []
  private readonly resourceErrors: string[] = []
  private readonly serverErrors: string[] = []

  constructor(
    private readonly page: Page,
    private readonly testInfo: TestInfo,
  ) {}

  async install() {
    this.page.on('console', (message) => {
      if (message.type() !== 'error') return
      const text = message.text()
      if (text.startsWith('Failed to load resource: the server responded with a status of 4')) return
      this.consoleErrors.push(text)
    })
    this.page.on('pageerror', (error) => this.pageErrors.push(error.stack || error.message))
    this.page.on('requestfailed', (request) => {
      const reason = request.failure()?.errorText || 'unknown error'
      if (!reason.includes('ERR_ABORTED')) {
        this.requestFailures.push(`${request.method()} ${request.url()} - ${reason}`)
      }
    })
    this.page.on('response', (response) => {
      if (response.status() >= 500) {
        this.serverErrors.push(`${response.status()} ${response.request().method()} ${response.url()}`)
      } else if (
        response.status() >= 400
        && ['document', 'font', 'image', 'script', 'stylesheet'].includes(response.request().resourceType())
      ) {
        this.resourceErrors.push(`${response.status()} ${response.request().resourceType()} ${response.url()}`)
      }
    })

    await this.page.addInitScript(() => {
      const auditedWindow = window as AuditedWindow
      const state: BrowserPerformanceState = {
        active: false,
        frameIntervals: [],
        lastFrameAt: null,
        layoutShifts: [],
        longTasks: [],
        missingFramesBySelector: {},
        opacitySamplesBySelector: {},
        sampleStartedAt: null,
        watchedSelectors: [],
      }
      auditedWindow.__clueterraE2ePerformance = state

      const onFrame = (timestamp: number) => {
        if (state.active) {
          if (state.lastFrameAt !== null) state.frameIntervals.push(timestamp - state.lastFrameAt)
          state.lastFrameAt = timestamp
          state.watchedSelectors.forEach((selector) => {
            const element = document.querySelector(selector)
            if (!element) {
              state.missingFramesBySelector[selector] = (state.missingFramesBySelector[selector] || 0) + 1
              return
            }
            let opacity = 1
            let current: Element | null = element
            while (current) {
              opacity *= Number.parseFloat(getComputedStyle(current).opacity || '1')
              current = current.parentElement
            }
            state.opacitySamplesBySelector[selector]?.push(opacity)
          })
        } else {
          state.lastFrameAt = null
        }
        window.requestAnimationFrame(onFrame)
      }
      window.requestAnimationFrame(onFrame)

      try {
        new PerformanceObserver((list) => {
          if (!state.active) return
          list.getEntries().forEach((entry) => state.longTasks.push(entry.duration))
        }).observe({ entryTypes: ['longtask'] })
      } catch {
        // Long Task API is Chromium-specific and optional in other engines.
      }

      try {
        new PerformanceObserver((list) => {
          if (!state.active) return
          list.getEntries().forEach((entry) => {
            const layoutShift = entry as PerformanceEntry & { hadRecentInput?: boolean; value?: number }
            if (!layoutShift.hadRecentInput && typeof layoutShift.value === 'number') {
              state.layoutShifts.push(layoutShift.value)
            }
          })
        }).observe({ type: 'layout-shift', buffered: true })
      } catch {
        // Layout Instability API is optional in non-Chromium environments.
      }
    })
  }

  async settle(milliseconds = 800) {
    await this.page.locator('body').waitFor({ state: 'visible' })
    await this.page.evaluate(async () => {
      if ('fonts' in document) await document.fonts.ready
    })
    await this.page.waitForTimeout(milliseconds)
  }

  async inspectDom(): Promise<DomHealth> {
    return this.page.evaluate(() => {
      const viewportWidth = document.documentElement.clientWidth
      const viewportHeight = document.documentElement.clientHeight
      const describe = (element: Element) => {
        const htmlElement = element as HTMLElement
        const id = htmlElement.id ? `#${htmlElement.id}` : ''
        const className = typeof htmlElement.className === 'string'
          ? htmlElement.className.split(/\s+/).filter(Boolean).slice(0, 3).map((name) => `.${name}`).join('')
          : ''
        const text = (htmlElement.innerText || htmlElement.getAttribute('aria-label') || '')
          .replace(/\s+/g, ' ')
          .trim()
          .slice(0, 80)
        return `${element.tagName.toLowerCase()}${id}${className}${text ? ` "${text}"` : ''}`
      }
      const isInViewport = (rect: DOMRect) => (
        rect.bottom > 0
        && rect.right > 0
        && rect.top < viewportHeight
        && rect.left < viewportWidth
      )
      const isAuditable = (element: Element, rect: DOMRect) => {
        const htmlElement = element as HTMLElement
        if (rect.width < 3 || rect.height < 3 || !isInViewport(rect)) return false
        if (htmlElement.hidden || htmlElement.closest('[hidden],[inert],[aria-hidden="true"],[data-audit-allow-transparent]')) return false
        const style = getComputedStyle(htmlElement)
        return style.display !== 'none' && style.visibility !== 'hidden'
      }
      const effectiveOpacity = (element: Element) => {
        let opacity = 1
        let current: Element | null = element
        while (current) {
          opacity *= Number.parseFloat(getComputedStyle(current).opacity || '1')
          current = current.parentElement
        }
        return opacity
      }

      const semanticSelector = [
        'button:not([disabled])',
        'a[href]',
        'input:not([disabled])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[role="button"]:not([aria-disabled="true"])',
        '[role="dialog"]',
        '[role="alert"]',
        'main h1',
        'main h2',
        'main h3',
      ].join(',')

      const suspiciousOpacity = Array.from(document.querySelectorAll(semanticSelector))
        .filter((element) => {
          const rect = element.getBoundingClientRect()
          if (!isAuditable(element, rect)) return false
          const style = getComputedStyle(element)
          const interactive = element.matches('button,a,input,select,textarea,[role="button"]')
          if (interactive && style.pointerEvents === 'none') return false
          const className = (element as HTMLElement).className
          if (typeof className === 'string' && /(?:group-hover|hover|focus-visible):opacity-/.test(className)) return false
          return effectiveOpacity(element) <= 0.02
        })
        .slice(0, 20)
        .map(describe)

      const brokenImages = Array.from(document.images)
        .filter((image) => {
          const sourceAssigned = Boolean(image.currentSrc || image.getAttribute('src'))
          return sourceAssigned && image.complete && image.naturalWidth === 0
        })
        .slice(0, 20)
        .map((image) => image.currentSrc || image.src || describe(image))

      const overflowElements = Array.from(document.body.querySelectorAll('*'))
        .filter((element) => {
          const rect = element.getBoundingClientRect()
          if (rect.width <= 1 || rect.height <= 1) return false
          const style = getComputedStyle(element)
          if (style.position === 'fixed' || style.position === 'absolute') return false
          return rect.left < -2 || rect.right > viewportWidth + 2
        })
        .slice(0, 12)
        .map(describe)

      return {
        bodyTextLength: document.body.innerText.trim().length,
        brokenImages,
        horizontalOverflow: Math.max(0, document.documentElement.scrollWidth - viewportWidth),
        overflowElements,
        suspiciousOpacity,
      }
    })
  }

  async expectHealthy() {
    const dom = await this.inspectDom()
    await this.testInfo.attach('dom-health.json', {
      body: Buffer.from(JSON.stringify(dom, null, 2)),
      contentType: 'application/json',
    })

    expect(dom.bodyTextLength, 'The page rendered no readable content').toBeGreaterThan(2)
    expect(dom.horizontalOverflow, `Horizontal overflow caused by: ${dom.overflowElements.join('; ')}`).toBeLessThanOrEqual(2)
    expect(dom.brokenImages, 'Visible images failed to decode').toEqual([])
    expect(dom.suspiciousOpacity, 'Visible semantic elements are stuck transparent').toEqual([])
    expect(this.serverErrors, 'Backend responses with status 5xx').toEqual([])
    expect(this.consoleErrors, 'Browser console errors').toEqual([])
    expect(this.pageErrors, 'Unhandled browser exceptions').toEqual([])
    expect(this.requestFailures, 'Failed browser requests').toEqual([])
    expect(this.resourceErrors, 'Documents or static resources returned a 4xx response').toEqual([])
  }

  async beginMotionSample(watchedSelectors = ['body', '#root', 'main']) {
    await this.page.evaluate((selectors) => {
      const state = (window as AuditedWindow).__clueterraE2ePerformance
      if (!state) throw new Error('Performance instrumentation was not installed')
      state.active = true
      state.frameIntervals = []
      state.lastFrameAt = null
      state.layoutShifts = []
      state.longTasks = []
      state.missingFramesBySelector = Object.fromEntries(selectors.map((selector) => [selector, 0]))
      state.opacitySamplesBySelector = Object.fromEntries(selectors.map((selector) => [selector, []]))
      state.sampleStartedAt = performance.now()
      state.watchedSelectors = selectors
    }, watchedSelectors)
    await this.page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => resolve())))
  }

  async endMotionSample(settleMilliseconds = 700, label = 'motion'): Promise<MotionMetrics> {
    await this.page.waitForTimeout(settleMilliseconds)
    const metrics = await this.page.evaluate(() => {
      const state = (window as AuditedWindow).__clueterraE2ePerformance
      if (!state) throw new Error('Performance instrumentation was not installed')
      state.active = false
      const sampleDurationMs = state.sampleStartedAt === null ? 0 : performance.now() - state.sampleStartedAt
      const frames = [...state.frameIntervals].sort((left, right) => left - right)
      const percentile = (values: number[], ratio: number) => {
        if (values.length === 0) return 0
        return values[Math.min(values.length - 1, Math.ceil(values.length * ratio) - 1)]
      }
      const minimumOpacityBySelector = Object.fromEntries(
        Object.entries(state.opacitySamplesBySelector)
          .map(([selector, samples]) => [selector, samples.length === 0 ? 0 : Math.min(...samples)]),
      )
      const transparentFramesBySelector = Object.fromEntries(
        Object.entries(state.opacitySamplesBySelector)
          .map(([selector, samples]) => [selector, samples.filter((opacity) => opacity <= 0.02).length]),
      )
      return {
        documentHidden: document.hidden,
        expectedFrameCount: sampleDurationMs / (1000 / 60),
        frameCount: frames.length,
        frameCoverage: sampleDurationMs === 0 ? 0 : frames.length / (sampleDurationMs / (1000 / 60)),
        framesOver32Ms: frames.filter((duration) => duration > 32).length,
        framesOver50Ms: frames.filter((duration) => duration > 50).length,
        layoutShift: state.layoutShifts.reduce((sum, value) => sum + value, 0),
        longTaskCount: state.longTasks.length,
        longestFrameMs: frames.at(-1) || 0,
        longestTaskMs: Math.max(0, ...state.longTasks),
        minimumOpacityBySelector,
        missingFramesBySelector: { ...state.missingFramesBySelector },
        p95FrameMs: percentile(frames, 0.95),
        sampleDurationMs,
        transparentFramesBySelector,
      }
    })
    this.testInfo.annotations.push({
      type: 'motion',
      description: `${label}: ${metrics.frameCount}/${metrics.expectedFrameCount.toFixed(0)} frames, p95 ${metrics.p95FrameMs.toFixed(1)} ms, max ${metrics.longestFrameMs.toFixed(1)} ms, longest task ${metrics.longestTaskMs.toFixed(1)} ms, CLS ${metrics.layoutShift.toFixed(4)}`,
    })
    await this.testInfo.attach(`${label.replace(/[^a-z0-9-]+/gi, '-')}-motion-metrics.json`, {
      body: Buffer.from(JSON.stringify(metrics, null, 2)),
      contentType: 'application/json',
    })
    return metrics
  }

  async measureMotionBaseline(label = 'idle-baseline', milliseconds = 800): Promise<MotionBaseline> {
    await this.beginMotionSample()
    const metrics = await this.endMotionSample(milliseconds, label)
    return {
      metrics,
      valid: !metrics.documentHidden
        && metrics.frameCount >= 8
        && metrics.p95FrameMs <= 34,
    }
  }

  expectMotionWithinBudget(metrics: MotionMetrics, budget: MotionBudget = {}, baseline?: MotionBaseline) {
    const resolved = { ...DEFAULT_MOTION_BUDGET, ...budget }
    if (baseline) {
      expect(
        baseline.valid,
        `The idle browser baseline is too slow for a trustworthy motion verdict: p95=${baseline.metrics.p95FrameMs.toFixed(1)} ms, frames=${baseline.metrics.frameCount}/${baseline.metrics.expectedFrameCount.toFixed(0)}, hidden=${baseline.metrics.documentHidden}`,
      ).toBe(true)
    }
    expect(metrics.documentHidden, 'The page became hidden, so the frame sample is invalid').toBe(false)
    expect(metrics.frameCount, 'The browser did not produce enough frames for a valid sample').toBeGreaterThanOrEqual(8)
    expect(metrics.p95FrameMs, 'p95 frame time exceeded the motion budget').toBeLessThanOrEqual(resolved.maxP95FrameMs)
    expect(metrics.longestFrameMs, 'A visibly frozen frame exceeded the motion budget').toBeLessThanOrEqual(resolved.maxLongestFrameMs)
    expect(metrics.longestTaskMs, 'A main-thread long task exceeded the motion budget').toBeLessThanOrEqual(resolved.maxLongestTaskMs)
    expect(metrics.layoutShift, 'Unexpected layout shift during the interaction').toBeLessThanOrEqual(resolved.maxLayoutShift)
    expect(
      Object.entries(metrics.missingFramesBySelector).filter(([, frames]) => frames > 0),
      'A stable page container disappeared during the interaction',
    ).toEqual([])
    expect(
      Object.entries(metrics.transparentFramesBySelector).filter(([, frames]) => frames > 0),
      'A stable page container became fully transparent during the interaction',
    ).toEqual([])
  }

  async attachSummary() {
    await this.testInfo.attach('runtime-errors.json', {
      body: Buffer.from(JSON.stringify({
        consoleErrors: this.consoleErrors,
        pageErrors: this.pageErrors,
        requestFailures: this.requestFailures,
        resourceErrors: this.resourceErrors,
        serverErrors: this.serverErrors,
      }, null, 2)),
      contentType: 'application/json',
    })
  }
}
