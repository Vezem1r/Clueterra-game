import { expect, test } from '@playwright/test'

const projectImage = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 540"><rect width="720" height="540" fill="#19243a"/><path d="M0 380h720M50 280h620M160 180l80 200m320-200-80 200" stroke="#c6a668" stroke-width="28"/></svg>')}`

const countryHubs = [
  { path: '/projects/countries/united-states', countryName: 'United States', countryCode: 'US', projectCount: 9 },
  { path: '/projects/countries/peoples-republic-of-china', countryName: "People's Republic of China", countryCode: 'CN', projectCount: 7 },
  { path: '/projects/countries/united-kingdom', countryName: 'United Kingdom', countryCode: 'GB', projectCount: 5 },
  { path: '/projects/countries/germany', countryName: 'Germany', countryCode: 'DE', projectCount: 3 },
].map(hub => ({ ...hub, kind: 'COUNTRY', category: null }))

test('airport construction, terminal packages and rail links have separate visible and search rankings', async ({ page }, testInfo) => {
  const projects = [
    ['whole-airport', 'Example International Airport', 'WHOLE'],
    ['heathrow-airport-terminal-5', 'Heathrow Airport Terminal 5', 'EXPANSION'],
    ['sydney-metro-western-sydney-airport', 'Sydney Metro Western Sydney Airport', 'ACCESS_LINK'],
  ].map(([slug, name, rankingScope], index) => ({
    slug, name, rankingScope, rank: index + 1, country: 'Example country', category: 'Airport', year: 2025,
    costUsd: `$${10 - index} billion`, answer: `$${10 - index} billion`,
    costType: 'Approved budget', costScope: 'Scope stated in the project source',
    estimate: false, status: 'Under construction', imageUrl: projectImage, imageAlt: name,
  }))
  await page.route('**/api/project-pages/hubs/most-expensive/airports', route => route.fulfill({
    json: { kind: 'CATEGORY', path: '/projects/most-expensive/airports', category: 'AIRPORT',
      countryCode: null, countryName: null, totalProjects: 3, projects, categoryHubs: [], countryHubs: [] },
  }))
  await page.goto('/projects/most-expensive/airports')
  await expect(page.locator('#airports li')).toHaveCount(1)
  await expect(page.locator('#airports li').first()).toBeInViewport()
  await expect(page.locator('#expansions li')).toContainText('Heathrow Airport Terminal 5')
  await expect(page.locator('#airport-rail li')).toContainText('Sydney Metro Western Sydney Airport')
  await expect(page.locator('#airports')).not.toContainText('Sydney Metro')
  await expect(page.locator('#airports li')).toContainText('Approved budget')
  await expect(page.getByRole('link', { name: 'Try today’s cost challenge' }).first()).toHaveAttribute('href', '/budget')

  const lists = await page.locator('script[type="application/ld+json"]').evaluateAll(scripts => scripts
    .flatMap(script => JSON.parse(script.textContent ?? '{}')['@graph'] ?? [])
    .filter(entry => entry['@type'] === 'ItemList'))
  expect(lists.map(list => list.itemListElement.map((item: { position: number }) => item.position))).toEqual([[1], [1], [1]])
  expect(lists.flatMap(list => list.itemListElement.map((item: { name: string }) => item.name))).toEqual(projects.map(project => project.name))
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  if (process.env.PROJECT_RANKINGS_CAPTURE === '1') {
    await page.screenshot({ path: testInfo.outputPath('airport-ranking.png') })
  }
})

test('project rankings stay compact while every ranking remains a real link', async ({ page }, testInfo) => {
  await page.route('**/api/project-pages/data**', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      projects: [{
        slug: 'example-bridge',
        name: 'Example Bridge',
        country: 'United States',
        category: 'Bridge',
        year: 2024,
        answer: '$1.2B',
        usdEquivalent: null,
        description: 'A bridge with a documented original budget.',
        imageUrl: projectImage,
        imageAlt: 'Example bridge',
        updatedDate: '2026-10-17',
      }],
      pageNumber: 1,
      pageSize: 24,
      totalProjects: 1,
      totalPages: 1,
      countryHubs,
    }),
  }))

  await page.goto('/projects')

  const rankings = page.getByRole('navigation', { name: 'Rankings' })
  await expect(rankings.getByRole('link', { name: /Most expensive overall/ })).toHaveAttribute('href', '/projects/most-expensive')
  await expect(rankings.locator('a[href="/projects/most-expensive/bridges"]')).toHaveText(/Bridges/)
  await expect(rankings.locator('a[href="/projects/countries/germany"]')).toHaveText(/Germany/)
  await expect(page.getByRole('heading', { name: 'Explore the collection' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Example Bridge' })).toBeVisible()

  const closedHeight = await rankings.evaluate(element => element.getBoundingClientRect().height)
  expect(closedHeight).toBeLessThan(testInfo.project.name === 'chromium-mobile' ? 145 : 180)

  if (process.env.PROJECT_RANKINGS_CAPTURE === '1') {
    await page.screenshot({ path: testInfo.outputPath('project-rankings.png'), fullPage: true })
  }

  if (testInfo.project.name === 'chromium-mobile') {
    await page.setViewportSize({ width: 320, height: 700 })
    const hasHorizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)
    expect(hasHorizontalOverflow).toBe(false)
  }

  await rankings.getByText('By type', { exact: true }).click()
  await expect(rankings.getByRole('link', { name: /Bridges/ })).toBeVisible()
  await rankings.getByText('By type', { exact: true }).click()

  await rankings.getByText('By country', { exact: true }).click()
  await rankings.getByRole('searchbox', { name: 'Find a country ranking' }).fill('germany')
  await expect(rankings.getByRole('link', { name: /Germany/ })).toBeVisible()
  await expect(rankings.getByRole('link', { name: /United States/ })).toHaveCount(0)

  await rankings.getByText('By country', { exact: true }).click()
  await rankings.getByText('By country', { exact: true }).click()
  await expect(rankings.getByRole('link', { name: /United States/ })).toBeVisible()

})
