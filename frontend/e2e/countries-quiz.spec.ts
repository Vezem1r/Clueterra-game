import { expect, test, type Route } from '@playwright/test'

const TOTAL = 197
const TOTAL_LEVELS = 591
const BASE_XP = 50
const FLAG_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 480"><path fill="#000" d="M0 0h640v160H0z"/>'
  + '<path fill="#d00" d="M0 160h640v160H0z"/><path fill="#ffce00" d="M0 320h640v160H0z"/></svg>'
const FRANCE = { countryCode: 'FR', names: { en: 'France', ru: 'Франция', cs: 'Francie' }, region: 'Europe', subregion: 'Western Europe', tier: 1 }
const GERMANY = { countryCode: 'DE', names: { en: 'Germany', ru: 'Германия', cs: 'Německo' }, region: 'Europe', subregion: 'Western Europe', tier: 1 }
const place = (en: string, ru: string, cs: string) => ({ type: 'PLACE', names: { en, ru, cs } })
const number = (value: number) => ({ type: 'NUMBER', value })
const country = (code: string, en: string, ru: string, cs: string) => ({ type: 'COUNTRY', countryCode: code, names: { en, ru, cs } })

interface Answer {
  selectedIndex: number
  answerIndex: number
  correct: boolean
  xpAwarded: number
  fact: Record<string, unknown>
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function stats(explored: number, mastered: number, levels: number, answered: number, correct: number, xp: number) {
  return {
    exploredCountries: explored, masteredCountries: mastered, totalCountries: TOTAL, completedLevels: levels,
    totalLevels: TOTAL_LEVELS, answeredQuestions: answered, correctAnswers: correct, xpAwarded: xp,
  }
}

test.beforeEach(async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.route('**/api/legal/current', route => json(route, { termsVersion: 'test-terms', privacyVersion: 'test-privacy' }))
  await page.route('**/api/game/guest/progress', route => json(route, { hasGuest: false }))
})

test('quiz guide explains the three levels and where the answers come from', async ({ page }) => {
  await page.goto('/games/countries-quiz/how-to')
  await expect(page.getByRole('heading', { level: 1, name: 'How to play Around the World Quiz' })).toBeVisible()
  await expect(page.getByText('Every country has three levels.', { exact: false })).toBeVisible()
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', 'https://clueterra.com/games/countries-quiz/how-to')
})

