import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { pathToFileURL, fileURLToPath } from 'node:url'

const SITE_ORIGIN = 'https://clueterra.com'
const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const frontendDirectory = resolve(scriptDirectory, '..')
const distDirectory = resolve(frontendDirectory, 'dist')
const serverEntry = resolve(frontendDirectory, 'dist-ssr', 'entry-server.js')
const baseHtml = await readFile(resolve(distDirectory, 'index.html'), 'utf8')
const clientManifest = JSON.parse(await readFile(resolve(distDirectory, '.vite', 'manifest.json'), 'utf8'))
const {
  render,
  getTranslation,
  localizedPaths: LOCALIZED_PATHS,
  prefixedLanguages: PREFIXED_LANGUAGES,
} = await import(pathToFileURL(serverEntry).href)

const publicPages = [
  {
    path: '/',
    image: '/images/share/clueterra.jpg',
    title: 'Clueterra: Daily geography game with real-world clues',
    description: 'Play a daily geography game with real-world clues, XP, streaks, and beautiful result screens that make you want tomorrow\'s run.',
    keywords: 'daily geography game, geography quiz, guess the country, country guessing game, clue game, world geography challenge',
    heading: 'Clueterra — Daily Geography Game',
    type: 'WebApplication',
    module: 'src/pages/game/home/HomePage.tsx',
  },
  {
    path: '/games',
    title: 'Free Geography Games Online | Clueterra',
    description: 'Play free geography games online: name countries from their shape, guess world flags, find rivers by their course and solve a daily country clue game.',
    keywords: 'geography games, flag quiz, country shapes quiz, rivers quiz, guess the country, daily geography games',
    image: '/images/share/games.jpg',
    heading: 'Geography games for curious minds.',
    module: 'src/pages/games/GamesPage.tsx',
  },
  {
    path: '/games/rivers',
    title: 'Guess the River – Free Rivers of the World Quiz | Clueterra',
    description: 'Name rivers of the world from their course on a 3D relief globe. A free river quiz with 400+ rivers from 145 countries, three clues and no time limit.',
    keywords: 'guess the river, guess the river game, rivers of the world quiz, world rivers quiz, river quiz, river map quiz, river geography game',
    heading: 'Guess the river.',
    type: 'WebApplication',
    applicationName: 'Clueterra Rivers',
    genre: 'River geography',
    image: '/images/share/rivers.jpg',
    breadcrumbs: [
      { name: 'Home', path: '/' },
      { name: 'Games', path: '/games' },
      { name: 'Rivers', path: '/games/rivers' },
    ],
    module: 'src/pages/games/RiversGamePage.tsx',
  },
  {
    path: '/games/rivers/how-to',
    title: 'How to play the river quiz | Clueterra',
    description: 'A free rivers of the world quiz with 400+ real rivers from 145 countries, from the Nile and the Amazon to rivers only locals know.',
    keywords: 'how to play river quiz, guess the river rules, rivers of the world quiz guide',
    heading: 'How to play the river quiz',
    image: '/images/share/rivers.jpg',
    breadcrumbs: [
      { name: 'Home', path: '/' },
      { name: 'Games', path: '/games' },
      { name: 'Rivers', path: '/games/rivers' },
      { name: 'How to play', path: '/games/rivers/how-to' },
    ],
    module: 'src/pages/games/RiversHowToPage.tsx',
  },
  {
    path: '/games/country-silhouettes',
    title: 'Guess the Country by Shape – Silhouette Quiz | Clueterra',
    description: 'Name all 197 countries of the world from their outline, microstates included. Every country you guess is painted on your own 3D globe in its flag colours.',
    keywords: 'guess the country by shape, country silhouette quiz, country outline game, country shapes quiz, guess the country outline, world map quiz',
    heading: 'Country silhouettes',
    type: 'WebApplication',
    applicationName: 'Clueterra Country Silhouettes',
    genre: 'Country geography',
    image: '/images/share/silhouettes.jpg',
    breadcrumbs: [
      { name: 'Home', path: '/' },
      { name: 'Games', path: '/games' },
      { name: 'Country silhouettes', path: '/games/country-silhouettes' },
    ],
    module: 'src/pages/games/CountrySilhouettesPage.tsx',
  },
  {
    path: '/games/country-silhouettes/how-to',
    title: 'How to play Country silhouettes | Clueterra',
    description: 'All 197 countries, from Canada to the Vatican. Every one you name is painted on your globe in the colours of its flag.',
    keywords: 'how to play country silhouette quiz, guess the country by shape rules, country outline quiz guide',
    heading: 'How to play Country silhouettes',
    image: '/images/share/silhouettes.jpg',
    breadcrumbs: [
      { name: 'Home', path: '/' },
      { name: 'Games', path: '/games' },
      { name: 'Country silhouettes', path: '/games/country-silhouettes' },
      { name: 'How to play', path: '/games/country-silhouettes/how-to' },
    ],
    module: 'src/pages/games/CountrySilhouettesHowToPage.tsx',
  },
  {
    path: '/games/country-flags',
    title: 'Guess the Flag – Free World Flags Quiz | Clueterra',
    description: 'Name the flags of all 197 countries, microstates included. Every flag you guess is raised on your own 3D plaza of nations.',
    keywords: 'guess the flag, flag quiz, world flags quiz, country flags game, flags of the world, guess the country by flag',
    heading: 'Guess the flag',
    type: 'WebApplication',
    applicationName: 'Clueterra Guess the Flag',
    genre: 'Flags quiz',
    image: '/images/share/flags.jpg',
    breadcrumbs: [
      { name: 'Home', path: '/' },
      { name: 'Games', path: '/games' },
      { name: 'Guess the flag', path: '/games/country-flags' },
    ],
    module: 'src/pages/games/CountryFlagsPage.tsx',
  },
  {
    path: '/games/country-flags/how-to',
    title: 'How to play Guess the flag | Clueterra',
    description: 'All 197 countries, from Canada to the Vatican. Every flag you name is raised on your plaza of nations and stays there.',
    keywords: 'how to play flag quiz, guess the flag rules, world flags quiz guide',
    heading: 'How to play Guess the flag',
    image: '/images/share/flags.jpg',
    breadcrumbs: [
      { name: 'Home', path: '/' },
      { name: 'Games', path: '/games' },
      { name: 'Guess the flag', path: '/games/country-flags' },
      { name: 'How to play', path: '/games/country-flags/how-to' },
    ],
    module: 'src/pages/games/CountryFlagsHowToPage.tsx',
  },
  {
    path: '/games/countries-quiz',
    title: 'Around the World Quiz – Free Geography Quiz Game | Clueterra',
    description: 'Travel through all 197 countries: answer two questions in each about capitals, flags, currencies and neighbours, then drive across the border or fly over the ocean to the next one.',
    keywords: 'countries of the world quiz, geography quiz, world quiz, capitals quiz, country quiz game, world tour geography game',
    heading: 'Around the World Quiz',
    type: 'WebApplication',
    applicationName: 'Clueterra Around the World Quiz',
    genre: 'Geography quiz',
    image: '/images/share/countries-quiz.jpg',
    breadcrumbs: [
      { name: 'Home', path: '/' },
      { name: 'Games', path: '/games' },
      { name: 'Around the World Quiz', path: '/games/countries-quiz' },
    ],
    module: 'src/pages/games/CountriesQuizPage.tsx',
  },
  {
    path: '/games/countries-quiz/how-to',
    title: 'How to play Around the World Quiz | Clueterra',
    description: 'Answer about capitals, flags, money and mountains, then drive to a neighbour or fly across the ocean. Every country you explore is painted on your globe.',
    keywords: 'how to play world quiz, geography quiz rules, countries of the world quiz guide',
    heading: 'How to play Around the World Quiz',
    image: '/images/share/countries-quiz.jpg',
    breadcrumbs: [
      { name: 'Home', path: '/' },
      { name: 'Games', path: '/games' },
      { name: 'Around the World Quiz', path: '/games/countries-quiz' },
      { name: 'How to play', path: '/games/countries-quiz/how-to' },
    ],
    module: 'src/pages/games/CountriesQuizHowToPage.tsx',
  },
  {
    path: '/about',
    image: '/images/share/clueterra.jpg',
    title: 'About Clueterra | Geography Games Built on Real Data',
    description: 'Learn how Clueterra turns real-world clues, flags, rivers and documented megaproject costs into daily and quick geography games.',
    keywords: 'about Clueterra, geography games, country guessing game, BudgetGuessr, megaproject cost game',
    heading: 'Geography games built around the real world',
    module: 'src/pages/public/AboutPage.tsx',
  },
  {
    path: '/how-to',
    image: '/images/share/clueterra.jpg',
    title: 'How to Play Clueterra | Geography Game Guide',
    description: 'Learn how to reveal clues, guess the mystery country, earn XP, protect streaks, and play Clueterra’s daily geography challenge.',
    keywords: 'how to play Clueterra, geography game guide, country guessing game rules',
    heading: 'How to play Clueterra',
    module: 'src/pages/public/HowToPage.tsx',
  },
  {
    path: '/budget',
    image: '/images/share/budgetguessr.jpg',
    title: 'BudgetGuessr — Guess real megaproject costs',
    description: 'Guess source-backed costs of real bridges, railways, stadiums, skyscrapers, airports, dams, energy and space megaprojects.',
    keywords: 'megaproject cost guessing game, infrastructure costs, budget quiz',
    heading: 'BudgetGuessr',
    type: 'WebApplication',
    applicationName: 'BudgetGuessr',
    genre: 'Infrastructure trivia',
    module: 'src/pages/budget/BudgetGamePage.tsx',
  },
  {
    path: '/budget/how-to',
    image: '/images/share/budgetguessr.jpg',
    title: 'How to Play BudgetGuessr | Megaproject Cost Game Guide',
    description: 'See how BudgetGuessr works: study a real megaproject, use a hint, guess what it cost and check the documented answer. Try every step here.',
    keywords: 'how to play BudgetGuessr, megaproject cost game, guess the cost game, budget guessing game rules',
    heading: 'How to play BudgetGuessr',
    module: 'src/pages/public/BudgetHowToPage.tsx',
  },
  {
    path: '/terms',
    title: 'Terms of Service | Clueterra',
    description: 'Read the terms that govern access to and use of the Clueterra geography game and related services.',
    keywords: 'Clueterra terms of service',
    heading: 'Terms of Service',
    module: 'src/pages/legal/TermsPage.tsx',
  },
  {
    path: '/privacy',
    title: 'Privacy Policy | Clueterra',
    description: 'Learn what personal data Clueterra processes, why it is used, how long it is retained, and what privacy rights users have.',
    keywords: 'Clueterra privacy policy, Clueterra personal data',
    heading: 'Privacy Policy',
    module: 'src/pages/legal/PrivacyPage.tsx',
  },
  {
    path: '/data-sources',
    title: 'Data Sources and Attributions | Clueterra',
    description: 'Review the public registry of data providers, source links, attribution notices, and license information used by Clueterra.',
    keywords: 'Clueterra data sources, source attribution, data licenses',
    heading: 'Data Sources and Attributions',
    module: 'src/pages/legal/DataSourcesPage.tsx',
  },
]

