import { useEffect, useMemo } from 'react'
import { CanvasTexture, LinearMipmapLinearFilter, SRGBColorSpace, type Texture } from 'three'
import { apiClient } from '@/api/client'
import { flagUrl } from '../silhouettes/silhouetteGameConstants'
import {
  ATLAS_ANISOTROPY,
  ATLAS_CELL_HEIGHT,
  ATLAS_CELL_WIDTH,
  ATLAS_COLUMNS,
  ATLAS_PADDING,
} from './flagPlazaConstants'
import type { FlagPlazaSlot } from './flagPlazaLayout'

export interface FlagAtlas {
  texture: Texture
  cellSize: [number, number]
  offsets: Float32Array
}

interface FlagAtlasSurface extends FlagAtlas {
  context: CanvasRenderingContext2D
  cells: Map<string, [number, number]>
  claimed: Set<string>
}

const BUNDLE_PATH = '/assets/flags/bundle'
const BUNDLE_SEPARATOR = ','
const SVG_DATA_URL = 'data:image/svg+xml;charset=utf-8,'
const INNER_WIDTH = ATLAS_CELL_WIDTH - ATLAS_PADDING * 2
const INNER_HEIGHT = ATLAS_CELL_HEIGHT - ATLAS_PADDING * 2

function createSurface(slots: FlagPlazaSlot[]): FlagAtlasSurface | null {
  const canvas = document.createElement('canvas')
  canvas.width = ATLAS_COLUMNS * ATLAS_CELL_WIDTH
  canvas.height = Math.ceil(slots.length / ATLAS_COLUMNS) * ATLAS_CELL_HEIGHT
  const context = canvas.getContext('2d')
  if (!context) return null
  const offsets = new Float32Array(slots.length * 2)
  const cells = new Map<string, [number, number]>()
  slots.forEach((slot, index) => {
    const left = (index % ATLAS_COLUMNS) * ATLAS_CELL_WIDTH + ATLAS_PADDING
    const top = Math.floor(index / ATLAS_COLUMNS) * ATLAS_CELL_HEIGHT + ATLAS_PADDING
    offsets[index * 2] = left / canvas.width
    offsets[index * 2 + 1] = 1 - (top + INNER_HEIGHT) / canvas.height
    cells.set(slot.country.iso, [left, top])
  })
  const texture = new CanvasTexture(canvas)
  texture.colorSpace = SRGBColorSpace
  texture.minFilter = LinearMipmapLinearFilter
  texture.anisotropy = ATLAS_ANISOTROPY
  return {
    texture,
    cellSize: [INNER_WIDTH / canvas.width, INNER_HEIGHT / canvas.height],
    offsets,
    context,
    cells,
    claimed: new Set(),
  }
}

function claimMissing(surface: FlagAtlasSurface, isos: string[]) {
  const missing = isos.filter(iso => surface.cells.has(iso) && !surface.claimed.has(iso))
  missing.forEach(iso => surface.claimed.add(iso))
  return missing
}

function release(surface: FlagAtlasSurface, isos: string[]) {
  isos.forEach(iso => surface.claimed.delete(iso))
}

function loadImage(src: string): Promise<HTMLImageElement> {
  const image = new Image()
  image.decoding = 'async'
  image.src = src
  return image.decode().then(() => image)
}

async function flagImages(isos: string[]): Promise<Map<string, HTMLImageElement>> {
  if (isos.length === 1) return new Map([[isos[0], await loadImage(flagUrl(isos[0]))]])
  const { data } = await apiClient.get<Record<string, string>>(BUNDLE_PATH, {
    params: { codes: isos.map(iso => iso.toLowerCase()).join(BUNDLE_SEPARATOR) },
  })
  const entries = await Promise.all(isos.map(async iso => {
    const svg = data[iso.toLowerCase()]
    return svg ? [iso, await loadImage(SVG_DATA_URL + encodeURIComponent(svg))] as const : null
  }))
  return new Map(entries.filter(entry => entry !== null))
}

function paint(surface: FlagAtlasSurface, images: Map<string, HTMLImageElement>) {
  images.forEach((image, iso) => {
    const cell = surface.cells.get(iso)
    if (cell) surface.context.drawImage(image, cell[0], cell[1], INNER_WIDTH, INNER_HEIGHT)
  })
  surface.texture.needsUpdate = true
}

export function useFlagAtlas(slots: FlagPlazaSlot[] | null, solved: string[]): FlagAtlas | null {
  const surface = useMemo(() => (slots ? createSurface(slots) : null), [slots])

  useEffect(() => () => surface?.texture.dispose(), [surface])

  useEffect(() => {
    if (!surface) return
    const missing = claimMissing(surface, solved)
    if (missing.length === 0) return
    flagImages(missing)
      .then(images => {
        paint(surface, images)
        release(surface, missing.filter(iso => !images.has(iso)))
      })
      .catch(() => release(surface, missing))
  }, [surface, solved])

  return surface
}
