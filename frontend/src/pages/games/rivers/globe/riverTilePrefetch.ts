import { RIVER_MAP_TILE_SIZE, RIVER_PREFETCH_TILE_LIMIT } from './riverGlobeConstants'
import { projectMercator, type RiverCamera, type RiverViewport } from './riverMercator'

export interface RiverTileTemplate {
  url: string
  tileSize: number
  maxZoom: number
}

const prefetched = new Set<string>()

export function prefetchRiverTiles({ center, zoom }: RiverCamera, viewport: RiverViewport, templates: RiverTileTemplate[]) {
  const worldSize = RIVER_MAP_TILE_SIZE * 2 ** zoom
  const [unitX, unitY] = projectMercator(center[0], center[1])
  const x = unitX * worldSize
  const y = unitY * worldSize
  const { padding, width, height } = viewport
  const innerWidth = width - padding.left - padding.right
  const innerHeight = height - padding.top - padding.bottom
  const left = x - padding.left - innerWidth / 2
  const right = x + padding.right + innerWidth / 2
  const top = y - padding.top - innerHeight / 2
  const bottom = y + padding.bottom + innerHeight / 2
  for (const template of templates) {
    const tileZoom = Math.max(0, Math.min(template.maxZoom, Math.round(zoom + Math.log2(RIVER_MAP_TILE_SIZE / template.tileSize))))
    const count = 2 ** tileZoom
    const tilePixels = worldSize / count
    const clamp = (value: number) => Math.max(0, Math.min(count - 1, Math.floor(value / tilePixels)))
    const urls: string[] = []
    for (let column = clamp(left); column <= clamp(right); column++) {
      for (let row = clamp(top); row <= clamp(bottom); row++) {
        urls.push(template.url.replace('{z}', String(tileZoom)).replace('{x}', String(column)).replace('{y}', String(row)))
      }
    }
    urls.filter(url => !prefetched.has(url)).slice(0, RIVER_PREFETCH_TILE_LIMIT).forEach(url => {
      prefetched.add(url)
      fetch(url).then(response => response.arrayBuffer()).catch(() => prefetched.delete(url))
    })
  }
}
