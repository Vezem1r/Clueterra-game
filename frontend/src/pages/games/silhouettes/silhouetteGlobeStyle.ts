import type { ExpressionSpecification, FilterSpecification, StyleSpecification } from 'maplibre-gl'
import type { FeatureCollection, Point } from 'geojson'
import type { SilhouetteFlagColor } from '@/api/silhouettes'
import { SILHOUETTE_COLORS, SILHOUETTE_GLOBE } from './silhouetteGameConstants'
import type { SilhouetteCountryOption, SilhouetteWorld } from './silhouetteWorld'

export const WORLD_SOURCE = 'world'
export const DOT_SOURCE = 'dots'
export const PAINTED_LAYER = 'painted'
export const PAINTED_EDGE_LAYER = 'painted-edge'
export const PAINTED_GLOW_LAYER = 'painted-glow'
export const STRIPES_LAYER = 'painted-stripes'
export const STRIPES_PREFIX = 'stripes-'
export const FOCUS_LAYER = 'focus'
export const FOCUS_GLOW_LAYER = 'focus-glow'
export const FOCUS_FILL_LAYER = 'focus-fill'
export const HINT_FILL_LAYER = 'hint-fill'
export const HINT_LINE_LAYER = 'hint-line'
export const DOT_LAYER = 'dots'
export const HINT_DOT_LAYER = 'hint-dot'
export const MISSED_FILL_LAYER = 'missed-fill'
export const MISSED_LINE_LAYER = 'missed-line'
export const MISSED_DOT_LAYER = 'missed-dot'
export const REGION_FILL_LAYER = 'region-fill'
export const REGION_LINE_LAYER = 'region-line'
export const DRAPE_BEFORE_LAYER = 'borders'
export const HOVER_LAYERS = [PAINTED_LAYER, DOT_LAYER]

const VECTOR_ATTRIBUTION = '<a href="https://www.naturalearthdata.com" target="_blank" rel="noopener noreferrer">Natural Earth</a>'
const EMPTY_COLLECTION: FeatureCollection = { type: 'FeatureCollection', features: [] }
const NONE = '__none__'
const HEX_RADIX = 16
const HEX_CHANNELS = [1, 3, 5]
const HEX_CHANNEL_LENGTH = 2
const CHANNEL_MAX = 255
const LUMA_WEIGHTS = [0.2126, 0.7152, 0.0722]
const NEUTRAL_LIGHT = 0.86
const NEUTRAL_DARK = 0.1
const MIN_ACCENT_SHARE = 0.1
const RGBA_CHANNELS = 4
const OPAQUE = 255

export interface FlagTones {
  fill: string
  edge: string
}

export function isoListFilter(isos: string[]): FilterSpecification {
  return ['in', ['get', 'iso'], ['literal', isos.length ? isos : [NONE]]]
}

export function regionFilter(subregion: string | null): FilterSpecification {
  return ['==', ['get', 'subregion'], subregion ?? NONE]
}

export function isoFilter(iso: string | null): FilterSpecification {
  return ['==', ['get', 'iso'], iso ?? NONE]
}

function luminance(hex: string) {
  const channels = HEX_CHANNELS.map(start => parseInt(hex.slice(start, start + HEX_CHANNEL_LENGTH), HEX_RADIX) / CHANNEL_MAX)
  return channels.reduce((sum, value, index) => sum + value * LUMA_WEIGHTS[index], 0)
}

function isNeutral(hex: string) {
  const value = luminance(hex)
  return value > NEUTRAL_LIGHT || value < NEUTRAL_DARK
}

export function flagTones(colors: SilhouetteFlagColor[]): FlagTones {
  const accents = colors.filter(entry => !isNeutral(entry.color) && entry.share >= MIN_ACCENT_SHARE)
  const fill = accents[0]?.color ?? colors[0]?.color ?? SILHOUETTE_COLORS.focus
  const edge = accents[1]?.color ?? colors.find(entry => entry.color !== fill)?.color ?? SILHOUETTE_COLORS.focus
  return { fill, edge }
}

function rgb(hex: string) {
  return HEX_CHANNELS.map(start => parseInt(hex.slice(start, start + HEX_CHANNEL_LENGTH), HEX_RADIX))
}

