import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { createReadStream, createWriteStream } from 'node:fs'
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pipeline } from 'node:stream/promises'
import { pathToFileURL } from 'node:url'

const RASTER_URL = 'https://naturalearth.s3.amazonaws.com/10m_raster/NE1_LR_LC_SR.zip'
const RASTER_ARCHIVE_SHA256 = '5a1e36c9944fd2866e812f7756a6c96d59afa7bc176360b1689dc232aecc169b'
const RASTER_TIFF_SHA256 = '1f8472f0158c107b61fa6ca91fe83adfbf3e1598f9ac34ea299b96e952457bee'
const RENDER_VERSION = 'natural-earth-relief-lumen-v2'
const WIDTH = 900
const HEIGHT = 600

function lines(geometry) {
  if (geometry?.type === 'LineString') return [geometry.coordinates]
  if (geometry?.type === 'MultiLineString') return geometry.coordinates
  return []
}

function polygons(geometry) {
  if (geometry?.type === 'Polygon') return [geometry.coordinates]
  if (geometry?.type === 'MultiPolygon') return geometry.coordinates
  return []
}

function bounds(points) {
  return points.reduce((box, [x, y]) => ({
    minX: Math.min(box.minX, x), minY: Math.min(box.minY, y),
    maxX: Math.max(box.maxX, x), maxY: Math.max(box.maxY, y),
  }), { minX: 180, minY: 90, maxX: -180, maxY: -90 })
}

function intersects(a, b) {
  return a.minX <= b.maxX && a.maxX >= b.minX && a.minY <= b.maxY && a.maxY >= b.minY
}

function clipRing(ring, box) {
  const boundaries = [
    { inside: point => point[0] >= box.minX, intersection: (a, b) => [box.minX, a[1] + (b[1] - a[1]) * (box.minX - a[0]) / (b[0] - a[0])] },
    { inside: point => point[0] <= box.maxX, intersection: (a, b) => [box.maxX, a[1] + (b[1] - a[1]) * (box.maxX - a[0]) / (b[0] - a[0])] },
    { inside: point => point[1] >= box.minY, intersection: (a, b) => [a[0] + (b[0] - a[0]) * (box.minY - a[1]) / (b[1] - a[1]), box.minY] },
    { inside: point => point[1] <= box.maxY, intersection: (a, b) => [a[0] + (b[0] - a[0]) * (box.maxY - a[1]) / (b[1] - a[1]), box.maxY] },
  ]
  return boundaries.reduce((points, boundary) => {
    if (!points.length) return points
    const clipped = []
    let previous = points.at(-1)
    for (const current of points) {
      const wasInside = boundary.inside(previous)
      const isInside = boundary.inside(current)
      if (wasInside !== isInside) clipped.push(boundary.intersection(previous, current))
      if (isInside) clipped.push(current)
      previous = current
    }
    return clipped
  }, ring)
}

function projector(box) {
  const centerLat = (box.minY + box.maxY) / 2
  const cos = Math.max(0.25, Math.cos(centerLat * Math.PI / 180))
  const width = Math.max((box.maxX - box.minX) * cos, 0.45)
  const height = Math.max(box.maxY - box.minY, 0.3)
  const scale = Math.min(690 / width, 410 / height)
  const centerLon = (box.minX + box.maxX) / 2
  return {
    point: ([lon, lat]) => [450 + (lon - centerLon) * cos * scale, 300 - (lat - centerLat) * scale],
    viewport: { minX: centerLon - 450 / (cos * scale), maxX: centerLon + 450 / (cos * scale),
      minY: centerLat - 300 / scale, maxY: centerLat + 300 / scale },
  }
}

function path(line, project, maxPoints = 1200) {
  const step = Math.max(1, Math.ceil(line.length / maxPoints))
  return line.filter((_, index) => index % step === 0 || index === line.length - 1)
    .map((point, index) => {
      const [x, y] = project(point)
      return `${index ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`
    }).join(' ')
}

function indexedLines(features) {
  return features.flatMap(feature => lines(feature.geometry).map(line => ({
    line, qid: feature.properties?.wikidataid,
    box: bounds(line), rank: Number(feature.properties?.scalerank),
  })))
}

function visiblePaths(index, viewport, project, maxPoints) {
  return index.filter(item => intersects(viewport, item.box))
    .map(item => path(item.line, project, maxPoints)).join(' ')
}

