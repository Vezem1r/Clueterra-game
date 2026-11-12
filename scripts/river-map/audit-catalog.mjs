#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const assetRoot = resolve('backend/clueterra-questions/src/main/resources/assets/river-game')
const auditRoot = resolve('scripts/.cache/river-game/audit')
const entries = JSON.parse(await readFile(resolve(assetRoot, 'catalog.json'), 'utf8')).entries
const unique = [...new Map(entries.map(entry => [entry.riverKey, entry])).values()]
const batchSize = 10
const batches = Array.from({ length: Math.ceil(unique.length / batchSize) }, (_, index) => unique.slice(index * batchSize, index * batchSize + batchSize))
const { chromium } = await import(pathToFileURL(resolve('frontend/node_modules/playwright/index.mjs')).href)
const browser = await chromium.launch({ headless: true })
const problems = []
await mkdir(auditRoot, { recursive: true })

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char])
}

try {
  for (const [batchIndex, batch] of batches.entries()) {
    const items = await Promise.all(batch.map(async entry => ({
      entry,
      plain: await readFile(resolve(assetRoot, entry.plainImageKey.slice('river-game/'.length))),
      borders: await readFile(resolve(assetRoot, entry.bordersImageKey.slice('river-game/'.length))),
    })))
    const images = items.map(({ entry, plain, borders }) => `
      <figure><div class="pair">
        <img src="data:image/webp;base64,${plain.toString('base64')}" alt="River map">
        <img src="data:image/webp;base64,${borders.toString('base64')}" alt="Country borders reveal">
      </div><figcaption>${escapeHtml(entry.countryCode)} · ${escapeHtml(entry.nameEn)} · #${entry.rankInCountry}</figcaption></figure>`).join('')
    const page = await browser.newPage({ viewport: { width: 1980, height: 600 }, deviceScaleFactor: 1 })
    try {
      await page.setContent(`<html><head><style>
        body{margin:0;background:#06131c;color:#d8f5f7;font:18px Arial}
        main{display:grid;grid-template-columns:repeat(4,1fr);gap:4px;padding:4px}
        figure{margin:0;background:#0b2230;overflow:hidden}.pair{display:flex}
        img{width:50%;aspect-ratio:3/2;object-fit:contain;display:block}
        figcaption{height:30px;padding:5px 8px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
      </style></head><body><main>${images}</main></body></html>`, { waitUntil: 'load' })
      const dimensions = await page.evaluate(async () => {
        const results = []
        for (const image of document.images) {
          try {
            await image.decode()
            results.push({ width: image.naturalWidth, height: image.naturalHeight })
          } catch (error) {
            results.push({ width: 0, height: 0, error: String(error) })
          }
        }
        return results
      })
      items.forEach(({ entry }, index) => {
        if ([dimensions[index * 2], dimensions[index * 2 + 1]]
          .some(({ width, height }) => width !== 1800 || height !== 1200)) {
          const pair = dimensions.slice(index * 2, index * 2 + 2)
          problems.push(`${entry.countryCode}/${entry.nameEn}: WebP map did not decode at 1800×1200 (${JSON.stringify(pair)})`)
        }
      })
      await page.screenshot({ path: resolve(auditRoot, `sheet-${String(batchIndex + 1).padStart(2, '0')}.jpg`), type: 'jpeg', quality: 80, fullPage: true })
      console.log(`Visual sheet ${batchIndex + 1}/${batches.length}: ${batch.length} maps`)
    } finally {
      await page.close()
    }
  }
} finally {
  await browser.close()
}

const report = { questions: entries.length, uniqueRivers: unique.length,
  uniqueAssets: new Set(entries.flatMap(entry => [entry.plainImageKey, entry.bordersImageKey])).size,
  problems }
await writeFile(resolve(auditRoot, 'report.json'), `${JSON.stringify(report, null, 2)}\n`)
console.log(`Audited ${report.questions} questions and ${report.uniqueAssets} unique WebP maps; ${problems.length} problems.`)
if (problems.length) {
  console.error(problems.join('\n'))
  process.exitCode = 1
}
