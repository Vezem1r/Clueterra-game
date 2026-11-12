import type { RiverCourse } from '@/api/rivers'
import {
  RIVER_DESKTOP_MIN_WIDTH_PX,
  RIVER_FIT_MARGIN_PX,
  RIVER_FIT_MAX_ZOOM,
  RIVER_FIT_MOBILE_MARGIN_PX,
  RIVER_GLOBE_MIN_ZOOM,
  RIVER_MAP_TILE_SIZE,
} from './riverGlobeConstants'

export interface RiverPadding {
  top: number
  right: number
  bottom: number
  left: number
}

export interface RiverViewport {
  width: number
  height: number
  padding: RiverPadding
}

export interface RiverCamera {
  center: [number, number]
  zoom: number
}

const DEGREES_IN_CIRCLE = 360
const HALF_CIRCLE = 180
const DEGREES_TO_RADIANS = Math.PI / HALF_CIRCLE
const QUARTER_TURN = Math.PI / 4
const MIN_EXTENT = 1e-6
const MIN_AVAILABLE_PX = 1

export function projectMercator(lng: number, lat: number): [number, number] {
  return [
    (lng + HALF_CIRCLE) / DEGREES_IN_CIRCLE,
    (1 - Math.log(Math.tan(QUARTER_TURN + lat * DEGREES_TO_RADIANS / 2)) / Math.PI) / 2,
  ]
}

function unprojectMercator(x: number, y: number): [number, number] {
  const lat = (2 * Math.atan(Math.exp(Math.PI * (1 - 2 * y))) - Math.PI / 2) / DEGREES_TO_RADIANS
  return [x * DEGREES_IN_CIRCLE - HALF_CIRCLE, lat]
}

export function fitRiverCamera(course: RiverCourse, viewport: RiverViewport, zoomOffset = 0): RiverCamera {
  const [west, south, east, north] = course.bbox
  const [left, top] = projectMercator(west, north)
  const [right, bottom] = projectMercator(east, south)
  const { padding } = viewport
  const margin = viewport.width >= RIVER_DESKTOP_MIN_WIDTH_PX ? RIVER_FIT_MARGIN_PX : RIVER_FIT_MOBILE_MARGIN_PX
  const width = Math.max(MIN_AVAILABLE_PX, viewport.width - padding.left - padding.right - margin * 2)
  const height = Math.max(MIN_AVAILABLE_PX, viewport.height - padding.top - padding.bottom - margin * 2)
  const scale = Math.min(
    width / (Math.max(MIN_EXTENT, right - left) * RIVER_MAP_TILE_SIZE),
    height / (Math.max(MIN_EXTENT, bottom - top) * RIVER_MAP_TILE_SIZE),
  )
  const zoom = Math.max(RIVER_GLOBE_MIN_ZOOM, Math.min(RIVER_FIT_MAX_ZOOM, Math.log2(scale))) + zoomOffset
  return { center: unprojectMercator((left + right) / 2, (top + bottom) / 2), zoom }
}
