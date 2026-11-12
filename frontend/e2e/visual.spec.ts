import { test, expect } from './support/auditTest'
import { exerciseDocumentScroll } from './support/pageActions'

const VISUAL_ROUTES = [
  ['about', '/about'],
  ['how-to', '/how-to'],
  ['login', '/login'],
  ['privacy', '/privacy'],
] as const
const TODAY_CHALLENGES = [
  { id: '1', date: '2026-01-01', level: 'EASY', difficulty: 'MEDIUM', totalCountries: 3, totalHints: 18, available: true, continentCount: 2, dataSourceCount: 10, categoryCount: 8, avgXp: 447 },
  { id: '2', date: '2026-01-01', level: 'MEDIUM', difficulty: 'HARD', totalCountries: 3, totalHints: 18, available: true, continentCount: 3, dataSourceCount: 8, categoryCount: 11, avgXp: 894 },
  { id: '3', date: '2026-01-01', level: 'HARD', difficulty: 'HARD', totalCountries: 3, totalHints: 18, available: true, continentCount: 2, dataSourceCount: 7, categoryCount: 8, avgXp: 1788 },
]
const BADGE_SAMPLES = [
  { id: '11', slug: 'visual-first', imageUrl: null, accentColorKey: 'emerald-core', effectColorKey: 'emerald-glow', name: 'First Steps', description: 'Finished a first run.', unlocked: false, grantedAt: null, progress: null },
  { id: '12', slug: 'visual-second', imageUrl: null, accentColorKey: 'sky-core', effectColorKey: 'sky-glow', name: 'Explorer', description: 'Explored ten countries.', unlocked: false, grantedAt: null, progress: null },
  { id: '13', slug: 'visual-third', imageUrl: null, accentColorKey: 'violet-core', effectColorKey: 'violet-glow', name: 'Hidden', description: 'A secret badge.', unlocked: false, grantedAt: null, progress: null },
]
const json = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })

test.describe('stable public visual baselines', () => {
  for (const [name, path] of VISUAL_ROUTES) {
    test(`@visual ${name}`, async ({ page, runtimeAudit }) => {
      await page.route('**/api/game/today', route => route.fulfill(json(TODAY_CHALLENGES)))
      await page.route('**/api/badges/samples', route => route.fulfill(json(BADGE_SAMPLES)))
      await page.goto(path, { waitUntil: 'domcontentloaded' })
      await runtimeAudit.settle()
      await exerciseDocumentScroll(page)
      await expect(page).toHaveScreenshot(`${name}.png`, {
        animations: 'disabled',
        caret: 'hide',
        fullPage: true,
        maxDiffPixelRatio: 0.005,
      })
      await runtimeAudit.expectHealthy()
    })
  }
})
