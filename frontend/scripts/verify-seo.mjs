const requestedOrigin = process.argv[2]

if (!requestedOrigin) {
  console.error('Usage: npm run verify:seo -- https://clueterra.com')
  process.exit(1)
}

const origin = new URL(requestedOrigin)
origin.pathname = '/'
origin.search = ''
origin.hash = ''

const publicPages = [
  ['/', 'Clueterra: Daily geography game with real-world clues', 'Daily geography game Guess the country from real-world clues'],
  ['/games', 'Free Geography Games Online | Clueterra', 'Geography games for curious minds.'],
  ['/games/rivers/how-to', 'How to play the river quiz | Clueterra', 'How to play the river quiz'],
  ['/games/country-silhouettes/how-to', 'How to play Country silhouettes | Clueterra', 'How to play Country silhouettes'],
  ['/games/country-flags/how-to', 'How to play Guess the flag | Clueterra', 'How to play Guess the flag'],
  ['/ru/games/country-flags/how-to', 'Как играть в «Угадай флаг» | Clueterra', 'Как играть в «Угадай флаг»'],
  ['/games/countries-quiz/how-to', 'How to play Around the World Quiz | Clueterra', 'How to play Around the World Quiz'],
  ['/cs/games/rivers/how-to', 'Jak hrát kvíz o řekách | Clueterra', 'Jak hrát kvíz o řekách'],
  ['/about', 'About Clueterra | Geography Games Built on Real Data', 'Geography games built around the real world'],
  ['/how-to', 'How to Play Clueterra | Geography Game Guide', 'How to play Clueterra'],
  ['/budget', 'BudgetGuessr — Guess real megaproject costs', 'BudgetGuessr — Guess real megaproject costs'],
  ['/budget/how-to', 'How to Play BudgetGuessr | Megaproject Cost Game Guide', 'How to play BudgetGuessr'],
  ['/terms', 'Terms of Service | Clueterra', 'Terms of Service'],
  ['/privacy', 'Privacy Policy | Clueterra', 'Privacy Policy'],
  ['/data-sources', 'Data Sources and Attributions | Clueterra', 'Data Sources'],
]
const projectPages = [
  ['/projects', 'Real megaproject costs and sources | Clueterra', 'Real megaproject costs, with the scope attached'],
  ['/projects/most-expensive', 'The Most Expensive Megaprojects in Our Catalogue | Clueterra', 'The most expensive megaprojects in our catalogue'],
  ['/projects/most-expensive/stadiums', 'The Most Expensive Stadiums in Our Catalogue | Clueterra', 'The most expensive stadiums in our catalogue'],
  ['/projects/countries/united-kingdom', 'The Most Expensive Megaprojects from the United Kingdom in Our Catalogue | Clueterra', 'The most expensive megaprojects from the United Kingdom in our catalogue'],
  ['/projects/cassini-huygens', 'How much did Cassini–Huygens cost? | Clueterra', 'How much did Cassini–Huygens cost?'],
  ['/projects/insight-mars-lander', "How much did NASA's InSight mission cost? | Clueterra", 'How much did InSight cost?'],
  ['/projects/maven-mars-orbiter', "How much did NASA's MAVEN mission cost? | Clueterra", 'How much did MAVEN cost?'],
  ['/projects/osiris-rex', 'How much did OSIRIS-REx cost? | Clueterra', 'How much did OSIRIS-REx cost?'],
  ['/projects/parker-solar-probe', 'How much did Parker Solar Probe cost? | Clueterra', 'How much did Parker Solar Probe cost?'],
  ['/projects/mars-2020', 'How much did the Mars 2020 mission cost? | Clueterra', 'How much did Mars 2020 cost?'],
  ['/projects/akashi-kaikyo-bridge', 'How much did Akashi Kaikyō Bridge cost? | Clueterra', 'How much did Akashi Kaikyō Bridge cost?'],
  ['/projects/follo-line', "How much did Norway's Follo Line cost? | Clueterra", 'How much did Follo Line cost?'],
  ['/projects/three-mile-island', 'How much did Three Mile Island cost to build? | Clueterra', 'How much did Three Mile Island Nuclear Generating Station cost?'],
  ['/projects/erfurt-leipzig-halle-high-speed-railway', 'Erfurt–Leipzig/Halle railway cost | Clueterra', 'How much did Erfurt–Leipzig/Halle high-speed railway cost?'],
]
const privatePages = [
  '/play',
  '/results',
  '/login',
  '/register',
  '/leaderboard',
  '/profile',
  '/profile/seo-verification-user',
  '/friends',
  '/legal/update',
  '/projects/preview/Q1',
  '/admin',
]
const expectedCanonicalOrigin = 'https://clueterra.com'
const publicRenderResources = [
  '/api/legal/current',
  '/api/budget/challenges/today',
  '/api/budget/sessions/today',
]
const failures = []