function baseSvg(terrain, coast, land) {
  const grid = Array.from({ length: 10 }, (_, index) => `<path d="M${index * 100} 0V600"/>`).join('')
    + Array.from({ length: 7 }, (_, index) => `<path d="M0 ${index * 100}H900"/>`).join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 600" width="900" height="600">
<defs>
  <linearGradient id="sea" x2="0" y2="1"><stop stop-color="#0b2638"/><stop offset="1" stop-color="#061522"/></linearGradient>
  <radialGradient id="vignette"><stop offset=".45" stop-color="#071522" stop-opacity="0"/><stop offset="1" stop-color="#020b17" stop-opacity=".62"/></radialGradient>
  <filter id="terrainTone"><feColorMatrix type="saturate" values="1.22"/><feComponentTransfer><feFuncR type="gamma" amplitude=".64" exponent="1.09" offset="0"/><feFuncG type="gamma" amplitude=".72" exponent="1.06" offset="0"/><feFuncB type="gamma" amplitude=".79" exponent="1.02" offset="0"/></feComponentTransfer></filter>
  <clipPath id="viewport"><rect width="900" height="600"/></clipPath>
  <clipPath id="land">${land}</clipPath>
</defs>
<g clip-path="url(#viewport)">
  <rect width="900" height="600" fill="url(#sea)"/>
  <g fill="none" stroke="#8dd7e4" stroke-width=".7" opacity=".055">${grid}</g>
  <g clip-path="url(#land)">
    <image width="900" height="600" href="data:image/jpeg;base64,${terrain}" filter="url(#terrainTone)"/>
    <rect width="900" height="600" fill="#08222c" opacity=".16"/>
  </g>
  <path d="${coast}" fill="none" stroke="#8de1e3" stroke-opacity=".31" stroke-width="1.25"/>
  <rect width="900" height="600" fill="url(#vignette)"/>
</g></svg>`
}

function finalSvg(base, river, tributaries, borders, withBorders) {
  const stroke = (color, width, extra = '') => `<use href="#selectedRiver" fill="none" stroke="${color}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round" ${extra}/>`
  const luminousRiver = stroke('#20eadc', 22, 'opacity=".58" filter="url(#glowWide)"')
    + stroke('#30d8d7', 8, 'opacity=".30"')
    + stroke('#124455', 5.2, 'opacity=".46"')
    + stroke('#a9fff2', 3.35)
    + stroke('#efffff', .55, 'opacity=".58"')
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 600" width="900" height="600" role="img" aria-label="River course on real shaded relief">
<defs><filter id="glowWide" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="12"/></filter><clipPath id="viewport"><rect width="900" height="600"/></clipPath><path id="selectedRiver" d="${river}"/></defs>
<g clip-path="url(#viewport)">
  <image width="900" height="600" href="data:image/jpeg;base64,${base}"/>
  <path d="${tributaries}" fill="none" stroke="#b1ece8" stroke-opacity=".23" stroke-width=".9" stroke-linecap="round"/>
  ${withBorders ? `<path d="${borders}" fill="none" stroke="#d5e3e1" stroke-opacity=".48" stroke-width="1.05" stroke-dasharray="4 4"/>` : ''}
  ${luminousRiver}
  ${withBorders ? `<g><rect x="22" y="22" width="158" height="31" rx="10" fill="#061c27" fill-opacity=".82" stroke="#b7ece8" stroke-opacity=".4"/><text x="36" y="43" font-family="Arial, Helvetica, sans-serif" font-size="12" font-weight="700" letter-spacing="1" fill="#e3fffb">COUNTRY BORDERS</text></g>` : ''}
  <text x="872" y="569" text-anchor="end" font-family="Arial, Helvetica, sans-serif" font-size="16" font-weight="700" letter-spacing=".5" fill="#e3fffb" fill-opacity=".72" stroke="#041722" stroke-opacity=".78" stroke-width="3" stroke-linejoin="round" paint-order="stroke fill">clueterra.com</text>
</g></svg>\n`
}

async function runJava(args) {
  await new Promise((done, reject) => {
    const child = spawn('java', [resolve('scripts/river-map/RenderTerrainCrop.java'), ...args.map(String)],
      { stdio: ['ignore', 'ignore', 'pipe'] })
    let error = ''
    child.stderr.on('data', chunk => { error += chunk.toString() })
    child.on('error', reject)
    child.on('close', code => code === 0 ? done() : reject(new Error(error || `Java exited ${code}`)))
  })
}

async function hashFile(file) {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(file)) hash.update(chunk)
  return hash.digest('hex')
}