const DEFAULT_LANGUAGE = 'en'
const X_DEFAULT = 'x-default'

function guideSeo(game) {
  return {
    title: `${game.guide.howTitle} | Clueterra`,
    description: game.leadDetail,
    heading: game.guide.howTitle,
  }
}

const LOCALIZED_SEO = {
  '/games': (t) => ({ title: t.games.seoTitle, description: t.games.seoDescription, heading: `${t.games.titleLead}${t.games.titleAccent}` }),
  '/games/rivers': (t) => ({ title: t.riverGame.seoTitle, description: t.riverGame.seoDescription, heading: t.riverGame.title }),
  '/games/rivers/how-to': (t) => guideSeo(t.riverGame),
  '/games/country-silhouettes': (t) => ({ title: t.silhouetteGame.seoTitle, description: t.silhouetteGame.seoDescription, heading: t.silhouetteGame.title }),
  '/games/country-silhouettes/how-to': (t) => guideSeo(t.silhouetteGame),
  '/games/country-flags': (t) => ({ title: t.flagGame.seoTitle, description: t.flagGame.seoDescription, heading: t.flagGame.title }),
  '/games/country-flags/how-to': (t) => guideSeo(t.flagGame),
  '/games/countries-quiz': (t) => ({ title: t.quizGame.seoTitle, description: t.quizGame.seoDescription, heading: t.quizGame.title }),
  '/games/countries-quiz/how-to': (t) => guideSeo(t.quizGame),
}

