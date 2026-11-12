import { Vector3 } from 'three'

export type GeoPoint = readonly [number, number]

const RADIANS = Math.PI / 180

export function globeNormal([longitude, latitude]: GeoPoint) {
  const lon = longitude * RADIANS
  const lat = latitude * RADIANS
  return new Vector3(
    Math.cos(lat) * Math.cos(lon),
    Math.sin(lat),
    -Math.cos(lat) * Math.sin(lon),
  ).normalize()
}

export function globePoint(point: GeoPoint, radius: number) {
  return globeNormal(point).multiplyScalar(radius)
}

export function betweenOnGlobe(from: GeoPoint, to: GeoPoint, share: number) {
  const start = globeNormal(from)
  const end = globeNormal(to)
  const angle = start.angleTo(end)
  if (angle < 0.0001) return start
  const sine = Math.sin(angle)
  return start.multiplyScalar(Math.sin((1 - share) * angle) / sine)
    .addScaledVector(end, Math.sin(share * angle) / sine).normalize()
}

export function easeJourney(share: number) {
  return share * share * (3 - 2 * share)
}
