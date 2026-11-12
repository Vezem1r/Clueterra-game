#!/usr/bin/env node

import { createHash } from 'node:crypto'
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'

const NE_VERSION = 'v5.1.2'
const BORDER_LINES_URL = `https://raw.githubusercontent.com/nvkelso/natural-earth-vector/${NE_VERSION}/geojson/ne_50m_admin_0_boundary_lines_land.geojson`
const CACHE = resolve('scripts/.cache/river-game')
const OUTPUT = resolve(process.argv.find(arg => arg.startsWith('--output='))?.slice('--output='.length)
  ?? 'frontend/public/maps/country-borders.json')
const BORDER_TOLERANCE_DEG = 0.02
const COORDINATE_DECIMALS = 3
const CACHE_MAX_AGE_MS = 30 * 86400000

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

async function getJson(url) {
  const cachePath = resolve(CACHE, `${sha256(url)}.json`)
  const cached = await stat(cachePath).catch(() => null)
  if (cached && Date.now() - cached.mtimeMs < CACHE_MAX_AGE_MS) return JSON.parse(await readFile(cachePath, 'utf8'))
  const response = await fetch(url, { headers: { 'User-Agent': 'ClueterraRiverGame/1.0 (https://clueterra.com)' } })
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`)
  const value = await response.json()
  await mkdir(CACHE, { recursive: true })
  await writeFile(cachePath, JSON.stringify(value))
  return value
}

function allLines(geometry) {
  if (geometry?.type === 'LineString') return [geometry.coordinates]
  if (geometry?.type === 'MultiLineString') return geometry.coordinates
  return []
}

function perpendicularDistance([x, y], [x1, y1], [x2, y2]) {
  const dx = x2 - x1
  const dy = y2 - y1
  const length = Math.hypot(dx, dy)
  return length === 0 ? Math.hypot(x - x1, y - y1) : Math.abs(dy * x - dx * y + x2 * y1 - y2 * x1) / length
}

function simplify(line, tolerance) {
  if (line.length <= 2) return line
  let farthest = 0
  let index = 0
  for (let i = 1; i < line.length - 1; i++) {
    const distance = perpendicularDistance(line[i], line[0], line[line.length - 1])
    if (distance > farthest) {
      farthest = distance
      index = i
    }
  }
  if (farthest <= tolerance) return [line[0], line[line.length - 1]]
  return [...simplify(line.slice(0, index + 1), tolerance).slice(0, -1), ...simplify(line.slice(index), tolerance)]
}

function round(line) {
  const factor = 10 ** COORDINATE_DECIMALS
  return line.map(([x, y]) => [Math.round(x * factor) / factor, Math.round(y * factor) / factor])
}

const borderLines = await getJson(BORDER_LINES_URL)
const coordinates = borderLines.features.flatMap(feature => allLines(feature.geometry))
  .map(line => round(simplify(line, BORDER_TOLERANCE_DEG)))
  .filter(line => line.length >= 2)
const body = JSON.stringify({ type: 'Feature', properties: {}, geometry: { type: 'MultiLineString', coordinates } })
await mkdir(dirname(OUTPUT), { recursive: true })
await writeFile(OUTPUT, body)
console.log(`${coordinates.length} border lines, ${Math.round(body.length / 1024)} KB written to ${OUTPUT}`)
