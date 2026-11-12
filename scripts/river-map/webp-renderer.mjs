import { stat } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const WIDTH = 900
const HEIGHT = 600
const SCALE = 2
const QUALITY = 0.95

export function readWebpDimensions(bytes) {
  if (bytes.length < 30 || bytes.toString('ascii', 0, 4) !== 'RIFF'
    || bytes.toString('ascii', 8, 12) !== 'WEBP'
    || bytes.readUInt32LE(4) + 8 !== bytes.length) {
    throw new Error('Invalid WebP container')
  }
  const chunk = bytes.toString('ascii', 12, 16)
  if (chunk === 'VP8X') {
    return { width: bytes.readUIntLE(24, 3) + 1, height: bytes.readUIntLE(27, 3) + 1 }
  }
  if (chunk === 'VP8 ' && bytes[23] === 0x9d && bytes[24] === 0x01 && bytes[25] === 0x2a) {
    return { width: bytes.readUInt16LE(26) & 0x3fff, height: bytes.readUInt16LE(28) & 0x3fff }
  }
  if (chunk === 'VP8L' && bytes[20] === 0x2f) {
    const dimensions = bytes.readUInt32LE(21)
    return { width: (dimensions & 0x3fff) + 1, height: ((dimensions >>> 14) & 0x3fff) + 1 }
  }
  throw new Error(`Unsupported WebP image chunk: ${chunk}`)
}

export async function createRiverWebpRenderer() {
  const browserModule = resolve('frontend/node_modules/playwright/index.mjs')
  if (!(await stat(browserModule).catch(() => null))) {
    throw new Error('River WebP build needs Playwright: install frontend dependencies with npm ci first')
  }
  const { chromium } = await import(pathToFileURL(browserModule).href)
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()

  return {
    async render(svg) {
      const dataUrl = await page.evaluate(async ({ source, width, height, quality }) => {
        const image = new Image()
        image.src = `data:image/svg+xml;base64,${source}`
        await image.decode()
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        canvas.getContext('2d').drawImage(image, 0, 0, width, height)
        return canvas.toDataURL('image/webp', quality)
      }, { source: Buffer.from(svg).toString('base64'), width: WIDTH * SCALE,
        height: HEIGHT * SCALE, quality: QUALITY })
      if (!dataUrl.startsWith('data:image/webp;base64,')) {
        throw new Error('Chromium did not encode the river map as WebP')
      }
      return Buffer.from(dataUrl.substring('data:image/webp;base64,'.length), 'base64')
    },
    async close() {
      await page.close()
      await browser.close()
    },
  }
}
