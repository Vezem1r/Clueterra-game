import { expect, test } from '@playwright/test'

const TOTAL = 197
const FLAG_URL = '/api/flag-game/rounds/1/flag.svg'
const FLAG_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 480"><path fill="#000" d="M0 0h640v160H0z"/>'
  + '<path fill="#d00" d="M0 160h640v160H0z"/><path fill="#ffce00" d="M0 320h640v160H0z"/></svg>'
const DESKTOP_MIN_WIDTH = 1024

test('flag guide explains where the capital clue comes from', async ({ page }) => {
  await page.goto('/games/country-flags/how-to')
  await expect(page.getByRole('heading', { level: 1, name: 'How to play Guess the flag' })).toBeVisible()
  await expect(page.getByText('A capital is only offered as a clue when our statistics and Wikidata agree', { exact: false })).toBeVisible()
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', 'https://clueterra.com/games/country-flags/how-to')
})

test('guest flag game opens clues, crosses out a wrong country and raises the right flag', async ({ page }) => {
  let hintsUsed = 0
  let attempts = 0
  let solved: string[] = []
  let current = false
  let misses: string[] = []

  const hints = () => ({
    region: hintsUsed >= 1 ? { region: 'Europe', subregion: 'Western Europe' } : null,
    initials: hintsUsed >= 2 ? { en: 'G', ru: 'Г', cs: 'N' } : null,
    capitals: hintsUsed >= 3 ? [{ en: 'Berlin', ru: 'Берлин', cs: 'Berlín' }] : null,
  })
  const clues = [{ kind: 'REGION', costXp: 12 }, { kind: 'INITIAL', costXp: 12 }, { kind: 'CAPITAL', costXp: 24 }]
  const question = () => ({
    round: 1, tier: 1, flagUrl: FLAG_URL, hints: hints(), clues, hintsUsed, attempts,
    maximumXp: 150 - clues.slice(0, hintsUsed).reduce((sum, clue) => sum + clue.costXp, 0), baseXp: 150, misses,
  })
  const stats = () => ({
    attemptedFlags: attempts > 0 ? 1 : 0, solvedFlags: solved.length, totalFlags: TOTAL,
    answerAttempts: attempts, skips: 0, xpAwarded: solved.length * 102,
  })
  const session = () => ({ stats: stats(), current: current ? question() : null, solved })
  const reveal = {
    countryCode: 'DE', region: 'Europe', subregion: 'Western Europe',
    names: { en: 'Germany', ru: 'Германия', cs: 'Německo' },
    capitals: [{ en: 'Berlin', ru: 'Берлин', cs: 'Berlín' }],
  }
  const json = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })

  await page.route('**/api/legal/current', route => route.fulfill(json({ termsVersion: 'test-terms', privacyVersion: 'test-privacy' })))
  await page.route('**/api/game/guest/progress', route => route.fulfill(json({ hasGuest: false })))
  await page.route('**/api/flag-game/**', route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    if (path === FLAG_URL) return route.fulfill({ status: 200, contentType: 'image/svg+xml', body: FLAG_SVG })
    if (path === '/api/flag-game/session' && method === 'GET') return route.fulfill(json(session()))
    if (path === '/api/flag-game/session/guest' && method === 'POST') {
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
      } else {
        misses = ['BE']
      }
      return route.fulfill(json({ correct, attempts, xpAwarded: correct ? 102 : 0, reveal: correct ? reveal : null, misses, stats: stats() }))
    }
    return route.abort()
  })

  await page.goto('/games/country-flags')
  await expect(page.getByRole('heading', { level: 1, name: 'Guess the flag' })).toBeVisible()
  await page.getByRole('button', { name: 'Start playing' }).click()

  const panel = page.getByTestId('flag-answer-panel')
  await expect(panel.getByRole('img', { name: 'The flag of the mystery country' })).toBeVisible()
  await panel.getByRole('button', { name: /Show the region/ }).click()
  await expect(panel.getByText('Western Europe')).toBeVisible()
  await panel.getByRole('button', { name: /Show the first letter/ }).click()
  await expect(panel.getByText('Its name starts with')).toBeVisible()
  await panel.getByRole('button', { name: /Show the capital/ }).click()
  await expect(panel.getByText('Berlin')).toBeVisible()

  const input = panel.getByRole('combobox', { name: 'Country name' })
  await input.fill('Belg')
  await panel.getByRole('option', { name: 'Belgium' }).click()
  await expect(panel.getByRole('status')).toHaveText('Belgium is not this flag.')
  await expect(panel.getByRole('listitem').filter({ hasText: 'Belgium' })).toBeVisible()

  await input.fill('Germ')
  await expect(panel.getByRole('option', { name: 'Germany' })).toBeVisible()
  await input.press('Enter')

  const result = page.getByTestId('flag-reveal')
  await expect(result.getByRole('heading', { name: 'Germany' })).toBeVisible()
  await expect(result.getByText('Raised on your plaza')).toBeVisible()
  await expect(result.getByText('Berlin')).toBeVisible()
  await expect(result.getByRole('region', { name: 'Your guesses' })).toContainText('Belgium')
  if ((page.viewportSize()?.width ?? 0) >= DESKTOP_MIN_WIDTH) await expect(page.getByText(`1/${TOTAL}`).first()).toBeVisible()
})