export function flagPattern(colors: SilhouetteFlagColor[]) {
  const size = SILHOUETTE_GLOBE.patternSize
  const data = new Uint8ClampedArray(size * size * RGBA_CHANNELS)
  const stops = colors.reduce<{ until: number, rgb: number[] }[]>((list, entry) => {
    const until = (list.at(-1)?.until ?? 0) + entry.share
    return [...list, { until, rgb: rgb(entry.color) }]
  }, [])
  const total = stops.at(-1)?.until ?? 1
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const position = (((x + y) % size) / size) * total
      const stop = stops.find(item => position < item.until) ?? stops[stops.length - 1]
      data.set([...(stop?.rgb ?? rgb(SILHOUETTE_COLORS.focus)), OPAQUE], (y * size + x) * RGBA_CHANNELS)
    }
  }
  return { width: size, height: size, data }
}

export function tonedWorld(world: SilhouetteWorld): FeatureCollection {
  return {
    type: 'FeatureCollection',
    features: world.collection.features.map(feature => ({
      ...feature,
      properties: {
        iso: feature.properties.iso,
        pool: feature.properties.pool,
        subregion: feature.properties.subregion ?? null,
        ...(feature.properties.colors ? flagTones(feature.properties.colors) : {}),
      },
    })),
  }
}

const FILL_TONE: ExpressionSpecification = ['coalesce', ['get', 'fill'], SILHOUETTE_COLORS.focus]
const EDGE_TONE: ExpressionSpecification = ['coalesce', ['get', 'edge'], SILHOUETTE_COLORS.focus]

