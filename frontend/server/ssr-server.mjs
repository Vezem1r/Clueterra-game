import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const serverDirectory = fileURLToPath(new URL('.', import.meta.url))
const appDirectory = resolve(serverDirectory, '..')
const template = await readFile(resolve(appDirectory, 'dist', 'ssr.html'), 'utf8')
const { getProjectDocumentMeta, render } = await import(new URL('../dist-ssr/entry-server.js', import.meta.url))

const port = positiveInteger(process.env.PORT, 3000)
const apiOrigin = withoutTrailingSlash(process.env.API_ORIGIN || 'http://api:8080')
const siteOrigin = withoutTrailingSlash(process.env.SITE_ORIGIN || 'https://clueterra.com')
const NOT_FOUND = 404
const GONE = 410

const server = createServer(async (request, response) => {
  try {
    const requestUrl = new URL(request.url || '/', siteOrigin)

    if (requestUrl.pathname === '/health') {
      send(response, 200, 'text/plain; charset=utf-8', 'OK', 'no-store')
      return
    }

    if (request.method !== 'GET' && request.method !== 'HEAD') {
      response.setHeader('Allow', 'GET, HEAD')
      send(response, 405, 'text/plain; charset=utf-8', 'Method Not Allowed', 'no-store')
      return
    }

    const route = matchProjectRoute(requestUrl.pathname)
    if (!route) {
      send(response, 404, 'text/plain; charset=utf-8', 'Not Found', 'no-store')
      return
    }

    const routeResult = await loadRoute(route, request)
    const markup = await render(`${requestUrl.pathname}${requestUrl.search}`, routeResult.data)
    const html = createHtml(markup, routeResult.data, routeResult.meta)

    response.statusCode = routeResult.status
    response.setHeader('Content-Type', 'text/html; charset=utf-8')
    response.setHeader('Cache-Control', routeResult.cacheControl)
    response.setHeader('Vary', 'Accept-Encoding')
    if (routeResult.status === NOT_FOUND || routeResult.status === GONE) {
      response.setHeader('X-Robots-Tag', 'noindex, nofollow')
    }
    response.end(request.method === 'HEAD' ? undefined : html)
  } catch (error) {
    console.error('Project SSR request failed', error)
    send(
      response,
      503,
      'text/html; charset=utf-8',
      '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="robots" content="noindex,nofollow"><title>Clueterra temporarily unavailable</title></head><body><h1>Temporarily unavailable</h1><p>Please try again shortly.</p></body></html>',
      'no-store',
    )
  }
})

server.listen(port, '0.0.0.0', () => {
  console.log(`Clueterra project SSR listening on ${port}`)
})

let shuttingDown = false
const shutdown = (signal) => {
  if (shuttingDown) return
  shuttingDown = true
  console.log(`Received ${signal}; shutting down Clueterra project SSR`)

  const forceExit = setTimeout(() => {
    console.error('Clueterra project SSR did not stop within 10 seconds')
    process.exit(1)
  }, 10_000)
  forceExit.unref()

  server.close((error) => {
    clearTimeout(forceExit)
    if (error) {
      console.error('Clueterra project SSR shutdown failed', error)
      process.exit(1)
    }
    process.exit(0)
  })
  server.closeIdleConnections?.()
}

process.on('SIGTERM', () => shutdown('SIGTERM'))
process.on('SIGINT', () => shutdown('SIGINT'))

