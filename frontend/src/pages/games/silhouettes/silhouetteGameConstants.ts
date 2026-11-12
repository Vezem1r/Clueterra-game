import { Camera, Compass, Landmark, LocateFixed, type LucideIcon } from 'lucide-react'
import type { SilhouetteClueKind } from '@/api/silhouettes'

export const SILHOUETTE_CANONICAL_PATH = '/games/country-silhouettes'
export const SILHOUETTE_GUIDE_PATH = '/games/country-silhouettes/how-to'
export const SILHOUETTE_GUIDE_QUERY = 'silhouettes'
export const SILHOUETTE_SHARE_IMAGE = '/images/share/silhouettes.jpg'
export const SILHOUETTE_GLOBE_EXAMPLE = { src: '/images/guides/silhouettes/globe.webp', width: 1400, height: 967 }
export const SILHOUETTE_ROUND_EXAMPLE = { src: '/images/guides/silhouettes/round.webp', width: 1600, height: 920 }
export const SILHOUETTE_SHAPE_EXAMPLE = { src: '/images/guides/silhouettes/step-shape.webp', width: 832, height: 840 }
export const SILHOUETTE_NAME_EXAMPLE = { src: '/images/guides/silhouettes/step-name.webp', width: 832, height: 336 }
export const SILHOUETTE_CLUES_EXAMPLE = { src: '/images/guides/silhouettes/step-clues.webp', width: 832, height: 648 }
export const SILHOUETTE_SEO_KEYWORDS =
  'guess the country by shape, country silhouette quiz, country outline game, country shapes quiz, guess the country outline, world map quiz'
export const SILHOUETTE_VIEWBOX = '0 0 1000 1000'
export const SILHOUETTE_PERCENT = 100
export const flagUrl = (iso: string) => `/api/assets/flags/${iso.toLowerCase()}.svg`

export const SILHOUETTE_HINT_ICONS: Record<SilhouetteClueKind, LucideIcon> = {
  REGION: Compass,
  LOCATION: LocateFixed,
  CAPITAL: Landmark,
  LANDMARK: Camera,
}

export const SILHOUETTE_SUGGESTION_LIMIT = 6
export const SILHOUETTE_COMPASS_SECTOR_DEGREES = 45

export const SILHOUETTE_TIER_BADGE: Record<number, string> = {
  1: 'bg-emerald-300/10 text-emerald-200 ring-emerald-300/25',
  2: 'bg-teal-300/10 text-teal-200 ring-teal-300/25',
  3: 'bg-amber-300/10 text-amber-200 ring-amber-300/25',
  4: 'bg-orange-300/10 text-orange-200 ring-orange-300/25',
  5: 'bg-rose-300/10 text-rose-200 ring-rose-300/25',
}

export const SILHOUETTE_COLORS = {
  space: '#04070d',
  ocean: '#0a1626',
  sky: '#071223',
  horizon: '#1b4a6b',
  fog: '#0b1a2e',
  land: '#1d2b3b',
  territory: '#17212d',
  border: '#3c5268',
  focus: '#a5f3fc',
  hint: '#67e8f9',
  miss: '#fb7185',
  dotStroke: '#f8fafc',
  shape: '#f1f5f9',
  shapeSolved: '#a5f3fc',
} as const

export const SILHOUETTE_STROKES = { shape: 1.25 } as const

export const SILHOUETTE_HOVER = {
  intentMs: 220,
  hideMs: 450,
  returnMs: 1200,
  maxSpeedPxPerMs: 0.35,
} as const

export const SILHOUETTE_MISS = {
  share: 0.45,
  minPx: 56,
  maxPx: 132,
  samples: 16,
  maxDegrees: 30,
  visibleAngle: 80,
  revealAngle: 55,
  pinDotOffsetPx: 4,
  arrowFrom: '#fb7185',
  arrowTo: '#fcd34d',
} as const

export const SILHOUETTE_GLOBE = {
  idleCenter: [18, 24] as [number, number],
  idleZoom: 1.55,
  minZoom: 0.8,
  maxZoom: 7,
  regionZoom: 2.3,
  fitMaxZoom: 5.4,
  countrySpread: 1.9,
  locationSpread: 1.7,
  spinDegreesPerSecond: 4,
  resumeSpinMs: 5000,
  flightMs: 2200,
  fitMs: 1600,
  maxPixelRatio: 2,
  tinyCountrySpanDegrees: 1.2,
  patternSize: 24,
  patternPixelRatio: 2,
} as const

export type SilhouettePaintMode = 'FLAG' | 'STRIPES'
export const SILHOUETTE_PAINT_MODES: SilhouettePaintMode[] = ['FLAG', 'STRIPES']
export const SILHOUETTE_DEFAULT_PAINT_MODE: SilhouettePaintMode = 'FLAG'
export const SILHOUETTE_PAINT_STORAGE_KEY = 'clueterra.silhouettes.paintMode'
