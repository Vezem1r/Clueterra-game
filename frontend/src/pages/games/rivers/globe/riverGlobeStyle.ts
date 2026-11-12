import type { ExpressionSpecification, RasterDEMSourceSpecification, StyleSpecification } from 'maplibre-gl'
import {
  RIVER_COLORS,
  RIVER_DRAW_EDGE,
  RIVER_DRAW_HEAD,
  RIVER_FLARE_FADE_MS,
  RIVER_HILLSHADE_MIN_ZOOM,
  RIVER_RELIEF_ATTRIBUTION,
  RIVER_RELIEF_MAX_ZOOM,
  RIVER_RELIEF_TILE_SIZE,
  RIVER_RELIEF_TILES,
  RIVER_TERRAIN_ATTRIBUTION,
  RIVER_TERRAIN_MAX_ZOOM,
  RIVER_TERRAIN_TILE_SIZE,
  RIVER_TERRAIN_TILES,
  RIVER_VECTOR_ATTRIBUTION,
} from './riverGlobeConstants'

export const RIVER_SOURCE = 'river'
export const RIVER_BORDER_SOURCE = 'borders'
export const RIVER_TERRAIN_SOURCE = 'terrain'
export const RIVER_BORDER_LAYER = 'borders'
export const RIVER_FLARE_LAYER = 'river-flare'
export const RIVER_DRAWN = 1

const HEX_RADIX = 16
const HEX_CHANNELS = [1, 3, 5]
const HEX_CHANNEL_LENGTH = 2

const RIVER_LINES = [
  { id: 'river-shadow', color: RIVER_COLORS.shadow, head: RIVER_COLORS.shadow },
  { id: RIVER_FLARE_LAYER, color: RIVER_COLORS.glow, head: RIVER_COLORS.glow },
  { id: 'river-glow', color: RIVER_COLORS.glow, head: RIVER_COLORS.halo },
  { id: 'river-halo', color: RIVER_COLORS.halo, head: RIVER_COLORS.core },
  { id: 'river-core', color: RIVER_COLORS.core, head: RIVER_COLORS.highlight },
]

const EMPTY_COLLECTION = { type: 'FeatureCollection' as const, features: [] }
const ROUND_LINE = { 'line-cap': 'round', 'line-join': 'round' } as const

function transparent(hex: string) {
  const [red, green, blue] = HEX_CHANNELS.map(start => parseInt(hex.slice(start, start + HEX_CHANNEL_LENGTH), HEX_RADIX))
  return `rgba(${red}, ${green}, ${blue}, 0)`
}

function riverGradient(color: string, head: string, progress: number): ExpressionSpecification {
  const hidden = transparent(color)
  if (progress <= 0) return ['interpolate', ['linear'], ['line-progress'], 0, hidden, 1, hidden]
  if (progress >= RIVER_DRAWN) return ['interpolate', ['linear'], ['line-progress'], 0, color, 1, color]
  const lead = progress - RIVER_DRAW_HEAD
  return lead > 0
    ? ['interpolate', ['linear'], ['line-progress'], 0, color, lead, color, progress, head, progress + RIVER_DRAW_EDGE, hidden]
    : ['interpolate', ['linear'], ['line-progress'], 0, head, progress, head, progress + RIVER_DRAW_EDGE, hidden]
}

export function riverGradients(progress: number) {
  return RIVER_LINES.map(line => ({ id: line.id, gradient: riverGradient(line.color, line.head, progress) }))
}

function drawn(id: string) {
  const line = RIVER_LINES.find(item => item.id === id) ?? RIVER_LINES[0]
  return riverGradient(line.color, line.head, RIVER_DRAWN)
}

function terrainSource(origin: string, attribution?: string): RasterDEMSourceSpecification {
  return {
    type: 'raster-dem',
    tiles: [origin + RIVER_TERRAIN_TILES],
    encoding: 'terrarium',
    tileSize: RIVER_TERRAIN_TILE_SIZE,
    maxzoom: RIVER_TERRAIN_MAX_ZOOM,
    ...(attribution ? { attribution } : {}),
  }
}

