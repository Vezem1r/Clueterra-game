import { expect, test } from '@playwright/test'

test('games catalogue groups daily, unlimited and visual games', async ({ page }, testInfo) => {
  await page.goto('/games')

  const gamesTopPadding = await page.locator('main').evaluate(main => getComputedStyle(main).paddingTop)
  await page.goto('/projects')
  const projectsTopPadding = await page.locator('main').evaluate(main => getComputedStyle(main).paddingTop)
  expect(gamesTopPadding).toBe(projectsTopPadding)
  await page.goto('/games')

  await expect(page).toHaveTitle('Free Geography Games Online | Clueterra')
  await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', 'Play free geography games online: name countries from their shape, guess world flags, find rivers by their course and solve a daily country clue game.')
  await expect(page.getByRole('heading', { level: 1, name: 'Geography games for curious minds.' })).toBeVisible()
  await expect(page.locator('[data-featured-family]')).toHaveCount(2)
  await expect(page.locator('[data-featured-family="clues"] a[href="/"]')).toHaveCount(1)
  await expect(page.locator('[data-featured-family="clues"] a[href="/games/clueterra-unlimited"]')).toHaveCount(0)
  await expect(page.locator('[data-featured-family="budget"] a[href="/budget"]')).toHaveCount(1)
  await expect(page.locator('[data-featured-family="budget"] a[href="/games/budgetguessr-unlimited"]')).toHaveCount(0)
  await expect(page.locator('[data-featured-family]')).toContainText(['Coming soon', 'Coming soon'])
  await expect(page.locator('[data-game-id]')).toHaveCount(4)
  await expect(page.getByRole('heading', { name: 'Explore the world through games' })).toBeVisible()
  await expect(page.locator('section[aria-labelledby="explore-games-heading"] a')).toHaveCount(6)
  expect(await page.locator('section[aria-labelledby="explore-games-heading"] a').evaluateAll(links => links.map(link => link.getAttribute('href')))).toEqual([
    '/', '/games/country-silhouettes', '/games/country-flags', '/games/rivers', '/games/countries-quiz', '/',
  ])
  await expect(page.locator('[data-game-id="silhouettes"]')).toHaveAttribute('href', '/games/country-silhouettes')
  await expect(page.locator('[data-game-id="flags"]')).toHaveAttribute('href', '/games/country-flags')
  await expect(page.locator('[data-game-id="rivers"]')).toHaveAttribute('href', '/games/rivers')
  await expect(page.locator('[data-game-id="countriesQuiz"]')).toHaveAttribute('href', '/games/countries-quiz')
  await expect(page.locator('[data-game-id="silhouettes"]')).not.toContainText('Coming soon')
  await expect(page.locator('[data-game-id="flags"]')).not.toContainText('Coming soon')
  await expect(page.locator('[data-featured-family] img, [data-game-id] img')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Quick games' })).toBeVisible()
  if ((page.viewportSize()?.width ?? 0) < 640) {
    const quickCards = await page.locator('main [data-game-id]').evaluateAll(cards => cards.map(card => ({
      left: Math.round(card.getBoundingClientRect().left),
      width: Math.round(card.getBoundingClientRect().width),
      height: Math.round(card.getBoundingClientRect().height),
    })))
    expect(new Set(quickCards.map(card => card.left)).size).toBe(1)
    expect(quickCards.every(card => card.width > 280 && card.height >= 180 && card.height <= 200)).toBe(true)
  } else if ((page.viewportSize()?.width ?? 0) < 1280) {
    const quickCards = await page.locator('main [data-game-id]').evaluateAll(cards => cards.map(card => Math.round(card.getBoundingClientRect().width)))
    expect(quickCards.every(width => width > 300)).toBe(true)
  }
  await expect(page.getByText('MORE THAN TRIVIA')).toHaveCount(0)
  const legalStrip = page.locator('main + div')
  if ((page.viewportSize()?.width ?? 0) >= 768) {
    await expect(legalStrip).toHaveCSS('position', 'fixed')
  } else {
    await expect(legalStrip).toBeHidden()
  }

  if (process.env.GAMES_CATALOG_CAPTURE === '1') {
    const dismissCookieBanner = page.getByRole('button', { name: 'OK' })
    if (await dismissCookieBanner.isVisible()) await dismissCookieBanner.click()
    await page.screenshot({ path: testInfo.outputPath('games-catalog.png'), fullPage: true })
  }

  const menu = page.getByRole('button', { name: 'Menu' })
  if (await menu.isVisible()) {
    await menu.click()
    await expect(page.locator('#mobile-navigation a[href="/games"]')).toBeVisible()
  } else {
    await expect(page.getByRole('banner').getByRole('link', { name: 'Games' })).toHaveAttribute('href', '/games')
  }

  const horizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)
  expect(horizontalOverflow).toBe(false)

})