const CRUMB_NAMES = {
  '/': (t) => t.nav.home,
  '/games': (t) => t.nav.games,
  '/games/rivers': (t) => t.riverGame.title,
  '/games/country-silhouettes': (t) => t.silhouetteGame.title,
  '/games/country-flags': (t) => t.flagGame.title,
  '/games/countries-quiz': (t) => t.quizGame.title,
  '/games/rivers/how-to': (t) => t.riverGame.howToPlay,
  '/games/country-silhouettes/how-to': (t) => t.silhouetteGame.howToPlay,
  '/games/country-flags/how-to': (t) => t.flagGame.howToPlay,
  '/games/countries-quiz/how-to': (t) => t.quizGame.howToPlay,
}

function prefixedPath(path, language) {
  return language === DEFAULT_LANGUAGE || !LOCALIZED_PATHS.includes(path) ? path : `/${language}${path}`
}

function crumbName(crumb, translation) {
  return CRUMB_NAMES[crumb.path]?.(translation) ?? crumb.name
}

function localizedVariant(page, language) {
  const translation = getTranslation(language)
  return {
    ...page,
    ...LOCALIZED_SEO[page.path](translation),
    path: prefixedPath(page.path, language),
    basePath: page.path,
    language,
    breadcrumbs: page.breadcrumbs?.map((crumb) => ({ name: crumbName(crumb, translation), path: prefixedPath(crumb.path, language) })),
  }
}

