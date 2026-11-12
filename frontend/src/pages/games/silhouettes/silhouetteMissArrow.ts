import type { Map as MapLibreMap } from 'maplibre-gl'
import type { SilhouettePoint } from '@/api/silhouettes'
import { SILHOUETTE_MISS } from './silhouetteGameConstants'

export interface SilhouetteGlobeMiss {
  iso: string
  center: SilhouettePoint
  distanceKm: number
  bearingDeg: number
  label: string
}

export interface SilhouetteMissHead {
  x: number
  y: number
  angle: number
}

export interface SilhouetteMissMark {
  iso: string
  label: string
  latest: boolean
  x: number
  y: number
  path: string | null
  head: SilhouetteMissHead | null
}

const DEGREES_TO_RADIANS = Math.PI / 180
const EARTH_RADIUS_KM = 6371.0088
const WORLD_TILE_PX = 512
const MIN_LATITUDE_SCALE = 0.2
const FULL_TURN_DEGREES = 360
const HALF_TURN_DEGREES = 180
const PATH_DIGITS = 1

export function angularDistance([lng1, lat1]: SilhouettePoint, [lng2, lat2]: SilhouettePoint) {
  const a = lat1 * DEGREES_TO_RADIANS
  const b = lat2 * DEGREES_TO_RADIANS
  const cos = Math.sin(a) * Math.sin(b) + Math.cos(a) * Math.cos(b) * Math.cos((lng2 - lng1) * DEGREES_TO_RADIANS)
  return Math.acos(Math.min(1, Math.max(-1, cos))) / DEGREES_TO_RADIANS
}

function destination([lng, lat]: SilhouettePoint, bearingDeg: number, angleDeg: number): SilhouettePoint {
  const phi = lat * DEGREES_TO_RADIANS
  const lambda = lng * DEGREES_TO_RADIANS
  const theta = bearingDeg * DEGREES_TO_RADIANS
  const delta = angleDeg * DEGREES_TO_RADIANS
  const phi2 = Math.asin(Math.sin(phi) * Math.cos(delta) + Math.cos(phi) * Math.sin(delta) * Math.cos(theta))
  const lambda2 = lambda + Math.atan2(Math.sin(theta) * Math.sin(delta) * Math.cos(phi), Math.cos(delta) - Math.sin(phi) * Math.sin(phi2))
  const wrapped = ((lambda2 / DEGREES_TO_RADIANS + HALF_TURN_DEGREES) % FULL_TURN_DEGREES + FULL_TURN_DEGREES) % FULL_TURN_DEGREES - HALF_TURN_DEGREES
  return [wrapped, phi2 / DEGREES_TO_RADIANS]
}

export function missMark(map: MapLibreMap, miss: SilhouetteGlobeMiss, latest: boolean): SilhouetteMissMark | null {
  const view = map.getCenter()
  const viewPoint: SilhouettePoint = [view.lng, view.lat]
  if (angularDistance(viewPoint, miss.center) > SILHOUETTE_MISS.visibleAngle) return null
  const start = map.project(miss.center)
  const latitudeScale = Math.max(MIN_LATITUDE_SCALE, Math.cos(view.lat * DEGREES_TO_RADIANS))
  const pxPerDegree = WORLD_TILE_PX * 2 ** map.getZoom() / FULL_TURN_DEGREES / latitudeScale
  const distanceDeg = miss.distanceKm / EARTH_RADIUS_KM / DEGREES_TO_RADIANS
  const lengthDeg = Math.min(
    Math.max(distanceDeg * SILHOUETTE_MISS.share, SILHOUETTE_MISS.minPx / pxPerDegree),
    SILHOUETTE_MISS.maxPx / pxPerDegree,
    SILHOUETTE_MISS.maxDegrees,
  )
  const points: { x: number, y: number }[] = []
  for (let index = 0; index <= SILHOUETTE_MISS.samples; index++) {
    const point = destination(miss.center, miss.bearingDeg, lengthDeg * index / SILHOUETTE_MISS.samples)
    if (angularDistance(viewPoint, point) > SILHOUETTE_MISS.visibleAngle) break
    points.push(map.project(point))
  }
  const mark = { iso: miss.iso, label: miss.label, latest, x: start.x, y: start.y }
  if (points.length < 2) return { ...mark, path: null, head: null }
  const end = points[points.length - 1]
  const before = points[points.length - 2]
  return {
    ...mark,
    path: points.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(PATH_DIGITS)} ${point.y.toFixed(PATH_DIGITS)}`).join(' '),
    head: { x: end.x, y: end.y, angle: Math.atan2(end.y - before.y, end.x - before.x) / DEGREES_TO_RADIANS },
  }
}