function matchProjectRoute(pathname) {
  if (pathname === '/projects') {
    return { kind: 'directory', page: 1, path: pathname }
  }

  const pageMatch = pathname.match(/^\/projects\/page\/([1-9][0-9]*)$/)
  if (pageMatch) {
    return { kind: 'directory', page: Number(pageMatch[1]), path: pathname }
  }

  if (pathname === '/projects/most-expensive') {
    return { kind: 'hub', apiPath: 'most-expensive', path: pathname }
  }

  const categoryHubMatch = pathname.match(/^\/projects\/most-expensive\/([a-z0-9]+(?:-[a-z0-9]+)*)$/)
  if (categoryHubMatch) {
    return { kind: 'hub', apiPath: `most-expensive/${categoryHubMatch[1]}`, path: pathname }
  }

  const countryHubMatch = pathname.match(/^\/projects\/countries\/([a-z0-9]+(?:-[a-z0-9]+)*)$/)
  if (countryHubMatch) {
    return { kind: 'hub', apiPath: `countries/${countryHubMatch[1]}`, path: pathname }
  }

  const detailMatch = pathname.match(/^\/projects\/([a-z0-9]+(?:-[a-z0-9]+)*)$/)
  if (detailMatch) {
    return { kind: 'detail', slug: detailMatch[1], path: pathname }
  }

  return null
}

async function loadRoute(route, request) {
  const headers = forwardedApiHeaders(request)
  const endpoint = route.kind === 'directory'
    ? `/api/project-pages/data?page=${route.page}`
    : route.kind === 'hub'
      ? `/api/project-pages/hubs/${route.apiPath}`
      : `/api/project-pages/data/${encodeURIComponent(route.slug)}`
  const apiResponse = await fetch(`${apiOrigin}${endpoint}`, {
    headers,
    signal: AbortSignal.timeout(7_000),
  })

  if (apiResponse.status === NOT_FOUND) {
    return notFoundResult(route.path, route.kind === 'detail' ? GONE : NOT_FOUND)
  }
  if (!apiResponse.ok) {
    return errorResult(route.path, 503)
  }

  const body = await apiResponse.json()
  if (route.kind === 'directory') {
    const canonicalPath = route.page === 1 ? '/projects' : `/projects/page/${route.page}`
    const data = { kind: 'directory', path: canonicalPath, directory: body }
    return {
      status: 200,
      cacheControl: 'public, max-age=300, stale-while-revalidate=600',
      data,
      meta: getProjectDocumentMeta(data, siteOrigin),
    }
  }

  if (route.kind === 'hub') {
    const data = { kind: 'hub', path: route.path, hub: body }
    return {
      status: 200,
      cacheControl: 'public, max-age=300, stale-while-revalidate=600',
      data,
      meta: getProjectDocumentMeta(data, siteOrigin),
    }
  }

  const data = { kind: 'detail', path: route.path, detail: body, preview: false }
  return {
    status: 200,
    cacheControl: 'public, max-age=300, stale-while-revalidate=600',
    data,
    meta: getProjectDocumentMeta(data, siteOrigin),
  }
}

function forwardedApiHeaders(request) {
  const headers = { Accept: 'application/json' }
  if (request.headers['x-forwarded-for']) headers['X-Forwarded-For'] = request.headers['x-forwarded-for']
  if (request.headers['cf-connecting-ip']) headers['CF-Connecting-IP'] = request.headers['cf-connecting-ip']
  return headers
}

function notFoundResult(path, status) {
  const data = { kind: 'not-found', path }
  return {
    status,
    cacheControl: 'no-cache, no-store, must-revalidate',
    data,
    meta: getProjectDocumentMeta(data, siteOrigin),
  }
}

function errorResult(path, status) {
  const data = { kind: 'error', path }
  return {
    status,
    cacheControl: 'no-cache, no-store, must-revalidate',
    data,
    meta: getProjectDocumentMeta(data, siteOrigin),
  }
}