test('about page describes the full game collection and links back to it', async ({ page }, testInfo) => {
  await page.goto('/about')

  await expect(page.getByRole('heading', { level: 1, name: 'Geography games built around the real world' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'More ways to play' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Explore all games' })).toHaveAttribute('href', '/games')
  await expect(page.getByRole('link', { name: 'Country Silhouettes' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Guess the Flag' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Rivers' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Countries Quiz' })).toHaveCount(0)
  await expect(page.getByText('Two daily games about the real world')).toHaveCount(0)
  if ((page.viewportSize()?.width ?? 0) >= 1280) {
    const introHeight = await page.locator('main h1 + p').evaluate(intro => intro.getBoundingClientRect().height)
    expect(introHeight).toBeLessThanOrEqual(36)
  }

  await page.evaluate(() => window.scrollTo(0, 600))
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(100)
  await expect.poll(() => page.getByRole('banner').evaluate(header => Math.round(header.getBoundingClientRect().top))).toBe(0)

  if (process.env.GAMES_CATALOG_CAPTURE === '1') {
    const dismissCookieBanner = page.getByRole('button', { name: 'OK' })
    if (await dismissCookieBanner.isVisible()) await dismissCookieBanner.click()
    await page.screenshot({ path: testInfo.outputPath('about-games.png'), fullPage: true })
  }
})

test('about fits a desktop screen without a tiny scroll or footer overlap', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1920, height: 1080 })
  await page.goto('/about')
  await expect(page.getByRole('heading', { level: 1, name: 'Geography games built around the real world' })).toBeVisible()
  const dismissCookieBanner = page.getByRole('button', { name: 'OK' })
  if (await dismissCookieBanner.isVisible()) await dismissCookieBanner.click()
  await expect.poll(() => page.locator('main + div').evaluate(footer => Math.round(footer.getBoundingClientRect().top))).toBeGreaterThan(1000)

  const layout = () => page.evaluate(() => ({
    viewport: window.innerHeight,
    scrollHeight: document.documentElement.scrollHeight,
    lastContentBottom: document.querySelector('main > div > div:last-child')!.getBoundingClientRect().bottom,
    footerTop: document.querySelector('main + div')!.getBoundingClientRect().top,
  }))
  await expect.poll(async () => {
    const { scrollHeight, viewport } = await layout()
    return scrollHeight - viewport
  }).toBeLessThanOrEqual(1)
  await expect.poll(async () => {
    const { footerTop, lastContentBottom } = await layout()
    return footerTop - lastContentBottom
  }).toBeGreaterThanOrEqual(8)

  if (process.env.GAMES_CATALOG_CAPTURE === '1') {
    await page.screenshot({ path: testInfo.outputPath('about-fit-desktop.png'), fullPage: true })
  }

  await page.setViewportSize({ width: 1440, height: 900 })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollHeight)).toBeGreaterThan(980)
})

test('games background fills a tall viewport without a bottom seam', async ({ page }) => {
  const width = page.viewportSize()?.width ?? 1280
  await page.setViewportSize({ width, height: 1200 })
  await page.goto('/games')

  const layout = await page.locator('main').evaluate(main => {
    const pageRoot = main.parentElement!
    return {
      mainBottom: main.getBoundingClientRect().bottom,
      rootBottom: pageRoot.getBoundingClientRect().bottom,
      backdropPosition: getComputedStyle(pageRoot.firstElementChild!).position,
    }
  })

  expect(layout.mainBottom).toBeGreaterThanOrEqual(1199)
  expect(layout.mainBottom).toBeCloseTo(layout.rootBottom, 0)
  expect(layout.backdropPosition).toBe('absolute')
})

test('home remains focused on the daily game', async ({ page }, testInfo) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Explore all games' })).toHaveCount(0)
  await expect(page.locator('[data-game-id]')).toHaveCount(0)

  if (process.env.GAMES_CATALOG_CAPTURE === '1') {
    const dismissCookieBanner = page.getByRole('button', { name: 'OK' })
    if (await dismissCookieBanner.isVisible()) await dismissCookieBanner.click()
    await page.screenshot({ path: testInfo.outputPath('home-daily.png'), fullPage: true })
  }
})