test('guest finishes a level, learns facts and returns to a country for level two', async ({ page }) => {
  let stopNumber = 0
  const answers: Record<number, Answer | null> = { 1: null, 2: null }
  let travelled = false

  const franceQuestions = () => [
    { position: 1, kind: 'CAPITAL', subject: null, answer: answers[1],
      options: [place('Lyon', 'Лион', 'Lyon'), place('Paris', 'Париж', 'Paříž'), place('Marseille', 'Марсель', 'Marseille'), place('Nice', 'Ницца', 'Nice')] },
    { position: 2, kind: 'NEIGHBOUR_COUNT', subject: null, answer: answers[2], options: [number(6), number(7), number(8), number(9)] },
  ]
  const germanyQuestions = [
    { position: 1, kind: 'FLAG', subject: null, answer: null, options: [0, 1, 2, 3].map(index => ({ type: 'FLAG', flagUrl: `/api/quiz-game/stops/2/questions/1/options/${index}/flag.svg` })) },
    { position: 2, kind: 'POPULATION', subject: null, answer: null,
      options: [country('DE', 'Germany', 'Германия', 'Německo'), country('PL', 'Poland', 'Польша', 'Polsko'), country('AT', 'Austria', 'Австрия', 'Rakousko'), country('CH', 'Switzerland', 'Швейцария', 'Švýcarsko')] },
  ]
  const finished = () => answers[1] != null && answers[2] != null
  const session = () => {
    const given = Object.values(answers).filter((answer): answer is Answer => answer != null)
    const current = travelled
      ? { stop: 2, country: GERMANY, level: 2, levels: 3, arrivalMode: 'DRIVE', fromCountryCode: 'FR', questions: germanyQuestions, next: null, finished: false, answerXp: 2 * BASE_XP }
      : { stop: 1, country: FRANCE, level: 1, levels: 3, arrivalMode: null, fromCountryCode: null, questions: franceQuestions(),
          next: finished() ? { country: GERMANY, mode: 'DRIVE', level: 2, route: [] } : null, finished: finished(), answerXp: BASE_XP }
    return {
      stats: stats(finished() ? 2 : 1, 0, finished() ? 3 : 2, given.length, given.filter(answer => answer.correct).length,
        given.reduce((sum, answer) => sum + answer.xpAwarded, 0)),
      current: stopNumber > 0 ? current : null,
      countries: [{ countryCode: 'DE', level: 1, levels: 3 }, ...(finished() ? [{ countryCode: 'FR', level: 1, levels: 3 }] : [])],
      trail: stopNumber > 0 ? [
        { stop: 1, countryCode: 'FR', fromCountryCode: null, mode: null, route: [], level: 1 },
        ...(travelled ? [{ stop: 2, countryCode: 'DE', fromCountryCode: 'FR', mode: 'DRIVE', route: [], level: 2 }] : []),
      ] : [],
      complete: false,
    }
  }

  await page.route('**/api/quiz-game/**', handled => {
    const path = new URL(handled.request().url()).pathname
    const method = handled.request().method()
    if (path.endsWith('/flag.svg')) return handled.fulfill({ status: 200, contentType: 'image/svg+xml', body: FLAG_SVG })
    if (path === '/api/quiz-game/session' && method === 'GET') return json(handled, session())
    if (path === '/api/quiz-game/session/guest' && method === 'POST') {
      stopNumber = 1
      return json(handled, session())
    }
    const answered = path.match(/^\/api\/quiz-game\/stops\/1\/questions\/(\d)\/answer$/)
    if (answered) {
      const position = Number(answered[1])
      const selected = handled.request().postDataJSON().option as number
      const answerIndex = position === 1 ? 1 : 2
      const fact = position === 1
        ? { type: 'POPULATION', value: 68_400_000, year: 2024 }
        : { type: 'NEIGHBOURS', countries: ['BE', 'DE', 'ES', 'IT', 'LU', 'CH', 'AD', 'MC'] }
      answers[position] = { selectedIndex: selected, answerIndex, correct: selected === answerIndex, xpAwarded: selected === answerIndex ? BASE_XP : 0, fact }
      const current = session()
      return json(handled, { answer: answers[position], stop: current.current, stats: current.stats, complete: false })
    }
    if (path === '/api/quiz-game/stops/1/travel' && method === 'POST') {
      travelled = true
      stopNumber = 2
      return json(handled, session())
    }
    return handled.abort()
  })

  await page.goto('/games/countries-quiz')
  await expect(page.getByRole('heading', { level: 1, name: 'Around the World Quiz' })).toBeVisible()
  await page.getByRole('button', { name: 'Start the trip' }).click()

  const panel = page.getByTestId('quiz-question-panel')
  await expect(panel.getByRole('heading', { name: 'France', exact: true })).toBeVisible()
  await expect(panel.getByText('Level 1 of 3')).toBeVisible()
  await panel.getByRole('button', { name: 'Paris' }).click()
  await expect(panel.getByRole('status')).toContainText(`+${BASE_XP} XP`)
  await expect(panel.getByRole('status')).toContainText('About 68.4M people lived in France in 2024.')

  await panel.getByRole('button', { name: 'Next question' }).click()
  await expect(panel.getByRole('heading', { name: 'How many countries share a land border with France?' })).toBeVisible()
  await page.keyboard.press('1')
  await expect(panel.getByRole('status')).toContainText('The answer is 8')
  await expect(panel.getByRole('status')).toContainText('France borders 8: Belgium, Germany, Spain')

  await panel.getByRole('button', { name: 'Next stop' }).click()
  const summary = page.getByTestId('quiz-stop-summary')
  await expect(summary.getByText('Level 1 reached')).toBeVisible()
  await expect(summary.getByTestId('quiz-level-reached')).toContainText('Explored')
  const nextStop = summary.getByRole('region', { name: 'Next stop' })
  await expect(nextStop).toContainText('Germany')
  await expect(nextStop).toContainText('Back for level 2')
  await expect(nextStop.locator('img')).toHaveCount(0)
  await summary.getByRole('button', { name: 'Drive to Germany' }).click()

  await expect(panel.getByRole('heading', { name: 'Germany', exact: true })).toBeVisible()
  await expect(panel.getByText('Level 2 of 3')).toBeVisible()
  await expect(panel.getByText(`${2 * BASE_XP} XP per correct answer`)).toBeVisible()
  await expect(panel.getByRole('heading', { name: 'Which flag belongs to Germany?' })).toBeVisible()
  await expect(panel.locator('img[src*="/api/assets/flags/de.svg"]')).toHaveCount(0)
  await expect(panel.getByRole('button', { name: 'Flag C' })).toBeVisible()
})

