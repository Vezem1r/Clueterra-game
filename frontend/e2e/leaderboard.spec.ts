import { expect, test, type Page } from '@playwright/test'

const names = [
  'North Star', 'Meridian', 'Astra', 'The Cartographer', 'Saffron Atlas',
  'Long Distance Explorer', 'Vesper', 'Moss', 'Skyline', 'Terra',
]

const entries = Array.from({ length: 50 }, (_, index) => ({
  rank: index + 1,
  username: `player${index + 1}`,
  displayName: names[index % names.length],
  avatarUrl: null,
  totalXp: 528_400 - index * 8_350,
  level: Math.max(1, 58 - index),
  challengesWon: Math.max(0, 142 - index * 2),
}))

async function signIn(page: Page, rank: number, totalXp: number, username = 'explorer') {
  await page.addInitScript(() => localStorage.setItem('clu_auth_session', '1'))
  await page.route('**/api/auth/refresh', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ email: 'explorer@example.test', username, role: 'USER', expiresIn: 3600, totpRequired: false, legalAcceptanceRequired: false, userId: '1' }),
  }))
  await page.route('**/api/profile', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ username, displayName: 'Test Explorer', avatarUrl: null, totalXp, level: 42, leaderboardRank: rank }),
  }))
  await page.route('**/api/profile/badges/unacknowledged', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: '[]',
  }))
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/leaderboard**', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ entries, page: 0, size: 50, totalElements: 50, totalPages: 1 }),
  }))
})

test('leaderboard requests compact avatars instead of full-size uploads', async ({ page }) => {
  const avatarFile = '0c1475b3-41cb-4eec-ae28-65f466530a23.png'
  const avatarUrl = `https://clueterra.com/storage/avatars/42/${avatarFile}`
  await page.route('**/api/leaderboard?*', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      entries: entries.map((entry, index) => index === 0 ? { ...entry, avatarUrl } : entry),
      page: 0, size: 50, totalElements: 50, totalPages: 1,
    }),
  }))
  await page.route('**/api/assets/avatars/**', route => route.fulfill({
    status: 200,
    contentType: 'image/jpeg',
    body: '',
  }))

  await page.goto('/leaderboard')

  const avatars = page.locator(`img[src="/api/assets/avatars/42/${avatarFile}/128.jpg"]`)
  await expect(avatars).toHaveCount(2)
  await expect(avatars.first()).toHaveAttribute('loading', 'eager')
  await expect(avatars.last()).toHaveAttribute('loading', 'lazy')
})

test('guest sees the chase and can open a ranked profile', async ({ page }, testInfo) => {
  await page.goto('/leaderboard')
  await expect(page.getByRole('heading', { name: 'Full leaderboard' })).toBeVisible()
  await expect(page.getByRole('tab')).toHaveCount(0)
  await expect(page.getByText('#01 — #25')).toBeVisible()
  await expect(page.getByText('#26 — #50')).toBeVisible()
  await expect(page.locator('.leaderboard-fill')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /Rank 2:.*XP/i })).not.toContainText('8,350 XP')
  await expect(page.getByRole('button', { name: /Rank 1:.*XP/i })).toHaveCount(2)
  await expect(page.getByRole('button', { name: /Rank 50:.*XP/i })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
  if (process.env.LEADERBOARD_CAPTURE === '1') {
    await page.screenshot({ path: testInfo.outputPath('chase-guest.png'), fullPage: false })
  }
  await page.getByRole('button', { name: 'OK' }).click()
  await page.getByRole('button', { name: /Rank 50:.*XP/i }).click()
  await expect(page).toHaveURL(/\/profile\/player50/)
})

test('signed-in player sees their own rank and the next player to pass', async ({ page }, testInfo) => {
  await signIn(page, 23, entries[22].totalXp)
  await page.goto('/leaderboard')
  await expect(page.getByText('Test Explorer')).toBeVisible()
  await expect(page.getByText('Your Position · #23')).toBeVisible()
  await expect(page.getByRole('button', { name: /Rank 22:.*XP/i })).toHaveCount(2)
  await expect(page.getByRole('button', { name: /Rank 22:.*XP/i }).first()).toContainText('8,350 XP')
  if (process.env.LEADERBOARD_CAPTURE === '1') {
    await page.screenshot({ path: testInfo.outputPath('chase-player.png'), fullPage: false })
  }
})

test('leader sees the closest challenger instead of a duplicate own card', async ({ page }) => {
  await signIn(page, 1, entries[0].totalXp, 'player1')
  await page.goto('/leaderboard')
  await expect(page.getByText('Closest challenger').first()).toBeVisible()
  await expect(page.getByRole('button', { name: /Rank 2:.*XP/i })).toHaveCount(2)
  await expect(page.getByText('Lead: 8,350 XP')).toBeVisible()
  await expect(page.getByText('Gap to first place')).toHaveCount(0)
})

test('player outside the Top 50 sees the entry threshold', async ({ page }) => {
  await signIn(page, 74, 90_000)
  await page.goto('/leaderboard')
  await expect(page.getByText('Your Position · #74')).toBeVisible()
  await expect(page.getByText('Outside top 50')).toBeVisible()
  await expect(page.getByText('Top 50 threshold')).toBeVisible()
  await expect(page.getByText('29,250 XP')).toBeVisible()
})

test('chase fits a narrow mobile viewport', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 320, height: 800 })
  await page.goto('/leaderboard')
  await expect(page.getByRole('button', { name: /Rank 50:.*XP/i })).toBeVisible()
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow, 'Leaderboard overflows at 320px').toBeLessThanOrEqual(2)
  if (process.env.LEADERBOARD_CAPTURE === '1') {
    await page.screenshot({ path: testInfo.outputPath('chase-mobile.png'), fullPage: false })
  }
})

test('mobile navigation opens and closes without leaving hidden links focusable', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 })
  await page.goto('/leaderboard')
  const navigation = page.locator('#mobile-navigation')
  await expect(navigation).toHaveAttribute('inert', '')

  await page.getByRole('button', { name: 'Menu' }).click()
  await expect(navigation).not.toHaveAttribute('inert')
  await navigation.getByRole('link', { name: 'Friends' }).click()
  await expect(page).toHaveURL(/\/friends$/)
  await expect(navigation).toHaveAttribute('inert', '')
})

test('shared footer appears on leaderboard and friends', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium-desktop', 'Shared footer is desktop-only')
  for (const path of ['/leaderboard', '/friends']) {
    await page.goto(path)
    await expect(page.getByRole('link', { name: 'About Clueterra' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Data Sources' })).toBeVisible()
  }
})

test('footer loads legal details only when requested', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium-desktop', 'The footer is desktop-only')
  await page.goto('/leaderboard')
  await page.getByRole('button', { name: 'Privacy Policy' }).click()
  await expect(page.getByRole('dialog', { name: 'Privacy Policy' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: 'Privacy Policy' })).toBeHidden()
})
