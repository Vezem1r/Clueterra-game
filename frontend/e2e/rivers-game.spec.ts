import { readFileSync } from 'node:fs'
import { expect, test, type Page } from '@playwright/test'

const riverAssets = new URL('../../backend/clueterra-questions/src/main/resources/assets/river-game/', import.meta.url)
const catalog = JSON.parse(readFileSync(new URL('catalog.json', riverAssets), 'utf8'))
const sampleMap = readFileSync(new URL(catalog.entries[0].plainImageKey.slice('river-game/'.length), riverAssets))
const sampleCourse = catalog.entries[0].course
const sampleLine = sampleCourse.coordinates[0]
const [sampleLng, sampleLat] = sampleLine[Math.floor(sampleLine.length / 2)]
const SAMPLE_JPEG = Buffer.from('/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==', 'base64')
const COLOGNE = { name: 'Cologne', lng: sampleLng, lat: sampleLat }
const EMPTY_STATS = { attemptedRivers: 0, solvedRivers: 0, answerAttempts: 0, skips: 0, xpAwarded: 0, currentQuestionId: null }
const RHINE_CLUES = [
  { position: 1, kind: 'GEOGRAPHY', text: 'Its waters flow into the North Sea.', place: null },
  { position: 2, kind: 'CITY', text: 'Cologne lies on its banks.', place: COLOGNE },
  { position: 3, kind: 'NAME', text: 'Its name starts with “R” and has 5 letters.', place: null },
]
const RHINE_FACTS = [
  { kind: 'LENGTH', text: 'Its reported length is about 1,230 km.' },
  { kind: 'COUNTRIES', text: 'Countries along its course include Germany.' },
  { kind: 'GEOGRAPHY', text: 'Its waters flow into the North Sea.' },
  { kind: 'CITY', text: 'Cologne lies on its banks.', place: COLOGNE },
]

const fitsViewport = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollHeight <= window.innerHeight + 1)

test('river guide keeps the FAQ off the game screen and opens its answers', async ({ page }) => {
  await page.goto('/games/rivers/how-to')
  await expect(page.getByRole('heading', { level: 1, name: 'How to play the river quiz' })).toBeVisible()
  const question = page.locator('details').filter({ hasText: 'Is the river quiz free?' })
  await question.locator('summary').click()
  await expect(question).toHaveAttribute('open', '')
  await expect(question.getByText('You can play without an account', { exact: false })).toBeVisible()
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', 'https://clueterra.com/games/rivers/how-to')
})

