import { expect, test } from '@playwright/test'

const OAUTH_REDIRECT_KEY = 'clueterra.oauth.redirect'
const FOREIGN_HOST = 'evil.example'
const FOREIGN_REDIRECTS = [
  `/\\${FOREIGN_HOST}`,
  `/\t/${FOREIGN_HOST}`,
  `/\n/${FOREIGN_HOST}`,
  `//${FOREIGN_HOST}`,
  `https://${FOREIGN_HOST}`,
  `javascript:alert(1)`,
]
const AUTH_RESPONSE = {
  email: 'explorer@example.test',
  username: 'explorer',
  role: 'USER',
  expiresIn: 3600,
  totpRequired: false,
  legalAcceptanceRequired: false,
  userId: '1',
}

test.beforeEach(async ({ page }) => {
  await page.route(`**://${FOREIGN_HOST}/**`, route => route.abort())
})

for (const redirect of FOREIGN_REDIRECTS) {
  test(`login drops foreign redirect ${JSON.stringify(redirect)}`, async ({ page }) => {
    await page.goto(`/login?redirect=${encodeURIComponent(redirect)}`)

    await expect(page.locator('a[href^="/register?redirect="]')).toHaveAttribute('href', '/register?redirect=%2F')
  })

  test(`oauth callback drops stored foreign redirect ${JSON.stringify(redirect)}`, async ({ page }) => {
    await page.route('**/api/auth/oauth2/exchange', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(AUTH_RESPONSE),
    }))
    await page.goto('/login')
    await page.evaluate(([key, value]) => sessionStorage.setItem(key, value), [OAUTH_REDIRECT_KEY, redirect])

    await page.goto('/oauth/callback?code=test-code')

    await expect(page).toHaveURL(url => url.pathname === '/' && url.host !== FOREIGN_HOST)
  })
}

test('login keeps a same-origin redirect with query and hash', async ({ page }) => {
  await page.goto(`/login?redirect=${encodeURIComponent('/leaderboard?tab=weekly#me')}`)

  await expect(page.locator('a[href^="/register?redirect="]'))
    .toHaveAttribute('href', `/register?redirect=${encodeURIComponent('/leaderboard?tab=weekly#me')}`)
})

test('oauth buttons store only the sanitized redirect', async ({ page }) => {
  await page.route('**/api/auth/oauth2/github', route => route.fulfill({ status: 204 }))
  await page.goto(`/login?redirect=${encodeURIComponent(`/\\${FOREIGN_HOST}`)}`)

  await page.getByRole('button', { name: 'GitHub' }).click()

  await expect.poll(() => page.evaluate(key => sessionStorage.getItem(key), OAUTH_REDIRECT_KEY)).toBe('/')
})
