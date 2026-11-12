import { useEffect, useState } from 'react'

export interface BadgeAccent {
  solid: string
  glow: string
  softGlow: string
  border: string
  surface: string
}

const accentCache = new Map<string, BadgeAccent>()
const pendingAccentCache = new Map<string, Promise<BadgeAccent>>()
const FALLBACK_HUES = [198, 218, 246, 278, 326, 42, 156]

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function hashString(value: string) {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash * 31 + value.charCodeAt(i)) >>> 0
  }
  return hash
}

function hslToRgb(h: number, s: number, l: number) {
  const hue = ((h % 360) + 360) % 360 / 360
  const saturation = clamp(s, 0, 100) / 100
  const lightness = clamp(l, 0, 100) / 100

  if (saturation === 0) {
    const value = Math.round(lightness * 255)
    return { r: value, g: value, b: value }
  }

  const q = lightness < 0.5
    ? lightness * (1 + saturation)
    : lightness + saturation - lightness * saturation
  const p = 2 * lightness - q

  const hueToChannel = (t: number) => {
    let channel = t
    if (channel < 0) channel += 1
    if (channel > 1) channel -= 1
    if (channel < 1 / 6) return p + (q - p) * 6 * channel
    if (channel < 1 / 2) return q
    if (channel < 2 / 3) return p + (q - p) * (2 / 3 - channel) * 6
    return p
  }

  return {
    r: Math.round(hueToChannel(hue + 1 / 3) * 255),
    g: Math.round(hueToChannel(hue) * 255),
    b: Math.round(hueToChannel(hue - 1 / 3) * 255),
  }
}

function buildAccent(r: number, g: number, b: number): BadgeAccent {
  const red = clamp(Math.round(r), 0, 255)
  const green = clamp(Math.round(g), 0, 255)
  const blue = clamp(Math.round(b), 0, 255)

  return {
    solid: `rgb(${red}, ${green}, ${blue})`,
    glow: `rgba(${red}, ${green}, ${blue}, 0.9)`,
    softGlow: `rgba(${red}, ${green}, ${blue}, 0.22)`,
    border: `rgba(${red}, ${green}, ${blue}, 0.34)`,
    surface: `rgba(${red}, ${green}, ${blue}, 0.14)`,
  }
}

function createFallbackAccent(seed: string) {
  const hue = FALLBACK_HUES[hashString(seed) % FALLBACK_HUES.length]
  const fallbackRgb = hslToRgb(hue, 84, 68)
  return buildAccent(fallbackRgb.r, fallbackRgb.g, fallbackRgb.b)
}

function sampleAccentFromImageData(data: Uint8ClampedArray, fallback: BadgeAccent) {
  let totalWeight = 0
  let red = 0
  let green = 0
  let blue = 0

  for (let index = 0; index < data.length; index += 4) {
    const alpha = data[index + 3]
    if (alpha < 120) continue

    const r = data[index]
    const g = data[index + 1]
    const b = data[index + 2]
    const max = Math.max(r, g, b)
    const min = Math.min(r, g, b)
    const saturation = max - min

    if (saturation < 18 && max < 110) continue

    const vividness = saturation / 255
    const brightness = max / 255
    const weight = Math.max(0.35, vividness * 1.35 + brightness * 0.4)

    red += r * weight
    green += g * weight
    blue += b * weight
    totalWeight += weight
  }

  if (totalWeight === 0) {
    return fallback
  }

  return buildAccent(red / totalWeight, green / totalWeight, blue / totalWeight)
}

export function resolveBadgeAccent(imageUrl: string | null | undefined, seed: string): Promise<BadgeAccent> {
  const fallbackAccent = createFallbackAccent(seed)
  const cacheKey = imageUrl || `fallback:${seed}`

  if (accentCache.has(cacheKey)) {
    return Promise.resolve(accentCache.get(cacheKey)!)
  }

  if (!imageUrl || typeof window === 'undefined') {
    accentCache.set(cacheKey, fallbackAccent)
    return Promise.resolve(fallbackAccent)
  }

  if (pendingAccentCache.has(cacheKey)) {
    return pendingAccentCache.get(cacheKey)!
  }

  const request = new Promise<BadgeAccent>((resolve) => {
    const img = new Image()
    img.decoding = 'async'
    img.crossOrigin = 'anonymous'

    const finish = (accent: BadgeAccent) => {
      accentCache.set(cacheKey, accent)
      pendingAccentCache.delete(cacheKey)
      resolve(accent)
    }

    img.onload = () => {
      try {
        const canvas = document.createElement('canvas')
        const size = 24
        canvas.width = size
        canvas.height = size

        const context = canvas.getContext('2d', { willReadFrequently: true })
        if (!context) {
          finish(fallbackAccent)
          return
        }

        context.drawImage(img, 0, 0, size, size)
        const imageData = context.getImageData(0, 0, size, size)
        finish(sampleAccentFromImageData(imageData.data, fallbackAccent))
      } catch {
        finish(fallbackAccent)
      }
    }

    img.onerror = () => finish(fallbackAccent)
    img.src = imageUrl
  })

  pendingAccentCache.set(cacheKey, request)
  return request
}

export function useBadgeAccent(imageUrl: string | null | undefined, seed: string) {
  const [accent, setAccent] = useState<BadgeAccent>(() => createFallbackAccent(seed))

  useEffect(() => {
    let active = true

    resolveBadgeAccent(imageUrl, seed).then((resolvedAccent) => {
      if (active) {
        setAccent(resolvedAccent)
      }
    })

    return () => {
      active = false
    }
  }, [imageUrl, seed])

  return accent
}