test('guest river game reveals clues, answers with a photo, defers, gives up and resumes', async ({ page }) => {
  let currentId: number | null = null
  let nextId = 101
  let hintsUsed = 0
  let attempts = 0
  let round = 0
  let solved = 0

  const advance = () => {
    currentId = nextId++
    hintsUsed = 0
    attempts = 0
    round++
  }
  const question = () => ({
    id: currentId,
    difficulty: currentId === 101 ? 'EASY' : 'MEDIUM',
    imageUrl: `/assets/rivers/${String(currentId).padStart(64, hintsUsed >= 2 ? 'b' : 'a')}.webp`,
    course: sampleCourse,
    revealedHints: RHINE_CLUES.slice(0, hintsUsed),
    attempts,
    hintsUsed,
    maximumXp: 200 - hintsUsed * 30,
    cluePenaltyXp: 30,
    roundNumber: round,
  })
  const reveal = (withPhoto: boolean) => ({
    name: 'Rhine',
    countryCode: 'DE',
    lengthKm: 1230,
    mapImageUrl: `/assets/rivers/${'c'.repeat(64)}.webp`,
    facts: RHINE_FACTS,
    photo: withPhoto ? {
      url: `/assets/rivers/photos/${'d'.repeat(64)}.jpg`,
      authorName: 'Jane Doe',
      licenseLabel: 'CC BY-SA 4.0',
      licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0',
      sourcePageUrl: 'https://commons.wikimedia.org/wiki/File:Loreley.jpg',
    } : null,
    wikipediaUrl: 'https://en.wikipedia.org/wiki/Rhine',
    wikidataUrl: 'https://www.wikidata.org/wiki/Q584',
  })
  const stats = () => ({ attemptedRivers: solved, solvedRivers: solved, answerAttempts: attempts, skips: 0, xpAwarded: solved * 140, currentQuestionId: currentId })
  const json = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })

  await page.route('**/api/legal/current', route => route.fulfill(json({ termsVersion: 'test-terms', privacyVersion: 'test-privacy' })))
  await page.route('**/api/game/guest/progress', route => route.fulfill(json({ hasGuest: false })))
  await page.route('**/tiles/**', route => route.fulfill({ status: 404, body: '' }))
  await page.route('**/assets/rivers/**', route => route.request().url().includes('/photos/')
    ? route.fulfill({ status: 200, contentType: 'image/jpeg', body: SAMPLE_JPEG })
    : route.fulfill({ status: 200, contentType: 'image/webp', body: sampleMap }))
  await page.route('**/api/rivers/**', route => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    if (path === '/api/rivers/session' && method === 'GET') {
      return route.fulfill(json({ stats: stats(), current: currentId === null ? null : question() }))
    }
    if (path === '/api/rivers/session/guest' && method === 'POST') {
      if (currentId === null) advance()
      return route.fulfill(json(question()))
    }
    if (path === '/api/rivers/session/next' && method === 'POST') {
      advance()
      return route.fulfill(json(question()))
    }
    if (path === '/api/rivers/names') return route.fulfill(json(['Rhine', 'Rhone']))
    if (path.endsWith('/hint')) {
      hintsUsed++
      return route.fulfill(json(question()))
    }
    if (path.endsWith('/skip')) {
      advance()
      return route.fulfill(json(question()))
    }
    if (path.endsWith('/answer')) {
      currentId = null
      return route.fulfill(json({ reveal: reveal(false), stats: stats() }))
    }
    if (path.endsWith('/guess')) {
      const correct = route.request().postDataJSON().answer === 'Rhine'
      attempts++
      if (correct) {
        currentId = null
        solved++
      }
      return route.fulfill(json({ correct, attempts, xpAwarded: correct ? 140 : 0, reveal: correct ? reveal(true) : null, stats: stats() }))
    }
    return route.abort()
  })

  await page.goto('/games/rivers')
  await expect(page.getByRole('heading', { level: 1, name: 'Guess the river' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Interactive relief globe' })).toBeVisible()
  await expect(page.locator('main').getByRole('link', { name: 'How to play' })).toHaveAttribute('href', '/games/rivers/how-to')
  const cookieBanner = page.getByRole('button', { name: 'OK' })
  if (await cookieBanner.isVisible()) await cookieBanner.click()
  expect(await fitsViewport(page)).toBe(true)
  const introMapTop = await page.getByTestId('river-globe').evaluate(element => element.getBoundingClientRect().top)

  await page.locator('main').getByRole('link', { name: 'How to play' }).click()
  await expect(page).toHaveURL(/guide=river/)
  const guide = page.getByRole('dialog', { name: 'How to play Guess the river' })
  await expect(guide.getByRole('heading', { name: 'Explore the globe' })).toBeVisible()
  await guide.getByRole('button', { name: 'Next' }).click()
  await guide.getByRole('button', { name: /Show a place on its course/ }).click()
  await expect(guide.getByText('Iquitos lies on its banks.')).toBeVisible()
  await guide.getByRole('button', { name: 'Next' }).click()
  await guide.getByRole('button', { name: 'Got it' }).click()
  await expect(page).not.toHaveURL(/guide=/)

  await page.getByRole('button', { name: 'Start playing' }).click()
  await expect(page.getByTestId('river-answer-panel').getByText('River 1')).toBeVisible()
  const activeMapTop = await page.getByTestId('river-globe').evaluate(element => element.getBoundingClientRect().top)
  expect(Math.abs(activeMapTop - introMapTop), 'The globe should stay in place between the intro and the round').toBeLessThanOrEqual(2)
  const skipBackground = await page.getByRole('button', { name: 'Play it later' }).evaluate(element => getComputedStyle(element).backgroundColor)
  const answerBackground = await page.getByRole('button', { name: 'Show the answer' }).evaluate(element => getComputedStyle(element).backgroundColor)
  expect(skipBackground).not.toBe(answerBackground)
  await expect(page.getByText('Worth 200 XP')).toBeVisible()
  await expect(page.getByRole('button', { name: /Show where it flows.*−30 XP/ })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Mystery river course on a relief globe' })).toBeVisible()
  await page.getByRole('button', { name: 'Why does the river line have gaps?' }).focus()
  await expect(page.getByRole('tooltip')).toBeVisible()
  expect(await fitsViewport(page)).toBe(true)

  const answer = page.getByRole('textbox', { name: 'River name' })
  await answer.fill('Rh')
  await expect(page.getByRole('option', { name: 'Rhine' })).toBeVisible()
  await answer.clear()

  await page.getByRole('button', { name: /Show where it flows/ }).click()
  await expect(page.getByText('Its waters flow into the North Sea.')).toBeVisible()
  await expect(page.getByText('Worth 170 XP')).toBeVisible()
  await page.getByRole('button', { name: /Show a place on its course/ }).click()
  await expect(page.getByText('Cologne lies on its banks.')).toBeVisible()
  const cityPin = page.getByTestId('river-globe').getByText('Cologne', { exact: true })
  await expect(cityPin).toBeVisible()
  const pinBox = await cityPin.boundingBox()
  const viewport = page.viewportSize()
  expect(pinBox != null && viewport != null && pinBox.x > 0 && pinBox.y > 0
    && pinBox.x < viewport.width && pinBox.y < viewport.height, 'The city pin should sit on the river in view').toBe(true)
  await expect(page.getByRole('region', { name: 'Mystery river course with country borders' })).toBeVisible()
  expect(await fitsViewport(page)).toBe(true)

  await answer.fill('Danube')
  await page.getByRole('button', { name: 'Check answer' }).click()
  await expect(page.getByText('“Danube” is not this river.')).toBeVisible()
  await answer.fill('Rhine')
  await answer.press('Enter')
  const revealCard = page.getByTestId('river-reveal')
  await expect(revealCard.getByRole('heading', { name: 'Rhine' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'The course of the Rhine with country borders' })).toBeVisible()
  await expect(revealCard.getByText('+140 XP')).toBeVisible()
  await expect(revealCard.getByText('Its waters flow into the North Sea.')).toBeVisible()
  await expect(revealCard.getByText('Its reported length is about 1,230 km.')).toBeVisible()
  await expect(revealCard.getByRole('link', { name: 'Photo: Jane Doe, CC BY-SA 4.0' }))
    .toHaveAttribute('href', 'https://commons.wikimedia.org/wiki/File:Loreley.jpg')
  await expect(revealCard.getByRole('link', { name: /Wikipedia/ })).toHaveAttribute('href', 'https://en.wikipedia.org/wiki/Rhine')
  await expect(page.getByRole('button', { name: /Next river/ })).toBeFocused()
  expect(await fitsViewport(page)).toBe(true)

  await page.getByRole('button', { name: /Next river/ }).click()
  await expect(page.getByTestId('river-answer-panel').getByText('River 2')).toBeVisible()
  await page.getByRole('button', { name: 'Play it later' }).click()
  await expect(page.getByTestId('river-answer-panel').getByText('River 3')).toBeVisible()
  await page.getByRole('button', { name: 'Show the answer' }).click()
  await expect(revealCard.getByText('The answer is')).toBeVisible()
  await expect(revealCard.getByText('No XP this time. It will come back in a later round.')).toBeVisible()
  await expect(revealCard.getByRole('img')).toHaveCount(0)

  await page.getByRole('button', { name: /Next river/ }).click()
  await expect(page.getByTestId('river-answer-panel').getByText('River 4')).toBeVisible()
  await page.getByLabel('Breadcrumb').getByRole('button', { name: 'Guess the river' }).click()
  await page.getByRole('button', { name: 'Continue river 4' }).click()
  await answer.fill('Dan')
  await page.reload()
  await expect(page.getByText('Your river is saved')).toBeVisible()
  await expect(page.getByText('Unsent guess: Dan')).toBeVisible()
  await expect(page.getByRole('region', { name: 'Mystery river course on a relief globe' })).toBeVisible()
  expect(await fitsViewport(page)).toBe(true)
  await page.getByRole('button', { name: 'Continue river 4' }).click()
  await expect(page.getByRole('textbox', { name: 'River name' })).toHaveValue('Dan')
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await fitsViewport(page)).toBe(true)
  await page.getByLabel('Breadcrumb').getByRole('button', { name: 'Guess the river' }).click()
  expect(await fitsViewport(page)).toBe(true)
  await page.setViewportSize({ width: 360, height: 640 })
  expect(await fitsViewport(page)).toBe(true)
  expect(await page.locator('section[aria-labelledby="river-title"] > div').first()
    .evaluate(element => element.scrollHeight <= element.clientHeight + 1)).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)).toBe(false)
})

for (const [language, heading] of [['ru', 'Угадай реку'], ['cs', 'Poznej řeku']] as const) {
  test(`river game intro follows the ${language} site locale and fits a small phone`, async ({ page }) => {
    await page.addInitScript(locale => {
      localStorage.setItem('clueterra-settings', JSON.stringify({ state: { language: locale }, version: 0 }))
    }, language)
    await page.route('**/api/game/guest/progress', route => route.fulfill({
      status: 200, contentType: 'application/json', body: '{"hasGuest":false}',
    }))
    await page.route('**/api/rivers/session', route => route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify({ stats: EMPTY_STATS, current: null }),
    }))

    await page.goto('/games/rivers')
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
    const cookieBanner = page.getByRole('button', { name: 'OK' })
    if (await cookieBanner.isVisible()) await cookieBanner.click()
    await page.setViewportSize({ width: 375, height: 667 })
    expect(await fitsViewport(page)).toBe(true)
    await page.setViewportSize({ width: 360, height: 640 })
    expect(await fitsViewport(page)).toBe(true)
    expect(await page.locator('section[aria-labelledby="river-title"] > div').first()
      .evaluate(element => element.scrollHeight <= element.clientHeight + 1)).toBe(true)
  })
}
