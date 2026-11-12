import { test as base } from '@playwright/test'

import { RuntimeAudit } from './runtimeAudit'
import { acceptNecessaryCookies } from './pageActions'

interface AuditFixtures {
  runtimeAudit: RuntimeAudit
}

export const test = base.extend<AuditFixtures>({
  runtimeAudit: async ({ page }, provide, testInfo) => {
    await acceptNecessaryCookies(page)
    await page.emulateMedia({ reducedMotion: 'no-preference' })
    const runtimeAudit = new RuntimeAudit(page, testInfo)
    await runtimeAudit.install()
    await provide(runtimeAudit)
    await runtimeAudit.attachSummary()
  },
})

export { expect } from '@playwright/test'
