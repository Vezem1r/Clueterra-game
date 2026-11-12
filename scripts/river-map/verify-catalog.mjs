#!/usr/bin/env node

import { createHash } from 'node:crypto'
import { mkdir, readFile, readdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { readWebpDimensions } from './webp-renderer.mjs'

const outputArg = process.argv.find(arg => arg.startsWith('--output='))?.slice('--output='.length)
const output = resolve(outputArg ?? 'backend/clueterra-questions/src/main/resources/assets/river-game')
const catalog = JSON.parse(await readFile(resolve(output, 'catalog.json'), 'utf8'))
const counts = new Map()
const referencedKeys = new Set()

for (const entry of catalog.entries) {
  counts.set(entry.countryCode, (counts.get(entry.countryCode) ?? 0) + 1)
  if (entry.plainImageKey === entry.bordersImageKey) {
    throw new Error(`Plain and borders maps are identical: ${entry.countryCode}/${entry.riverKey}`)
  }
  const { contentHash, ...payload } = entry
  if (createHash('sha256').update(JSON.stringify(payload)).digest('hex') !== contentHash) {
    throw new Error(`Question content checksum mismatch: ${entry.countryCode}/${entry.riverKey}`)
  }
  for (const key of [entry.plainImageKey, entry.bordersImageKey]) {
    if (!/^river-game\/[0-9a-f]{64}\.webp$/.test(key)) {
      throw new Error(`Invalid river asset key: ${key}`)
    }
    referencedKeys.add(key)
    const file = resolve(output, key.slice('river-game/'.length))
    const bytes = await readFile(file)
    const expected = key.match(/^river-game\/([0-9a-f]{64})\.webp$/)[1]
    const actual = createHash('sha256').update(bytes).digest('hex')
    if (actual !== expected) throw new Error(`Asset checksum mismatch: ${key}`)
    const { width, height } = readWebpDimensions(bytes)
    if (width !== 1800 || height !== 1200) {
      throw new Error(`Invalid WebP dimensions ${width}×${height}: ${key}`)
    }
  }
}

if (!outputArg) {
  const assetFiles = (await readdir(output)).filter(name => /\.(svg|webp)$/.test(name))
  if (assetFiles.length !== referencedKeys.size || assetFiles.some(name => !referencedKeys.has(`river-game/${name}`))) {
    throw new Error(`Expected exactly ${referencedKeys.size} referenced WebP assets; found ${assetFiles.length}`)
  }
}

if ([...counts.values()].some(count => count < 1 || count > 5)) {
  throw new Error('Expected one to five river questions per country')
}
for (const [countryCode, count] of counts) {
  const entries = catalog.entries.filter(entry => entry.countryCode === countryCode)
  if (new Set(entries.map(entry => entry.riverKey)).size !== count
    || entries.some(entry => entry.rankInCountry < 1 || entry.rankInCountry > count)
    || new Set(entries.map(entry => entry.rankInCountry)).size !== count) {
    throw new Error(`Duplicate river or nonconsecutive rank for ${countryCode}`)
  }
}
const distinctRivers = new Set(catalog.entries.map(entry => entry.riverKey)).size
console.log(`Verified ${catalog.entries.length} questions (${distinctRivers} distinct rivers) and ${referencedKeys.size} unique WebP assets across ${counts.size} countries.`)

if (process.argv.includes('--screenshots')) {
  const { chromium } = await import(pathToFileURL(resolve('frontend/node_modules/playwright/index.mjs')).href)
  const browser = await chromium.launch({ headless: true })
  const previewDir = resolve('scripts/.cache/river-game/verified')
  await mkdir(previewDir, { recursive: true })
  try {
    for (const countryCode of ['BR', 'EG', 'DE', 'IN', 'US']) {
      const entry = catalog.entries.find(item => item.countryCode === countryCode)
      if (!entry) continue
      for (const variant of ['plainImageKey', 'bordersImageKey']) {
        const page = await browser.newPage({ viewport: { width: 900, height: 600 }, deviceScaleFactor: 1 })
        try {
          await page.goto(pathToFileURL(resolve(output, entry[variant].slice('river-game/'.length))).href)
          await page.evaluate(() => {
            document.body.style.margin = '0'
            const image = document.querySelector('img')
            image.style.width = '900px'
            image.style.height = '600px'
          })
          const target = resolve(previewDir, `${countryCode.toLowerCase()}-${variant === 'plainImageKey' ? 'plain' : 'borders'}.png`)
          await page.screenshot({ path: target })
          console.log(target)
        } finally {
          await page.close()
        }
      }
    }
  } finally {
    await browser.close()
  }
}
