import { CatmullRomCurve3, Vector3 } from 'three'
import type { SilhouetteWorld } from '../silhouettes/silhouetteWorld'
import { betweenOnGlobe, globeNormal, type GeoPoint } from './quizGlobeMath'

const BORDER_SEARCH_DEGREES = 1.5
const DEGREES = Math.PI / 180
const HALF = 0.5
const CURVE_TYPE = 'centripetal'

export interface QuizGlobePath {
  normalAt: (share: number) => Vector3
}

type Box = readonly [number, number, number, number]

const outlines = new WeakMap<SilhouetteWorld, Map<string, GeoPoint[]>>()
const crossings = new WeakMap<SilhouetteWorld, Map<string, GeoPoint | null>>()

function outline(world: SilhouetteWorld, iso: string): GeoPoint[] {
  let byIso = outlines.get(world)
  if (!byIso) {
    byIso = new Map()
    outlines.set(world, byIso)
  }
  const cached = byIso.get(iso)
  if (cached) return cached
  const feature = world.collection.features.find(candidate => candidate.properties.iso === iso)
  const points = feature
    ? feature.geometry.coordinates.flatMap(polygon => polygon.flatMap(ring => ring.map(([longitude, latitude]) => [longitude, latitude] as const)))
    : []
  byIso.set(iso, points)
  return points
}

function boxOf(points: GeoPoint[]): Box {
  return points.reduce<Box>(([west, south, east, north], [longitude, latitude]) =>
    [Math.min(west, longitude), Math.min(south, latitude), Math.max(east, longitude), Math.max(north, latitude)],
  [Infinity, Infinity, -Infinity, -Infinity])
}

function near([west, south, east, north]: Box) {
  return ([longitude, latitude]: GeoPoint) =>
    longitude >= west - BORDER_SEARCH_DEGREES && longitude <= east + BORDER_SEARCH_DEGREES
    && latitude >= south - BORDER_SEARCH_DEGREES && latitude <= north + BORDER_SEARCH_DEGREES
}

function borderPoint(world: SilhouetteWorld, from: string, to: string): GeoPoint | null {
  let byPair = crossings.get(world)
  if (!byPair) {
    byPair = new Map()
    crossings.set(world, byPair)
  }
  const key = `${from}|${to}`
  if (!byPair.has(key)) byPair.set(key, nearestCrossing(world, from, to))
  return byPair.get(key) ?? null
}

function nearestCrossing(world: SilhouetteWorld, from: string, to: string): GeoPoint | null {
  const first = outline(world, from)
  const second = outline(world, to)
  if (!first.length || !second.length) return null
  const firstNear = first.filter(near(boxOf(second)))
  const secondNear = second.filter(near(boxOf(first)))
  let best: { distance: number, point: GeoPoint } | null = null
  for (const [firstLongitude, firstLatitude] of firstNear) {
    const scale = Math.cos(firstLatitude * DEGREES)
    for (const [secondLongitude, secondLatitude] of secondNear) {
      const distance = ((firstLongitude - secondLongitude) * scale) ** 2 + (firstLatitude - secondLatitude) ** 2
      if (!best || distance < best.distance) {
        best = {
          distance,
          point: [(firstLongitude + secondLongitude) * HALF, (firstLatitude + secondLatitude) * HALF],
        }
      }
    }
  }
  return best?.point ?? null
}

export function landWaypoints(world: SilhouetteWorld | null, from: GeoPoint, fromIso: string, route: string[], toIso: string, to: GeoPoint) {
  if (!world) return [from, to]
  const chain = [fromIso, ...route, toIso]
  const borders = chain.slice(1).flatMap((iso, index) => {
    const point = borderPoint(world, chain[index], iso)
    return point ? [point] : []
  })
  return [from, ...borders, to]
}

export function globePath(waypoints: GeoPoint[]): QuizGlobePath {
  if (waypoints.length <= 2) {
    const [from, to] = [waypoints[0], waypoints[waypoints.length - 1]]
    return { normalAt: share => betweenOnGlobe(from, to, share) }
  }
  const curve = new CatmullRomCurve3(waypoints.map(point => globeNormal(point)), false, CURVE_TYPE)
  return { normalAt: share => curve.getPointAt(Math.min(1, Math.max(0, share))).normalize() }
}
