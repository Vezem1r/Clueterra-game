export const RIVER_BORDERS_URL = '/maps/country-borders.json'

export const RIVER_RELIEF_TILES = '/tiles/relief/{z}/{y}/{x}.jpeg'
export const RIVER_TERRAIN_TILES = '/tiles/terrain/{z}/{x}/{y}.webp'
export const RIVER_RELIEF_MAX_ZOOM = 8
export const RIVER_TERRAIN_MAX_ZOOM = 10
export const RIVER_RELIEF_TILE_SIZE = 256
export const RIVER_TERRAIN_TILE_SIZE = 1024
export const RIVER_MAP_TILE_SIZE = 512
export const RIVER_HILLSHADE_MIN_ZOOM = 3
export const RIVER_MAX_PIXEL_RATIO = 2
export const RIVER_PREFETCH_TILE_LIMIT = 48
export const RIVER_RELIEF_ATTRIBUTION = '<a href="https://www.earthdata.nasa.gov/data/tools/gibs" target="_blank" rel="noopener noreferrer">NASA GIBS Blue Marble</a>'
export const RIVER_TERRAIN_ATTRIBUTION = '<a href="https://mapterhorn.com/attribution" target="_blank" rel="noopener noreferrer">© Mapterhorn</a>'
export const RIVER_VECTOR_ATTRIBUTION = '<a href="https://www.naturalearthdata.com" target="_blank" rel="noopener noreferrer">Natural Earth</a>'

export const RIVER_GLOBE_MIN_ZOOM = 0.6
export const RIVER_GLOBE_MAX_ZOOM = 9.5
export const RIVER_GLOBE_IDLE_ZOOM = 1.9
export const RIVER_GLOBE_IDLE_CENTER: [number, number] = [-58, -6]
export const RIVER_GLOBE_SPIN_DEGREES_PER_SECOND = 3
export const RIVER_ORBIT_DEGREES_PER_SECOND = 4
export const RIVER_ORBIT_RESUME_MS = 4000
export const RIVER_MS_PER_SECOND = 1000
export const RIVER_FLIGHT_CURVE = 1.6
export const RIVER_FLIGHT_MS = 2600
export const RIVER_FIT_DURATION_MS = 1400
export const RIVER_FIT_MAX_ZOOM = 7.2
export const RIVER_FIT_MARGIN_PX = 40
export const RIVER_FIT_MOBILE_MARGIN_PX = 12
export const RIVER_ZOOM_STEP_DURATION_MS = 300
export const RIVER_DRAW_DURATION_MS = 1600
export const RIVER_DRAW_EDGE = 0.04
export const RIVER_DRAW_HEAD = 0.06
export const RIVER_FLARE_OPACITY = 0.85
export const RIVER_FLARE_HOLD_MS = 700
export const RIVER_FLARE_FADE_MS = 1400
export const RIVER_TERRAIN_EXAGGERATION = 1.8
export const RIVER_TERRAIN_PITCH = 52
export const RIVER_REVEAL_PITCH = 58
export const RIVER_REVEAL_BEARING = -24
export const RIVER_REVEAL_ZOOM_OFFSET = 0.55
export const RIVER_REVEAL_DIVE_MS = 2600
export const RIVER_BORDER_OPACITY = 0.55
export const RIVER_PANEL_GAP_PX = 24
export const RIVER_CONTROLS_WIDTH_PX = 72
export const RIVER_DESKTOP_MIN_WIDTH_PX = 1024
export const RIVER_DESKTOP_QUERY = `(min-width: ${RIVER_DESKTOP_MIN_WIDTH_PX}px)`
export const RIVER_MOBILE_CONTROLS_GAP_PX = 8
export const RIVER_MOBILE_ATTRIBUTION_SLOT_PX = 62
export const RIVER_ATTRIBUTION_ZONE_PX = 64

export const RIVER_COLORS = {
  space: '#03050b',
  ocean: '#0a1b2e',
  horizon: '#1d4e7a',
  sky: '#06101f',
  fog: '#0b1830',
  glow: '#22d3ee',
  halo: '#67e8f9',
  core: '#ecfeff',
  border: '#f8fafc',
  shadow: '#02040a',
  highlight: '#ffffff',
} as const

export const RIVER_STARFIELD = [
  'radial-gradient(1px 1px at 12% 18%, rgba(255,255,255,.55), transparent)',
  'radial-gradient(1px 1px at 68% 9%, rgba(255,255,255,.4), transparent)',
  'radial-gradient(1.5px 1.5px at 33% 72%, rgba(186,230,253,.5), transparent)',
  'radial-gradient(1px 1px at 84% 56%, rgba(255,255,255,.35), transparent)',
  'radial-gradient(1px 1px at 47% 38%, rgba(255,255,255,.3), transparent)',
  'radial-gradient(1.5px 1.5px at 91% 88%, rgba(255,255,255,.45), transparent)',
  'radial-gradient(1px 1px at 6% 93%, rgba(186,230,253,.4), transparent)',
].join(', ')
export const RIVER_STARFIELD_SIZE = '420px 420px'