export function buildSilhouetteGlobeStyle(): StyleSpecification {
  return {
    version: 8,
    projection: { type: 'globe' },
    sky: {
      'sky-color': SILHOUETTE_COLORS.sky,
      'horizon-color': SILHOUETTE_COLORS.horizon,
      'fog-color': SILHOUETTE_COLORS.fog,
      'sky-horizon-blend': 0.6,
      'horizon-fog-blend': 0.7,
      'fog-ground-blend': 0.85,
      'atmosphere-blend': 1,
    },
    sources: {
      [WORLD_SOURCE]: { type: 'geojson', data: EMPTY_COLLECTION, attribution: VECTOR_ATTRIBUTION },
      [DOT_SOURCE]: { type: 'geojson', data: EMPTY_COLLECTION },
    },
    layers: [
      { id: 'ocean', type: 'background', paint: { 'background-color': SILHOUETTE_COLORS.ocean } },
      {
        id: 'land', type: 'fill', source: WORLD_SOURCE,
        paint: { 'fill-color': ['case', ['get', 'pool'], SILHOUETTE_COLORS.land, SILHOUETTE_COLORS.territory] },
      },
      {
        id: PAINTED_LAYER, type: 'fill', source: WORLD_SOURCE, filter: isoListFilter([]),
        paint: { 'fill-color': FILL_TONE, 'fill-opacity': 0.86 },
      },
      {
        id: STRIPES_LAYER, type: 'fill', source: WORLD_SOURCE, filter: isoListFilter([]),
        layout: { visibility: 'none' },
        paint: { 'fill-pattern': ['concat', STRIPES_PREFIX, ['get', 'iso']], 'fill-opacity': 0.94 },
      },
      {
        id: 'borders', type: 'line', source: WORLD_SOURCE,
        paint: {
          'line-color': SILHOUETTE_COLORS.border,
          'line-width': ['interpolate', ['linear'], ['zoom'], 1, 0.35, 5, 0.9],
          'line-opacity': 0.8,
        },
      },
      {
        id: PAINTED_GLOW_LAYER, type: 'line', source: WORLD_SOURCE, filter: isoListFilter([]),
        layout: { 'line-join': 'round' },
        paint: {
          'line-color': FILL_TONE,
          'line-width': ['interpolate', ['linear'], ['zoom'], 1, 1.4, 5, 4],
          'line-blur': ['interpolate', ['linear'], ['zoom'], 1, 1.2, 5, 3],
          'line-opacity': 0.3,
        },
      },
      {
        id: PAINTED_EDGE_LAYER, type: 'line', source: WORLD_SOURCE, filter: isoListFilter([]),
        layout: { 'line-join': 'round' },
        paint: { 'line-color': EDGE_TONE, 'line-width': ['interpolate', ['linear'], ['zoom'], 1, 0.5, 5, 1.3] },
      },
      {
        id: REGION_FILL_LAYER, type: 'fill', source: WORLD_SOURCE, filter: regionFilter(null),
        paint: { 'fill-color': SILHOUETTE_COLORS.hint, 'fill-opacity': 0.14 },
      },
      {
        id: REGION_LINE_LAYER, type: 'line', source: WORLD_SOURCE, filter: regionFilter(null),
        layout: { 'line-join': 'round' },
        paint: { 'line-color': SILHOUETTE_COLORS.hint, 'line-width': 0.9, 'line-opacity': 0.7 },
      },
      {
        id: MISSED_FILL_LAYER, type: 'fill', source: WORLD_SOURCE, filter: isoListFilter([]),
        paint: { 'fill-color': SILHOUETTE_COLORS.miss, 'fill-opacity': 0.22 },
      },
      {
        id: MISSED_LINE_LAYER, type: 'line', source: WORLD_SOURCE, filter: isoListFilter([]),
        layout: { 'line-join': 'round' },
        paint: { 'line-color': SILHOUETTE_COLORS.miss, 'line-width': 1.6, 'line-opacity': 0.85 },
      },
      {
        id: HINT_FILL_LAYER, type: 'fill', source: WORLD_SOURCE, filter: isoFilter(null),
        paint: { 'fill-color': SILHOUETTE_COLORS.hint, 'fill-opacity': 0.28 },
      },
      {
        id: HINT_LINE_LAYER, type: 'line', source: WORLD_SOURCE, filter: isoFilter(null),
        layout: { 'line-join': 'round' },
        paint: { 'line-color': SILHOUETTE_COLORS.hint, 'line-width': 2.4, 'line-blur': 0.4 },
      },
      {
        id: FOCUS_FILL_LAYER, type: 'fill', source: WORLD_SOURCE, filter: isoFilter(null),
        paint: { 'fill-color': SILHOUETTE_COLORS.dotStroke, 'fill-opacity': 0.16 },
      },
      {
        id: FOCUS_GLOW_LAYER, type: 'line', source: WORLD_SOURCE, filter: isoFilter(null),
        layout: { 'line-join': 'round' },
        paint: {
          'line-color': SILHOUETTE_COLORS.focus,
          'line-width': ['interpolate', ['linear'], ['zoom'], 1, 1.6, 4, 3.5, 6, 5],
          'line-blur': ['interpolate', ['linear'], ['zoom'], 1, 1.2, 4, 2.5, 6, 3.5],
          'line-opacity': 0.4,
        },
      },
      {
        id: FOCUS_LAYER, type: 'line', source: WORLD_SOURCE, filter: isoFilter(null),
        layout: { 'line-join': 'round' },
        paint: { 'line-color': SILHOUETTE_COLORS.dotStroke, 'line-width': ['interpolate', ['linear'], ['zoom'], 1, 0.5, 5, 1.4] },
      },
      {
        id: HINT_DOT_LAYER, type: 'circle', source: DOT_SOURCE, filter: isoFilter(null),
        paint: {
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, 9, 5, 14],
          'circle-color': SILHOUETTE_COLORS.hint,
          'circle-opacity': 0.25,
          'circle-stroke-color': SILHOUETTE_COLORS.hint,
          'circle-stroke-width': 2,
        },
      },
      {
        id: MISSED_DOT_LAYER, type: 'circle', source: DOT_SOURCE, filter: isoListFilter([]),
        paint: {
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, 5, 5, 9],
          'circle-color': SILHOUETTE_COLORS.miss,
          'circle-opacity': 0.3,
          'circle-stroke-color': SILHOUETTE_COLORS.miss,
          'circle-stroke-width': 1.4,
        },
      },
      {
        id: DOT_LAYER, type: 'circle', source: DOT_SOURCE, filter: isoListFilter([]),
        paint: {
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, 3.4, 5, 6.5],
          'circle-color': ['get', 'color'],
          'circle-stroke-color': ['get', 'edge'],
          'circle-stroke-width': 1.4,
        },
      },
    ],
  }
}

export function tinyCountryDots(countries: SilhouetteCountryOption[]): FeatureCollection<Point> {
  return {
    type: 'FeatureCollection',
    features: countries
      .filter(({ bounds: [west, south, east, north] }) =>
        Math.max(east - west, north - south) < SILHOUETTE_GLOBE.tinyCountrySpanDegrees)
      .map(country => {
        const tones = flagTones(country.colors)
        return {
          type: 'Feature',
          properties: { iso: country.iso, color: tones.fill, edge: tones.edge },
          geometry: { type: 'Point', coordinates: country.center },
        }
      }),
  }
}