export function buildRiverGlobeStyle(origin: string): StyleSpecification {
  return {
    version: 8,
    projection: { type: 'globe' },
    sky: {
      'sky-color': RIVER_COLORS.sky,
      'horizon-color': RIVER_COLORS.horizon,
      'fog-color': RIVER_COLORS.fog,
      'sky-horizon-blend': 0.6,
      'horizon-fog-blend': 0.7,
      'fog-ground-blend': 0.85,
      'atmosphere-blend': ['interpolate', ['linear'], ['zoom'], 0, 1, 5, 1, 8, 0],
    },
    sources: {
      relief: {
        type: 'raster',
        tiles: [origin + RIVER_RELIEF_TILES],
        tileSize: RIVER_RELIEF_TILE_SIZE,
        maxzoom: RIVER_RELIEF_MAX_ZOOM,
        attribution: RIVER_RELIEF_ATTRIBUTION,
      },
      hillshade: terrainSource(origin, RIVER_TERRAIN_ATTRIBUTION),
      [RIVER_TERRAIN_SOURCE]: terrainSource(origin),
      [RIVER_BORDER_SOURCE]: { type: 'geojson', data: EMPTY_COLLECTION, attribution: RIVER_VECTOR_ATTRIBUTION },
      [RIVER_SOURCE]: { type: 'geojson', data: EMPTY_COLLECTION, lineMetrics: true },
    },
    layers: [
      { id: 'ocean', type: 'background', paint: { 'background-color': RIVER_COLORS.ocean } },
      {
        id: 'relief', type: 'raster', source: 'relief',
        paint: { 'raster-saturation': -0.08, 'raster-contrast': 0.12, 'raster-brightness-max': 0.88, 'raster-fade-duration': 250 },
      },
      {
        id: 'hillshade', type: 'hillshade', source: 'hillshade', minzoom: RIVER_HILLSHADE_MIN_ZOOM,
        paint: {
          'hillshade-exaggeration': ['interpolate', ['linear'], ['zoom'], RIVER_HILLSHADE_MIN_ZOOM, 0, 4, 0.24, 7, 0.42],
          'hillshade-shadow-color': RIVER_COLORS.shadow,
          'hillshade-highlight-color': RIVER_COLORS.highlight,
          'hillshade-accent-color': RIVER_COLORS.shadow,
        },
      },
      {
        id: RIVER_BORDER_LAYER, type: 'line', source: RIVER_BORDER_SOURCE,
        layout: { 'line-join': 'round' },
        paint: {
          'line-color': RIVER_COLORS.border,
          'line-width': ['interpolate', ['linear'], ['zoom'], 1, 0.4, 6, 1.1, 9, 1.6],
          'line-opacity': 0,
          'line-opacity-transition': { duration: 700, delay: 0 },
        },
      },
      {
        id: 'river-shadow', type: 'line', source: RIVER_SOURCE, layout: ROUND_LINE,
        paint: {
          'line-gradient': drawn('river-shadow'),
          'line-width': ['interpolate', ['linear'], ['zoom'], 1, 6, 5, 12, 9, 22],
          'line-blur': ['interpolate', ['linear'], ['zoom'], 1, 4, 5, 8, 9, 14],
          'line-opacity': 0.5,
        },
      },
      {
        id: RIVER_FLARE_LAYER, type: 'line', source: RIVER_SOURCE, layout: ROUND_LINE,
        paint: {
          'line-gradient': drawn(RIVER_FLARE_LAYER),
          'line-width': ['interpolate', ['linear'], ['zoom'], 1, 16, 5, 34, 9, 56],
          'line-blur': ['interpolate', ['linear'], ['zoom'], 1, 12, 5, 26, 9, 42],
          'line-opacity': 0,
          'line-opacity-transition': { duration: RIVER_FLARE_FADE_MS, delay: 0 },
        },
      },
      {
        id: 'river-glow', type: 'line', source: RIVER_SOURCE, layout: ROUND_LINE,
        paint: {
          'line-gradient': drawn('river-glow'),
          'line-width': ['interpolate', ['linear'], ['zoom'], 1, 7, 5, 16, 9, 28],
          'line-blur': ['interpolate', ['linear'], ['zoom'], 1, 5, 5, 12, 9, 20],
          'line-opacity': 0.55,
        },
      },
      {
        id: 'river-halo', type: 'line', source: RIVER_SOURCE, layout: ROUND_LINE,
        paint: {
          'line-gradient': drawn('river-halo'),
          'line-width': ['interpolate', ['linear'], ['zoom'], 1, 2.5, 5, 4.5, 9, 7],
          'line-blur': 1.5,
          'line-opacity': 0.9,
        },
      },
      {
        id: 'river-core', type: 'line', source: RIVER_SOURCE, layout: ROUND_LINE,
        paint: {
          'line-gradient': drawn('river-core'),
          'line-width': ['interpolate', ['linear'], ['zoom'], 1, 1, 5, 1.6, 9, 2.6],
        },
      },
    ],
  }
}