function createHtml(markup, data, meta) {
  let html = template
  html = replaceRequired(html, /<div id="root" data-render-mode="csr"><\/div>/, `<div id="root" data-render-mode="ssr">${markup}</div><script id="project-ssr-data" type="application/json">${safeJson(data)}</script>`, 'application root')
  html = replaceRequired(html, /<title>.*?<\/title>/s, `<title>${escapeHtml(meta.title)}</title>`, 'title')
  html = replaceNamedMeta(html, 'description', meta.description)
  html = replaceNamedMeta(html, 'keywords', meta.keywords)
  html = replaceNamedMeta(html, 'robots', meta.robots)
  html = replaceNamedMeta(html, 'twitter:title', meta.title)
  html = replaceNamedMeta(html, 'twitter:description', meta.description)
  html = replacePropertyMeta(html, 'og:title', meta.title)
  html = replacePropertyMeta(html, 'og:description', meta.description)
  html = replacePropertyMeta(html, 'og:type', meta.type)

  if (meta.canonicalPath) {
    const canonicalUrl = `${siteOrigin}${meta.canonicalPath}`
    html = replaceRequired(html, /<link rel="canonical" href=".*?"\s*\/>/s, `<link rel="canonical" href="${escapeHtml(canonicalUrl)}" />`, 'canonical URL')
    html = replacePropertyMeta(html, 'og:url', canonicalUrl)
  } else {
    html = html
      .replace(/\s*<link rel="canonical" href=".*?"\s*\/>/s, '')
      .replace(/\s*<meta property="og:url" content=".*?"\s*\/>/s, '')
  }

  if (meta.image) {
    const absoluteImage = new URL(meta.image, siteOrigin).toString()
    html = insertHeadMeta(html, `<meta property="og:image" content="${escapeHtml(absoluteImage)}" />`)
    html = insertHeadMeta(html, `<meta name="twitter:image" content="${escapeHtml(absoluteImage)}" />`)
    if (meta.imageAlt) {
      html = insertHeadMeta(html, `<meta property="og:image:alt" content="${escapeHtml(meta.imageAlt)}" />`)
    }
  }

  if (meta.previousPath) {
    html = insertHeadMeta(html, `<link rel="prev" href="${siteOrigin}${meta.previousPath}" />`)
  }
  if (meta.nextPath) {
    html = insertHeadMeta(html, `<link rel="next" href="${siteOrigin}${meta.nextPath}" />`)
  }

  if (meta.structuredData) {
    html = replaceRequired(
      html,
      /<script id="static-structured-data" type="application\/ld\+json">.*?<\/script>/s,
      `<script id="static-structured-data" type="application/ld+json">${safeJson(meta.structuredData)}</script>`,
      'structured data',
    )
  } else {
    html = html.replace(/\s*<script id="static-structured-data" type="application\/ld\+json">.*?<\/script>/s, '')
  }

  return html
}

function replaceNamedMeta(html, name, content) {
  return replaceRequired(
    html,
    new RegExp(`<meta name="${escapeRegExp(name)}" content=".*?"\\s*\\/>`, 's'),
    `<meta name="${name}" content="${escapeHtml(content)}" />`,
    `${name} metadata`,
  )
}

function replacePropertyMeta(html, property, content) {
  return replaceRequired(
    html,
    new RegExp(`<meta property="${escapeRegExp(property)}" content=".*?"\\s*\\/>`, 's'),
    `<meta property="${property}" content="${escapeHtml(content)}" />`,
    `${property} metadata`,
  )
}

function insertHeadMeta(html, tag) {
  return html.replace('</head>', `    ${tag}\n  </head>`)
}

function replaceRequired(html, pattern, replacement, label) {
  if (!pattern.test(html)) {
    throw new Error(`Cannot render project page: ${label} was not found in index.html`)
  }
  return html.replace(pattern, replacement)
}

function safeJson(value) {
  return JSON.stringify(value)
    .replaceAll('<', '\\u003c')
    .replaceAll('>', '\\u003e')
    .replaceAll('&', '\\u0026')
}

function escapeHtml(value) {
  return String(value || '')
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function withoutTrailingSlash(value) {
  return value.endsWith('/') ? value.slice(0, -1) : value
}

function positiveInteger(value, fallback) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function send(response, status, contentType, body, cacheControl) {
  response.statusCode = status
  response.setHeader('Content-Type', contentType)
  response.setHeader('Cache-Control', cacheControl)
  response.end(body)
}