function alternateLinks(basePath) {
  const variants = [
    ...[DEFAULT_LANGUAGE, ...PREFIXED_LANGUAGES].map((language) => [language, prefixedPath(basePath, language)]),
    [X_DEFAULT, basePath],
  ]
  return variants
    .map(([hreflang, path]) => `<link rel="alternate" hreflang="${hreflang}" href="${SITE_ORIGIN}${path}" />`)
    .join('\n    ')
}

function applyLocale(html, page) {
  const basePath = page.basePath ?? page.path
  if (!LOCALIZED_PATHS.includes(basePath)) return html
  const language = page.language ?? DEFAULT_LANGUAGE
  return replaceRequired(html, /<html lang="[^"]*">/, `<html lang="${language}">`, 'document language')
    .replace('</head>', `    ${alternateLinks(basePath)}\n  </head>`)
}

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function replaceRequired(html, pattern, replacement, label) {
  if (!pattern.test(html)) {
    throw new Error(`Cannot generate page: ${label} was not found in dist/index.html`)
  }
  return html.replace(pattern, replacement)
}

function replaceRoot(html, markup, renderMode) {
  return replaceRequired(
    html,
    /<div id="root" data-render-mode="csr"><\/div>/,
    `<div id="root" data-render-mode="${renderMode}">${markup}</div>`,
    'application root',
  )
}

function preloadPageModule(html, moduleId) {
  const entry = clientManifest[moduleId]
  if (!entry?.file) {
    throw new Error(`Cannot generate page: client manifest entry ${moduleId} was not found`)
  }

  const styles = new Set()
  const visited = new Set()
  function collectStyles(id) {
    if (visited.has(id)) return
    visited.add(id)
    const chunk = clientManifest[id]
    if (!chunk) return
    for (const dependency of chunk.imports ?? []) collectStyles(dependency)
    for (const stylesheet of chunk.css ?? []) styles.add(stylesheet)
  }
  collectStyles(moduleId)
  const stylesheets = [...styles]
    .filter(file => !html.includes(`href="/${file}"`))
    .map(file => `    <link rel="stylesheet" crossorigin href="/${file}">`)
  return html.replace('</head>', [
    ...stylesheets,
    `    <link rel="modulepreload" crossorigin href="/${entry.file}">`,
    '  </head>',
  ].join('\n'))
}