test('a stop finished in another tab moves the trip on instead of failing', async ({ page }) => {
  const question = (position: number) => ({ position, kind: 'CAPITAL', subject: null, answer: null, options: [place('A', 'А', 'A'), place('B', 'Б', 'B'), place('C', 'В', 'C'), place('D', 'Г', 'D')] })
  let serverStop = 1
  const stop = () => ({
    stop: serverStop, country: serverStop === 1 ? FRANCE : GERMANY, level: 1, levels: 3, arrivalMode: serverStop === 1 ? null : 'DRIVE',
    fromCountryCode: serverStop === 1 ? null : 'FR', questions: [question(1), question(2)], next: null, finished: false, answerXp: BASE_XP,
  })
  const session = () => ({ stats: stats(0, 0, 0, 0, 0, 0), current: stop(), countries: [], trail: [], complete: false })

  await page.route('**/api/quiz-game/**', handled => {
    const path = new URL(handled.request().url()).pathname
    if (path === '/api/quiz-game/session' || path === '/api/quiz-game/session/guest') return json(handled, session())
    if (path.endsWith('/answer')) {
      serverStop = 2
      return json(handled, { status: 409, error: 'Conflict', message: 'This stop has already moved on', code: 'ROUND_NOT_CURRENT' }, 409)
    }
    return handled.abort()
  })

  await page.goto('/games/countries-quiz')
  await page.getByRole('button', { name: /Continue in France|Start the trip/ }).click()
  const panel = page.getByTestId('quiz-question-panel')
  await expect(panel.getByRole('heading', { name: 'France', exact: true })).toBeVisible()
  await panel.getByRole('button', { name: 'B', exact: true }).click()

  await expect(page.getByRole('alert')).toHaveText('Your trip moved on in another tab, so here is where you are now')
  await expect(panel.getByRole('heading', { name: 'Germany', exact: true })).toBeVisible()
})

test('quiz guide opens as a modal with a playable demo question', async ({ page }) => {
  await page.route('**/api/quiz-game/session', route => json(route, { stats: stats(0, 0, 0, 0, 0, 0), current: null, countries: [], trail: [], complete: false }))
  await page.goto('/games/countries-quiz')
  await page.locator('main').getByRole('link', { name: 'How to play' }).click()
  const dialog = page.getByRole('dialog', { name: 'How to play Around the World Quiz' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'Rome' }).click()
  await expect(dialog.getByRole('button', { name: 'Rome' })).toHaveAttribute('aria-pressed', 'true')
  await dialog.getByRole('button', { name: 'Next' }).click()
  await expect(dialog.getByText('About 59M people live in Italy.')).toBeVisible()
  await dialog.getByRole('button', { name: 'Next' }).click()
  await dialog.getByRole('button', { name: 'Next' }).click()
  await dialog.getByRole('button', { name: 'Got it' }).click()
  await expect(dialog).toBeHidden()
})
