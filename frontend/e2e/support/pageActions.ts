import type { Page } from '@playwright/test'

export async function acceptNecessaryCookies(page: Page) {
  await page.addInitScript(() => localStorage.setItem('clueterra_cookieConsent', '1'))
}

export async function exerciseDocumentScroll(page: Page) {
  await page.evaluate(async () => {
    const maximum = Math.max(0, document.documentElement.scrollHeight - window.innerHeight)
    const step = Math.max(300, Math.floor(window.innerHeight * 0.75))
    for (let position = 0; position <= maximum; position += step) {
      window.scrollTo({ top: position, behavior: 'auto' })
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
    }
    window.scrollTo({ top: 0, behavior: 'auto' })
  })
}