test('a round finished in another tab moves the game on instead of failing', async ({ page }) => {
  const stats = { attemptedFlags: 1, solvedFlags: 1, totalFlags: TOTAL, answerAttempts: 1, skips: 0, xpAwarded: 150 }
  const clues = [{ kind: 'REGION', costXp: 12 }, { kind: 'INITIAL', costXp: 12 }, { kind: 'CAPITAL', costXp: 24 }]
  const round = (number: number) => ({
    round: number, tier: 1, flagUrl: FLAG_URL, hints: { region: null, initials: null, capitals: null }, clues,
    hintsUsed: 0, attempts: 0, maximumXp: 150, baseXp: 150, misses: [],
  })
  let serverRound = 1
  const json = (status: number, body: unknown) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })

  await page.route('**/api/legal/current', route => route.fulfill(json(200, { termsVersion: 'test-terms', privacyVersion: 'test-privacy' })))
  await page.route('**/api/game/guest/progress', route => route.fulfill(json(200, { hasGuest: false })))
  await page.route('**/api/flag-game/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/flag.svg')) return route.fulfill({ status: 200, contentType: 'image/svg+xml', body: FLAG_SVG })
    if (path === '/api/flag-game/session') return route.fulfill(json(200, { stats, current: round(serverRound), solved: ['DE'] }))
    if (path === '/api/flag-game/session/guest') return route.fulfill(json(200, { stats, current: round(serverRound), solved: ['DE'] }))
    if (path.endsWith('/answer')) {
      serverRound = 2
      return route.fulfill(json(409, { status: 409, error: 'Conflict', message: 'This round has already moved on', code: 'ROUND_NOT_CURRENT' }))
    }
    return route.abort()
  })

  await page.goto('/games/country-flags')
  await page.getByRole('button', { name: /Continue round 1|Start playing/ }).click()
  const panel = page.getByTestId('flag-answer-panel')
  await expect(panel.getByText('Round 1')).toBeVisible()
  await panel.getByRole('button', { name: /Show the answer/ }).click()

  await expect(page.getByRole('alert')).toHaveText('This round already ended in another tab, so here is where your game is now')
  await expect(page.getByRole('alert')).not.toContainText('Internal server error')
  await expect(panel.getByText('Round 2')).toBeVisible()
})

test('flag guide opens as a modal from the game screen', async ({ page }) => {
  await page.route('**/api/flag-game/session', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ stats: { attemptedFlags: 0, solvedFlags: 0, totalFlags: TOTAL, answerAttempts: 0, skips: 0, xpAwarded: 0 }, current: null, solved: [] }),
  }))
  await page.goto('/games/country-flags')
  await page.locator('main').getByRole('link', { name: 'How to play' }).click()
  const dialog = page.getByRole('dialog', { name: 'How to play Guess the flag' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'Next' }).click()
  await dialog.getByRole('button', { name: 'Next' }).click()
  await dialog.getByRole('button', { name: /Show the first letter/ }).click()
  await expect(dialog.getByText('Its name starts with')).toBeVisible()
  await dialog.getByRole('button', { name: 'Next' }).click()
  await expect(dialog.getByRole('img', { name: /plaza of nations at night/ })).toBeVisible()
  await dialog.getByRole('button', { name: 'Got it' }).click()
  await expect(dialog).toBeHidden()
})
