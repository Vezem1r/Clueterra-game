import { expect, test } from '@playwright/test'

const SHAPE = 'M300 200 700 200 700 800 300 800Z'
const GERMAN_FLAG = [{ color: '#000000', share: 0.333 }, { color: '#dd0000', share: 0.333 }, { color: '#ffce00', share: 0.334 }]
const TOTAL = 197
const DESKTOP_MIN_WIDTH = 1024

test('country silhouette guide explains the EU border policy', async ({ page }) => {
  await page.goto('/games/country-silhouettes/how-to')
  await expect(page.getByRole('heading', { level: 1, name: 'How to play Country silhouettes' })).toBeVisible()
  await expect(page.getByText('Borders follow the position of the European Union', { exact: false })).toBeVisible()
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', 'https://clueterra.com/games/country-silhouettes/how-to')
})

test('guest silhouette game opens clues, rejects a wrong country and paints the right one', async ({ page }) => {
  let hintsUsed = 0
  let attempts = 0
  let solved: string[] = []
  let current = false
  let misses: { countryCode: string, distanceKm: number, bearingDeg: number }[] = []

  const hints = () => ({
    region: hintsUsed >= 1 ? { region: 'Europe', subregion: 'Western Europe', center: [8, 49] } : null,
    location: hintsUsed >= 2 ? { center: [10.4, 51.1], bounds: [5.9, 47.3, 15.0, 55.1] } : null,
    capitals: hintsUsed >= 3 ? [{ en: 'Berlin', ru: 'Берлин', cs: 'Berlín' }] : null,
    flagColors: null,
    landmark: null,
  })
  const clues = [
    { kind: 'REGION', costXp: 12 }, { kind: 'LOCATION', costXp: 12 }, { kind: 'CAPITAL', costXp: 24 },
  ]
  const question = () => ({
    round: 1, tier: 1, shapePath: SHAPE, hints: hints(), clues, hintsUsed, attempts,
    maximumXp: 150 - clues.slice(0, hintsUsed).reduce((sum, clue) => sum + clue.costXp, 0), baseXp: 150, misses,
  })
  const stats = () => ({
    attemptedCountries: attempts > 0 ? 1 : 0, solvedCountries: solved.length, totalCountries: TOTAL,
    answerAttempts: attempts, skips: 0, xpAwarded: solved.length * 106,
  })
  const session = () => ({ stats: stats(), current: current ? question() : null, solved })
  const reveal = {
    countryCode: 'DE',
    subregion: 'Western Europe',
    names: { en: 'Germany', ru: 'Германия', cs: 'Německo' },
    facts: { capital: { en: 'Berlin', ru: 'Берлин', cs: 'Berlín' }, population: { value: 83000000, year: 2025, source: 'WORLD_BANK' },
      areaKm2: { value: 357022 }, highestPoint: null, landlocked: false, drivesLeft: false },
    flagColors: GERMAN_FLAG,
    center: [10.4, 51.1],
    bounds: [5.9, 47.3, 15.0, 55.1],
    shapePath: SHAPE,
    photo: null,
  }
  const json = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })

  await page.route('**/api/legal/current', route => route.fulfill(json({ termsVersion: 'test-terms', privacyVersion: 'test-privacy' })))
  await page.route('**/api/game/guest/progress', route => route.fulfill(json({ hasGuest: false })))
  await page.route('**/api/silhouettes/**', route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    if (path === '/api/silhouettes/session' && method === 'GET') return route.fulfill(json(session()))
    if (path === '/api/silhouettes/session/guest' && method === 'POST') {
      current = true
      return route.fulfill(json(session()))
    }
    if (path.endsWith('/hint')) {
      hintsUsed++
      return route.fulfill(json(question()))
    }
    if (path.endsWith('/guess')) {
      const correct = route.request().postDataJSON().countryCode === 'DE'
      attempts++
      if (correct) {
        current = false
        solved = ['DE']
      }
      const miss = correct ? null : { countryCode: 'FR', distanceKm: 878, bearingDeg: 67 }
      if (miss) misses = [miss]
      return route.fulfill(json({ correct, attempts, xpAwarded: correct ? 106 : 0, reveal: correct ? reveal : null, miss, misses, stats: stats() }))
    }
    if (path === '/api/silhouettes/countries/DE') return route.fulfill(json(reveal))
    return route.abort()
  })

  await page.goto('/games/country-silhouettes')
  await expect(page.getByRole('heading', { level: 1, name: 'Country silhouettes' })).toBeVisible()
  await page.getByRole('button', { name: 'Start playing' }).click()

  const panel = page.getByTestId('silhouette-answer-panel')
  await expect(panel.getByRole('img', { name: 'Silhouette of the mystery country' })).toBeVisible()
  await panel.getByRole('button', { name: /Show the region/ }).click()
  await expect(panel.getByText('Western Europe')).toBeVisible()
  await panel.getByRole('button', { name: /Show the country/ }).click()
  await expect(panel.getByText('It is outlined on the globe now.')).toBeVisible()
  await panel.getByRole('button', { name: /Show the capital/ }).click()
  await expect(panel.getByText('Berlin')).toBeVisible()

  const input = panel.getByRole('combobox', { name: 'Country name' })
  await input.fill('Fran')
  await panel.getByRole('option', { name: 'France' }).click()
  await expect(panel.getByRole('status')).toHaveText('France is not this country. The answer lies 878 km to the north-east.')
  await expect(panel.getByRole('listitem').filter({ hasText: 'France' })).not.toContainText('878 km')
  await expect(page.getByTestId('silhouette-miss-marks')).toContainText('878 km')

  await input.fill('Germ')
  await expect(panel.getByRole('option', { name: 'Germany' })).toBeVisible()
  await input.press('Enter')

  const result = page.getByTestId('silhouette-reveal')
  await expect(result.getByRole('heading', { name: 'Germany' })).toBeVisible()
  await expect(result.getByText('Painted on your globe')).toBeVisible()
  await expect(result.getByText('Berlin')).toBeVisible()
  await expect(result.getByRole('region', { name: 'Your guesses' })).toContainText('France')
  await expect(result.getByRole('region', { name: 'Your guesses' })).toContainText('878 km')
  if ((page.viewportSize()?.width ?? 0) >= DESKTOP_MIN_WIDTH) await expect(page.getByText(`1/${TOTAL}`).first()).toBeVisible()
})

test('country silhouette guide opens as a modal from the game screen', async ({ page }) => {
  await page.route('**/api/silhouettes/session', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ stats: { attemptedCountries: 0, solvedCountries: 0, totalCountries: TOTAL, answerAttempts: 0, skips: 0, xpAwarded: 0 }, current: null, solved: [] }),
  }))
  await page.goto('/games/country-silhouettes')
  await page.locator('main').getByRole('link', { name: 'How to play' }).click()
  const dialog = page.getByRole('dialog', { name: 'How to play Country silhouettes' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'Next' }).click()
  await expect(dialog.getByRole('img', { name: /two wrong guesses/ })).toBeVisible()
  await dialog.getByRole('button', { name: 'Next' }).click()
  await dialog.getByRole('button', { name: /Show the country/ }).click()
  await expect(dialog.getByText('It is outlined on the globe now.')).toBeVisible()
  await dialog.getByRole('button', { name: 'Next' }).click()
  await expect(dialog.getByRole('img', { name: /card of Kazakhstan/ })).toBeVisible()
  await dialog.getByRole('button', { name: 'Got it' }).click()
  await expect(dialog).toBeHidden()
})