function expect(condition, message) {
  if (!condition) failures.push(message)
}

function tagAttribute(html, tagPattern, attribute) {
  const tag = html.match(tagPattern)?.[0]
  return tag?.match(new RegExp(`${attribute}="([^"]*)"`, 'i'))?.[1] ?? null
}

function headingText(html) {
  return (html.match(/<h1\b[^>]*>(.*?)<\/h1>/is)?.[1] ?? '')
    .replace(/<!--.*?-->/gs, '')
    .replace(/<[^>]+>/g, '')
    .replaceAll('&#x27;', "'")
    .replaceAll('&amp;', '&')
    .trim()
}

async function request(path, options = {}) {
  const response = await fetch(new URL(path, origin), options)
  const body = await response.text()
  return { response, body }
}

for (const [path, expectedTitle, expectedHeading] of publicPages) {
  const { response, body } = await request(path)
  const canonical = tagAttribute(body, /<link\s+[^>]*rel="canonical"[^>]*>/i, 'href')
  const robots = tagAttribute(body, /<meta\s+[^>]*name="robots"[^>]*>/i, 'content')
  const title = body.match(/<title>(.*?)<\/title>/is)?.[1] ?? null
  const expectedCanonical = `${expectedCanonicalOrigin}${path}`

  expect(response.status === 200, `${path}: expected HTTP 200, received ${response.status}`)
  expect(response.headers.get('content-type')?.includes('text/html'), `${path}: expected an HTML response`)
  expect(title === expectedTitle, `${path}: unexpected title "${title}"`)
  expect(canonical === expectedCanonical, `${path}: expected canonical ${expectedCanonical}, received ${canonical}`)
  expect(robots?.replaceAll(' ', '') === 'index,follow', `${path}: expected index,follow meta robots, received ${robots}`)
  expect(!response.headers.has('x-robots-tag'), `${path}: must not return an X-Robots-Tag header`)
  expect(body.includes('data-render-mode="ssg"'), `${path}: pre-rendered React markup is missing`)
  expect(!body.includes('data-static-seo-fallback'), `${path}: obsolete static fallback must not be present`)
  expect(headingText(body) === expectedHeading, `${path}: expected rendered H1 "${expectedHeading}", received "${headingText(body)}"`)
}

for (const [path, expectedTitle, expectedHeading] of projectPages) {
  const { response, body } = await request(path)
  const canonical = tagAttribute(body, /<link\s+[^>]*rel="canonical"[^>]*>/i, 'href')
  const robots = tagAttribute(body, /<meta\s+[^>]*name="robots"[^>]*>/i, 'content')
  const title = body.match(/<title>(.*?)<\/title>/is)?.[1] ?? null
  const expectedCanonical = `${expectedCanonicalOrigin}${path}`

  expect(response.status === 200, `${path}: expected HTTP 200, received ${response.status}`)
  expect(response.headers.get('content-type')?.includes('text/html'), `${path}: expected an HTML response`)
  expect(title === expectedTitle, `${path}: unexpected title "${title}"`)
  expect(canonical === expectedCanonical, `${path}: expected canonical ${expectedCanonical}, received ${canonical}`)
  expect(robots?.replaceAll(' ', '') === 'index,follow', `${path}: expected index,follow meta robots, received ${robots}`)
  expect(!response.headers.has('x-robots-tag'), `${path}: must not return an X-Robots-Tag header`)
  expect(body.includes('data-render-mode="ssr"'), `${path}: runtime-rendered React markup is missing`)
  expect(headingText(body) === expectedHeading, `${path}: expected rendered H1 "${expectedHeading}", received "${headingText(body)}"`)
  expect(body.includes('application/ld+json'), `${path}: structured data is missing`)
}

for (const path of privatePages) {
  const { response, body } = await request(path)
  const canonical = tagAttribute(body, /<link\s+[^>]*rel="canonical"[^>]*>/i, 'href')
  const robotsMeta = tagAttribute(body, /<meta\s+[^>]*name="robots"[^>]*>/i, 'content')
  const robotsHeader = response.headers.get('x-robots-tag')

  expect(response.status === 200, `${path}: expected an existing SPA route with HTTP 200, received ${response.status}`)
  expect(robotsMeta?.replaceAll(' ', '') === 'noindex,nofollow', `${path}: missing noindex,nofollow meta robots`)
  expect(robotsHeader?.replaceAll(' ', '') === 'noindex,nofollow', `${path}: missing noindex,nofollow X-Robots-Tag`)
  expect(canonical === null, `${path}: private route must not expose a canonical URL`)
  expect(body.includes('data-render-mode="csr"'), `${path}: private application route must use the CSR shell`)
}

