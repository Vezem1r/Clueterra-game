import { test, expect } from './support/auditTest'
import { exerciseDocumentScroll } from './support/pageActions'

const PUBLIC_ROUTES = [
  ['home', '/'],
  ['guide-redirect', '/guide'],
  ['about', '/about'],
  ['how-to', '/how-to'],
  ['play-entry', '/play'],
  ['budget', '/budget'],
  ['budget-how-to', '/budget/how-to'],
  ['results', '/results'],
  ['login', '/login'],
  ['register', '/register'],
  ['oauth-callback-without-provider-response', '/oauth/callback'],
  ['leaderboard', '/leaderboard'],
  ['friends', '/friends'],
  ['privacy', '/privacy'],
  ['terms', '/terms'],
  ['data-sources', '/data-sources'],
  ['projects', '/projects'],
  ['project-page-two', '/projects/page/2'],
  ['project-ranking', '/projects/most-expensive'],
  ['project-category-ranking', '/projects/most-expensive/stadiums'],
  ['project-country-ranking', '/projects/countries/united-kingdom'],
  ['not-found', '/e2e-not-found'],
] as const

test.describe('public route audit', () => {
  for (const [name, path] of PUBLIC_ROUTES) {
    test(`@audit ${name} renders without runtime or visual-health failures`, async ({ page, runtimeAudit }) => {
      const response = await page.goto(path, { waitUntil: 'domcontentloaded' })

      expect(response, `No document response received for ${path}`).not.toBeNull()
      expect(response?.status() || 0, `Document request failed for ${path}`).toBeLessThan(500)
      await runtimeAudit.settle()
      await exerciseDocumentScroll(page)
      await page.waitForTimeout(250)
      await runtimeAudit.expectHealthy()
    })
  }
})
