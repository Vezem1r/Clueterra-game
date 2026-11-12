import { expect, test } from '@playwright/test'

test('guest sees the shared profile with a save prompt in the header after one game', async ({ page }, testInfo) => {
  await page.route('**/api/game/guest/progress', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      hasGuest: true,
      playCount: 1,
      budgetPlayCount: 0,
      totalXp: 325,
      currentLevel: 2,
      levelDivisor: 100,
      currentStreak: 1,
      bestStreak: 1,
      badgesEarned: 1,
      expiresAt: '2026-11-16T00:00:00Z',
    }),
  }))
  await page.route('**/api/profile/guest', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      username: 'guest-42',
      displayName: null,
      avatarUrl: null,
      totalXp: 100,
      level: 1,
      completedRuns: 1,
      countriesSolved: 3,
      perfectRuns: 1,
      solveRate: 75,
      currentStreak: 1,
      bestStreak: 1,
      leaderboardRank: null,
      favoriteDifficulty: 'EASY',
      profileLocked: true,
      xpForCurrentLevel: 0,
      xpForNextLevel: 100,
      levelDivisor: 100,
      streakBonusPercent: 2,
      freezesAvailable: 0,
      budgetStats: {
        completedChallenges: 1,
        projectsExplored: 5,
        projectsMasteredValueUsd: 12000000,
      },
    }),
  }))
  await page.route('**/api/profile/guest/badges**', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      content: [{
        id: '1',
        slug: 'first-win',
        imageUrl: null,
        accentColorKey: 'emerald-core',
        effectColorKey: 'emerald-glow',
        name: 'First victory',
        description: 'Your first challenge win.',
        unlocked: true,
        grantedAt: '2026-10-17T00:00:00Z',
        progress: null,
      }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 9,
      earnedCount: 1,
      lockedVisibleCount: 0,
      hiddenSecretCount: 0,
    }),
  }))

  await page.goto('/profile')

  await expect(page.getByRole('heading', { name: 'Your guest profile' })).toBeVisible()
  await expect(page.getByText('Level', { exact: true })).toHaveCount(0)
  await expect(page.getByText('Total XP', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('325', { exact: false }).first()).toBeVisible()
  await expect(page.getByText('Countries Solved')).toBeVisible()
  await expect(page.getByText('Perfect Games')).toBeVisible()
  await expect(page.getByText('Projects explored')).toBeVisible()
  if (testInfo.project.name === 'chromium-mobile') {
    await expect(page.getByText('First victory')).toBeVisible()
  } else {
    await expect(page.getByText('First victory')).toBeHidden()
  }
  await expect(page.getByText('Just for you')).toBeVisible()
  await expect(page.getByText('Not saved permanently')).toBeVisible()
  await expect(page.getByText('Global Rank', { exact: true })).toHaveCount(0)
  await expect(page.getByText('Favorite Difficulty', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Create account' })).toHaveAttribute('href', '/register?redirect=%2Fprofile')
  await expect(page.getByRole('heading', { name: 'Your guest profile' }).locator('..').getByRole('link', { name: 'Create account' })).toBeVisible()
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex,nofollow')
  if (process.env.GUEST_PROFILE_CAPTURE === '1') {
    await page.waitForTimeout(700)
    await page.screenshot({ path: testInfo.outputPath('guest-profile.png'), fullPage: true })
  }
})

test('profile without a completed guest game invites the player to start', async ({ page }) => {
  await page.route('**/api/game/guest/progress', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ hasGuest: false, playCount: 0, budgetPlayCount: 0 }),
  }))

  await page.goto('/profile')

  await expect(page.getByRole('heading', { name: 'Your story starts with one game' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Play Clueterra' })).toBeVisible()
  await expect(page.getByText('First victory')).toHaveCount(0)
})

test('old guest profile URL redirects to the shared profile route', async ({ page }) => {
  await page.goto('/guest/profile')
  await expect(page).toHaveURL(/\/profile$/)
})