const missingPath = '/seo-verification-route-that-must-not-exist'
const missing = await request(missingPath)
expect(missing.response.status === 404, `${missingPath}: expected HTTP 404, received ${missing.response.status}`)
expect(missing.response.headers.get('x-robots-tag')?.includes('noindex'), `${missingPath}: missing noindex header`)
expect(missing.body.includes('<title>Page Not Found | Clueterra</title>'), `${missingPath}: branded 404 HTML was not returned`)

const localizedPages = ['/games/country-flags', '/ru/games/country-flags', '/cs/games/country-silhouettes/how-to']
for (const path of localizedPages) {
  const { body } = await request(path)
  for (const hreflang of ['en', 'ru', 'cs', 'x-default']) {
    expect(new RegExp(`<link rel="alternate" hreflang="${hreflang}" href="${expectedCanonicalOrigin}/`).test(body), `${path}: missing hreflang ${hreflang}`)
  }
}

const sitemap = await request('/sitemap.xml')
expect(sitemap.response.status === 200, `/sitemap.xml: expected HTTP 200, received ${sitemap.response.status}`)
expect(sitemap.response.headers.get('content-type')?.includes('application/xml'), '/sitemap.xml: expected application/xml')
for (const [path] of publicPages) {
  expect(sitemap.body.includes(`<loc>${expectedCanonicalOrigin}${path}</loc>`), `/sitemap.xml: missing ${path}`)
}
for (const [path] of projectPages) {
  expect(sitemap.body.includes(`<loc>${expectedCanonicalOrigin}${path}</loc>`), `/sitemap.xml: missing ${path}`)
}
for (const path of privatePages) {
  expect(!sitemap.body.includes(`<loc>${expectedCanonicalOrigin}${path}</loc>`), `/sitemap.xml: private route ${path} must not be listed`)
}

const robots = await request('/robots.txt')
expect(robots.response.status === 200, `/robots.txt: expected HTTP 200, received ${robots.response.status}`)
expect(robots.body.includes(`Sitemap: ${expectedCanonicalOrigin}/sitemap.xml`), '/robots.txt: sitemap declaration is missing')
expect(!robots.body.match(/^Disallow:\s*\/(?:about|how-to|terms|privacy|data-sources|projects)/m), '/robots.txt: a public page is disallowed')
for (const path of publicRenderResources) {
  expect(robots.body.includes(`Allow: ${path}$`), `/robots.txt: render dependency ${path} is not explicitly allowed`)
  const resource = await request(path)
  expect(resource.response.status < 400, `${path}: render dependency returned HTTP ${resource.response.status}`)
  expect(resource.response.headers.get('x-robots-tag')?.includes('noindex'), `${path}: API resource must return X-Robots-Tag: noindex`)
}

const guide = await request('/guide', { redirect: 'manual' })
expect([301, 308].includes(guide.response.status), `/guide: expected a permanent redirect, received ${guide.response.status}`)
const guideLocation = guide.response.headers.get('location')
const guideUrl = guideLocation ? new URL(guideLocation, origin) : null
expect(guideUrl?.pathname === '/' && guideUrl.searchParams.get('guide') === '1', `/guide: expected redirect to /?guide=1`)

const root = await request('/')
const csp = root.response.headers.get('content-security-policy') ?? ''
expect(csp.includes("default-src 'self'"), '/: Content-Security-Policy is missing or incomplete')
expect(csp.includes("frame-ancestors 'none'"), '/: CSP frame protection is missing')
expect(root.response.headers.get('x-frame-options') === 'DENY', '/: X-Frame-Options must be DENY')
expect(root.response.headers.get('referrer-policy') === 'strict-origin-when-cross-origin', '/: Referrer-Policy is missing')
expect(root.response.headers.has('permissions-policy'), '/: Permissions-Policy is missing')

if (failures.length > 0) {
  console.error(`SEO verification failed for ${origin.origin}:`)
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exit(1)
}

console.log(`SEO verification passed for ${origin.origin}.`)
console.log(`Checked ${publicPages.length + projectPages.length} indexable pages, ${privatePages.length} noindex routes, sitemap, robots.txt, redirects, security headers, and the branded 404 response.`)