async function ensureRaster(cacheDir) {
  const raster = resolve(cacheDir, 'NE1_LR_LC_SR.tif')
  if ((await stat(raster).catch(() => null))?.size > 390_000_000
    && await hashFile(raster) === RASTER_TIFF_SHA256) return raster
  const archive = resolve(cacheDir, 'NE1_LR_LC_SR.zip')
  if (!(await stat(archive).catch(() => null))?.size) {
    const response = await fetch(RASTER_URL, { headers: { 'User-Agent': 'ClueterraRiverCatalog/1.0 (https://clueterra.com)' } })
    if (!response.ok || !response.body) throw new Error(`Natural Earth raster download: HTTP ${response.status}`)
    await mkdir(cacheDir, { recursive: true })
    const temporary = `${archive}.part`
    await pipeline(response.body, createWriteStream(temporary))
    if (await hashFile(temporary) !== RASTER_ARCHIVE_SHA256) {
      throw new Error('Natural Earth raster download does not match the pinned archive checksum')
    }
    await (await import('node:fs/promises')).rename(temporary, archive)
  }
  if (await hashFile(archive) !== RASTER_ARCHIVE_SHA256) {
    throw new Error('Cached Natural Earth raster archive does not match the pinned checksum')
  }
  await runJava(['--extract', archive, raster])
  if (await hashFile(raster) !== RASTER_TIFF_SHA256) {
    throw new Error('Extracted Natural Earth raster does not match the pinned checksum')
  }
  return raster
}

export async function createTerrainRenderer({ riverFeatures, countryFeatures, getJson, cacheDir }) {
  const [landData, coastData, raster] = await Promise.all([
    getJson('https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/ne_10m_land.geojson'),
    getJson('https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/ne_10m_coastline.geojson'),
    ensureRaster(cacheDir),
  ])
  const browserModule = resolve('frontend/node_modules/playwright/index.mjs')
  if (!(await stat(browserModule).catch(() => null))) {
    throw new Error('River map build needs Playwright: install frontend dependencies with npm ci first')
  }
  const { chromium } = await import(pathToFileURL(browserModule).href)
  const browser = await chromium.launch({ headless: true })
  const riverIndex = indexedLines(riverFeatures).filter(item => item.rank <= 6)
  const coastIndex = indexedLines(coastData.features)
  const landIndex = landData.features.flatMap(feature => polygons(feature.geometry)).map(polygon => ({
    rings: polygon, box: bounds(polygon[0]),
  }))
  const countryIndex = countryFeatures.flatMap(feature => polygons(feature.geometry))
    .flatMap(polygon => polygon.map(ring => ({ line: ring, box: bounds(ring) })))
  const rendered = new Map()
  let count = 0

  async function renderPair(river) {
    if (rendered.has(river.qid)) return rendered.get(river.qid)
    const box = bounds(river.lines.flat())
    const { point, viewport } = projector(box)
    const geometryHash = createHash('sha256').update(JSON.stringify(river.lines)).digest('hex').slice(0, 12)
    const key = `${RENDER_VERSION}-${river.qid}-${geometryHash}`
    const renderDir = resolve(cacheDir, 'renders')
    await mkdir(renderDir, { recursive: true })
    const baseJpegPath = resolve(renderDir, `${key}-base.jpg`)
    let base = (await stat(baseJpegPath).catch(() => null))?.size > 1000
      ? (await readFile(baseJpegPath)).toString('base64') : null
    if (!base) {
      const terrainPath = resolve(renderDir, `${key}-terrain.jpg`)
      await runJava([raster, terrainPath, viewport.minX, viewport.maxX, viewport.minY, viewport.maxY])
      const terrain = (await readFile(terrainPath)).toString('base64')
      const coast = visiblePaths(coastIndex, viewport, point, 1200)
      const land = landIndex.filter(item => intersects(viewport, item.box))
        .map(item => item.rings.map(ring => clipRing(ring, viewport)).filter(ring => ring.length >= 3))
        .filter(rings => rings.length)
        .map(rings => `<path fill-rule="evenodd" d="${rings.map(ring => `${path(ring, point, 20000)} Z`).join(' ')}"/>`).join('')
      const basePath = resolve(renderDir, `${key}-base.svg`)
      await writeFile(basePath, baseSvg(terrain, coast, land))
      const page = await browser.newPage({ viewport: { width: WIDTH, height: HEIGHT }, deviceScaleFactor: 1 })
      try {
        await page.goto(pathToFileURL(basePath).href)
        await page.screenshot({ path: baseJpegPath, type: 'jpeg', quality: 88 })
      } finally {
        await page.close()
      }
      base = (await readFile(baseJpegPath)).toString('base64')
    }
    const riverPath = river.lines.map(line => path(line, point)).join(' ')
    const tributaries = visiblePaths(riverIndex.filter(item => item.qid !== river.qid), viewport, point, 600)
    const borders = visiblePaths(countryIndex, viewport, point, 850)
    const pair = {
      plain: finalSvg(base, riverPath, tributaries, borders, false),
      borders: finalSvg(base, riverPath, tributaries, borders, true),
    }
    rendered.set(river.qid, pair)
    count++
    if (count % 20 === 0) console.log(`Rendered ${count} unique terrain river maps...`)
    return pair
  }

  return { renderPair, close: () => browser.close() }
}