function appBootShell() {
  return `<div data-app-boot role="status" aria-label="Loading Clueterra">
      <span data-app-boot-mark aria-hidden="true">C</span>
      <span data-app-boot-name>Clueterra</span>
      <span data-app-boot-spinner aria-hidden="true"></span>
    </div>`
}

function appBootStyles() {
  return `<style id="app-boot-style">
    body{margin:0;background:#08080c;color:#f4f4f5}
    [data-app-boot]{box-sizing:border-box;min-height:100vh;display:flex;align-items:center;justify-content:center;gap:.75rem;font-family:Inter,ui-sans-serif,system-ui,sans-serif;opacity:0;animation:app-boot-show .12s ease .18s forwards}
    [data-app-boot-mark]{display:grid;width:2rem;height:2rem;place-items:center;border:1px solid rgba(110,231,183,.25);border-radius:.75rem;background:rgba(52,211,153,.09);color:#6ee7b7;font-size:.8rem;font-weight:900}
    [data-app-boot-name]{font-size:.9rem;font-weight:750;letter-spacing:.02em;color:rgba(255,255,255,.78)}
    [data-app-boot-spinner]{width:.9rem;height:.9rem;margin-left:.25rem;border:2px solid rgba(255,255,255,.12);border-top-color:rgba(255,255,255,.55);border-radius:999px;animation:app-boot-spin .7s linear infinite}
    @keyframes app-boot-show{to{opacity:1}}
    @keyframes app-boot-spin{to{transform:rotate(360deg)}}
    @media(prefers-reduced-motion:reduce){[data-app-boot]{animation:none;opacity:1}[data-app-boot-spinner]{animation-duration:1.4s}}
  </style>`
}

function structuredDataFor(page, canonicalUrl) {
  const breadcrumb = page.breadcrumbs && {
    '@type': 'BreadcrumbList',
    itemListElement: page.breadcrumbs.map(({ name, path }, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      name,
      item: `${SITE_ORIGIN}${path}`,
    })),
  }
  if (page.type === 'WebApplication') {
    const application = {
      '@context': 'https://schema.org',
      '@type': 'WebApplication',
      name: page.applicationName ?? 'Clueterra',
      url: canonicalUrl,
      description: page.description,
      applicationCategory: 'GameApplication',
      operatingSystem: 'Web',
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
      genre: page.genre ?? 'Geography',
      inLanguage: page.language ?? DEFAULT_LANGUAGE,
    }
    return breadcrumb ? { '@context': 'https://schema.org', '@graph': [application, breadcrumb] } : application
  }

  const webPage = {
    '@context': 'https://schema.org',
    '@type': 'WebPage',
    name: page.heading,
    url: canonicalUrl,
    description: page.description,
    inLanguage: page.language ?? DEFAULT_LANGUAGE,
    isPartOf: {
      '@type': 'WebSite',
      name: 'Clueterra',
      url: `${SITE_ORIGIN}/`,
    },
  }
  return breadcrumb ? { '@context': 'https://schema.org', '@graph': [webPage, breadcrumb] } : webPage
}

