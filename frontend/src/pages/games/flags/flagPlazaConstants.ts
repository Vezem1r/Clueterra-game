export type FlagPlazaRegionKey = 'europe' | 'asia' | 'africa' | 'americas' | 'oceania'

export interface FlagPlazaRegion {
  key: FlagPlazaRegionKey
  color: string
}

export const FLAG_PLAZA_REGIONS: FlagPlazaRegion[] = [
  { key: 'europe', color: '#60a5fa' },
  { key: 'asia', color: '#f87171' },
  { key: 'africa', color: '#fbbf24' },
  { key: 'americas', color: '#34d399' },
  { key: 'oceania', color: '#c084fc' },
]

export const POLE_HEIGHT = 8
export const POLE_RADIUS = 0.055
export const POLE_BASE_SIZE = 0.46
export const POLE_FINIAL_RADIUS = 0.11
export const FLAG_WIDTH = 2.2
export const FLAG_HEIGHT = FLAG_WIDTH * 0.75
export const FLAG_TOP_GAP = 0.16
export const FLAG_TOP_Y = POLE_HEIGHT - FLAG_TOP_GAP
export const FLAG_LOWERED_Y = 1.6
export const FLAG_SEGMENTS_X = 20
export const FLAG_SEGMENTS_Y = 8
export const PLAQUE_SIZE: [number, number, number] = [0.5, 0.22, 0.06]
export const GLOW_RADIUS = 1.1
export const BEAM_RADIUS = 0.75
export const HIT_BOX_SIZE = 0.7

export const FIRST_RING_RADIUS = 16
export const RING_GAP = 4.2
export const SLOT_SPACING = 3.1
export const SECTOR_GAP_RADIANS = 0.16
export const SECTOR_PADDING = 1.7
export const PLAZA_MARGIN = 9

export const ATLAS_COLUMNS = 16
export const ATLAS_CELL_WIDTH = 192
export const ATLAS_CELL_HEIGHT = 144
export const ATLAS_PADDING = 3
export const ATLAS_ANISOTROPY = 4

export const RAISE_SECONDS = 3
export const OVERVIEW_CAMERA: [number, number, number] = [0, 34, 74]
export const OVERVIEW_TARGET_HEIGHT = 2
export const REGION_CAMERA_DISTANCE = 22
export const REGION_TARGET_HEIGHT = 1
export const SLOT_CAMERA_DISTANCE = 12
export const SLOT_CAMERA_HEIGHT = 9.5
export const CAMERA_FLOOR = 1.2
export const WIDE_ASPECT = 1.3
export const MAX_NARROW_ZOOM_OUT = 2.2
export const MIN_CAMERA_DISTANCE = 12
export const MAX_CAMERA_DISTANCE = 95
export const MIN_POLAR_ANGLE = 0.3
export const MAX_POLAR_ANGLE = 1.42
export const MAX_DEVICE_PIXEL_RATIO = 1.5
export const STAR_COUNT = 900
export const STAR_RADIUS = 260
export const SKY_RADIUS = 300
export const HORIZON_COLOR = '#16233a'
export const ZENITH_COLOR = '#03050b'
export const FOG_COLOR = HORIZON_COLOR
export const FOG_NEAR = 55
export const FOG_FAR = 175
export const MEADOW_RADIUS = 360
export const MOON_DIRECTION: [number, number, number] = [-0.55, 0.32, -0.77]
export const TREE_COUNT = 260
export const TREE_BELT_START = 104
export const TREE_BELT_DEPTH = 64
export const BUSH_COUNT = 170
export const BUSH_BELT_START = 3
export const BUSH_BELT_DEPTH = 58
export const LABEL_DISTANCE = 4.2
export const LABEL_HEIGHT = 1.9
