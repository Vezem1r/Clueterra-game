#!/usr/bin/env node

import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const CATALOG = resolve('backend/clueterra-questions/src/main/resources/assets/river-game/catalog.json')
const BASE_URL = process.argv.find(arg => arg.startsWith('--base='))?.slice('--base='.length) ?? 'https://clueterra.com'
const DRY_RUN = process.argv.includes('--dry-run')
const CONCURRENCY = Number(process.argv.find(arg => arg.startsWith('--concurrency='))?.slice('--concurrency='.length) ?? 4)
const MAP_TILE_SIZE = 512
const FIT_MAX_ZOOM = 7.2
const FIT_MIN_ZOOM = 0.6
const REVEAL_ZOOM_OFFSET = 0.55
const VIEW_MARGIN = 1.6
const WORLD_MAX_ZOOM = 3
const VIEWPORTS = [
  { name: 'desktop', width: 820, height: 600 },
  { name: 'mobile', width: 340, height: 230 },
]
const LAYERS = [
  { name: 'relief', template: '/tiles/relief/{z}/{y}/{x}.jpeg', tileSize: 256, maxZoom: 8 },
  { name: 'hillshade', template: '/tiles/terrain/{z}/{x}/{y}.webp', tileSize: 1024, maxZoom: 10 },
]
const REVEAL_TERRAIN = { name: 'terrain', template: '/tiles/terrain/{z}/{x}/{y}.webp', tileSize: 2048, maxZoom: 10 }

function project(lng, lat) {
  const sin = Math.sin(lat * Math.PI / 180)
  return [(lng + 180) / 360, 0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)]
}

function fitZoom([west, south, east, north], viewport) {
  const [left, top] = project(west, north)
  const [right, bottom] = project(east, south)
  const scale = Math.min(viewport.width / Math.max(1e-6, right - left), viewport.height / Math.max(1e-6, bottom - top)) / MAP_TILE_SIZE
  return Math.max(FIT_MIN_ZOOM, Math.min(FIT_MAX_ZOOM, Math.log2(scale)))
}

function tilesFor(bbox, zoom, layer, viewport) {
  const tileZoom = Math.max(0, Math.min(layer.maxZoom, Math.round(zoom + Math.log2(MAP_TILE_SIZE / layer.tileSize))))
  const count = 2 ** tileZoom
  const [west, south, east, north] = bbox
  const [left, top] = project(west, north)
  const [right, bottom] = project(east, south)
  const centerX = (left + right) / 2
  const centerY = (top + bottom) / 2
  const worldPixels = MAP_TILE_SIZE * 2 ** zoom
  const halfWidth = viewport.width * VIEW_MARGIN / 2 / worldPixels
  const halfHeight = viewport.height * VIEW_MARGIN / 2 / worldPixels
  const clamp = value => Math.max(0, Math.min(count - 1, Math.floor(value * count)))
  const tiles = []
  for (let x = clamp(centerX - halfWidth); x <= clamp(centerX + halfWidth); x++) {
    for (let y = clamp(centerY - halfHeight); y <= clamp(centerY + halfHeight); y++) {
      tiles.push(layer.template.replace('{z}', tileZoom).replace('{x}', x).replace('{y}', y))
    }
  }
  return tiles
}

function worldTiles(layer) {
  const tiles = []
  for (let z = 0; z <= WORLD_MAX_ZOOM; z++) {
    for (let x = 0; x < 2 ** z; x++) {
      for (let y = 0; y < 2 ** z; y++) tiles.push(layer.template.replace('{z}', z).replace('{x}', x).replace('{y}', y))
    }
  }
  return tiles
}

async function warm(paths) {
  const stats = { HIT: 0, MISS: 0, failed: 0, bytes: 0 }
  let next = 0
  async function worker() {
    while (next < paths.length) {
      const path = paths[next++]
      try {
        const response = await fetch(BASE_URL + path, { headers: { 'User-Agent': 'ClueterraDev/1.0 (tile-warmer)' } })
        const body = await response.arrayBuffer()
        if (!response.ok) {
          stats.failed++
          continue
        }
        stats.bytes += body.byteLength
        const status = response.headers.get('x-cache-status') ?? 'MISS'
        stats[status === 'HIT' ? 'HIT' : 'MISS']++
      } catch {
        stats.failed++
      }
      if (next % 500 === 0) console.log(`${next}/${paths.length}`, stats)
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker))
  return stats
}

const catalog = JSON.parse(await readFile(CATALOG, 'utf8'))
const courses = new Map(catalog.entries.map(entry => [entry.riverKey, entry.course]))
const paths = new Set(worldTiles(LAYERS[0]))
for (const course of courses.values()) {
  for (const viewport of VIEWPORTS) {
    const zoom = fitZoom(course.bbox, viewport)
    LAYERS.forEach(layer => tilesFor(course.bbox, zoom, layer, viewport).forEach(path => paths.add(path)))
    tilesFor(course.bbox, zoom + REVEAL_ZOOM_OFFSET, REVEAL_TERRAIN, viewport).forEach(path => paths.add(path))
  }
}
const list = [...paths].sort()
const byLayer = list.reduce((counts, path) => ({ ...counts, [path.split('/')[2]]: (counts[path.split('/')[2]] ?? 0) + 1 }), {})
console.log(`${courses.size} rivers -> ${list.length} unique tiles`, byLayer)
if (!DRY_RUN) console.log('done', await warm(list))