function applyMetadata(html, page, { canonicalPath, robots }) {
  const canonicalUrl = canonicalPath === null
    ? null
    : `${SITE_ORIGIN}${canonicalPath === '/' ? '/' : canonicalPath}`
  const safeTitle = escapeHtml(page.title)
  const safeDescription = escapeHtml(page.description)
  const safeKeywords = escapeHtml(page.keywords)

  html = replaceRequired(html, /<title>.*?<\/title>/s, `<title>${safeTitle}</title>`, 'title')
  html = replaceRequired(html, /<meta name="description" content=".*?"\s*\/>/s, `<meta name="description" content="${safeDescription}" />`, 'description')
  html = replaceRequired(html, /<meta name="keywords" content=".*?"\s*\/>/s, `<meta name="keywords" content="${safeKeywords}" />`, 'keywords')
  html = replaceRequired(html, /<meta name="robots" content=".*?"\s*\/>/s, `<meta name="robots" content="${robots}" />`, 'robots')
  html = replaceRequired(html, /<meta property="og:title" content=".*?"\s*\/>/s, `<meta property="og:title" content="${safeTitle}" />`, 'Open Graph title')
  html = replaceRequired(html, /<meta property="og:description" content=".*?"\s*\/>/s, `<meta property="og:description" content="${safeDescription}" />`, 'Open Graph description')
  html = replaceRequired(html, /<meta name="twitter:title" content=".*?"\s*\/>/s, `<meta name="twitter:title" content="${safeTitle}" />`, 'Twitter title')
  html = replaceRequired(html, /<meta name="twitter:description" content=".*?"\s*\/>/s, `<meta name="twitter:description" content="${safeDescription}" />`, 'Twitter description')

  if (page.image) {
    const imageUrl = escapeHtml(`${SITE_ORIGIN}${page.image}`)
    html = html.replace('</head>', `<meta property="og:image" content="${imageUrl}" />\n  <meta name="twitter:image" content="${imageUrl}" />\n  </head>`)
  }

  if (canonicalUrl) {
    html = replaceRequired(html, /<link rel="canonical" href=".*?"\s*\/>/s, `<link rel="canonical" href="${canonicalUrl}" />`, 'canonical URL')
    html = replaceRequired(html, /<meta property="og:url" content=".*?"\s*\/>/s, `<meta property="og:url" content="${canonicalUrl}" />`, 'Open Graph URL')
  } else {
    html = html
      .replace(/\s*<link rel="canonical" href=".*?"\s*\/>/s, '')
      .replace(/\s*<meta property="og:url" content=".*?"\s*\/>/s, '')
  }

  const structuredData = canonicalUrl
    ? structuredDataFor(page, canonicalUrl)
    : {
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        name: page.heading,
        description: page.description,
      }

  return replaceRequired(
    html,
    /<script id="static-structured-data" type="application\/ld\+json">.*?<\/script>/s,
    `<script id="static-structured-data" type="application/ld+json">${JSON.stringify(structuredData)}</script>`,
    'structured data',
  )
}

async function writeHtml(relativePath, html) {
  const destination = resolve(distDirectory, relativePath)
  await mkdir(dirname(destination), { recursive: true })
  await writeFile(destination, html, 'utf8')
}

const localizedPages = publicPages
  .filter((page) => LOCALIZED_PATHS.includes(page.path))
  .flatMap((page) => PREFIXED_LANGUAGES.map((language) => localizedVariant(page, language)))

for (const page of [...publicPages, ...localizedPages]) {
  const markup = await render(page.path)
  const html = preloadPageModule(replaceRoot(
    applyLocale(applyMetadata(baseHtml, page, { canonicalPath: page.path, robots: 'index,follow' }), page),
    markup,
    'ssg',
  ), page.module)
  const relativePath = page.path === '/' ? 'index.html' : `${page.path.slice(1)}/index.html`
  await writeHtml(relativePath, html)
}

const applicationPage = {
  title: 'Clueterra',
  description: 'Clueterra application page.',
  keywords: 'Clueterra',
  heading: 'Clueterra',
}
const ssrHtml = applyMetadata(baseHtml, applicationPage, { canonicalPath: '/', robots: 'noindex,nofollow' })
await writeHtml('ssr.html', ssrHtml)

let appHtml = applyMetadata(baseHtml, applicationPage, { canonicalPath: null, robots: 'noindex,nofollow' })
appHtml = replaceRoot(appHtml, appBootShell(), 'csr')
appHtml = appHtml.replace('</head>', `${appBootStyles()}\n  </head>`)
await writeHtml('app.html', appHtml)

const notFoundPage = {
  title: 'Page Not Found | Clueterra',
  description: 'The requested Clueterra page could not be found.',
  keywords: 'Clueterra',
  heading: 'Page Not Found',
}
const notFoundMarkup = await render('/__clueterra_not_found__')
const notFoundHtml = replaceRoot(
  applyMetadata(baseHtml, notFoundPage, { canonicalPath: null, robots: 'noindex,nofollow' }),
  notFoundMarkup,
  'ssg',
)
await writeHtml('404.html', preloadPageModule(notFoundHtml, 'src/pages/NotFoundPage.tsx'))

console.log(`Pre-rendered ${publicPages.length} public React pages, ${localizedPages.length} localized pages, SSR shell, app.html, and 404.html.`)
