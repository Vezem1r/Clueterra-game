import type { SilhouetteNames } from '@/api/silhouettes'
import type { CountryPool } from '../silhouettes/silhouetteWorld'
import {
  FIRST_RING_RADIUS,
  FLAG_PLAZA_REGIONS,
  type FlagPlazaRegionKey,
  PLAZA_MARGIN,
  RING_GAP,
  SECTOR_GAP_RADIANS,
  SLOT_SPACING,
} from './flagPlazaConstants'

export interface FlagPlazaCountry {
  iso: string
  names: SilhouetteNames
  region: FlagPlazaRegionKey
}

export interface FlagPlazaSlot {
  index: number
  country: FlagPlazaCountry
  x: number
  z: number
  angle: number
}

export interface FlagPlazaSector {
  region: FlagPlazaRegionKey
  start: number
  length: number
  innerRadius: number
  outerRadius: number
  slotIndexes: number[]
}

export interface FlagPlazaLayout {
  slots: FlagPlazaSlot[]
  sectors: FlagPlazaSector[]
  radius: number
}

const REGION_MATCHERS: Array<[RegExp, FlagPlazaRegionKey]> = [
  [/Europe/, 'europe'],
  [/Africa/, 'africa'],
  [/Asia/, 'asia'],
  [/America|Caribbean/, 'americas'],
]
const FULL_TURN = Math.PI * 2
const SEED_MULTIPLIER = 31
const UNSIGNED_RANGE = 4_294_967_296
const HASH_MIX = 2_246_822_507
const HASH_SHIFT_HIGH = 15
const HASH_SHIFT_LOW = 13

export function regionOfSubregion(subregion: string | undefined): FlagPlazaRegionKey {
  return REGION_MATCHERS.find(([pattern]) => pattern.test(subregion ?? ''))?.[1] ?? 'oceania'
}

export function plazaCountries(pool: CountryPool): FlagPlazaCountry[] {
  return pool.countries.map(({ iso, names, subregion }) => ({ iso, names, region: regionOfSubregion(subregion) }))
}

export function plazaPoint(radius: number, angle: number): [number, number] {
  return [radius * Math.cos(angle), -radius * Math.sin(angle)]
}

export function layoutFlagPlaza(countries: FlagPlazaCountry[]): FlagPlazaLayout {
  const groups = FLAG_PLAZA_REGIONS
    .map(region => ({
      region: region.key,
      countries: countries
        .filter(country => country.region === region.key)
        .sort((left, right) => left.names.en.localeCompare(right.names.en)),
    }))
    .filter(group => group.countries.length > 0)
  const available = FULL_TURN - SECTOR_GAP_RADIANS * groups.length
  const slots: FlagPlazaSlot[] = []
  const sectors: FlagPlazaSector[] = []
  let start = SECTOR_GAP_RADIANS / 2
  let outermost = FIRST_RING_RADIUS
  groups.forEach(group => {
    const length = available * group.countries.length / countries.length
    const slotIndexes: number[] = []
    let placed = 0
    let ring = 0
    while (placed < group.countries.length) {
      const radius = FIRST_RING_RADIUS + ring * RING_GAP
      const capacity = Math.max(1, Math.floor(radius * length / SLOT_SPACING))
      const count = Math.min(capacity, group.countries.length - placed)
      const step = length / capacity
      const offset = start + (length - step * (count - 1)) / 2
      for (let position = 0; position < count; position++) {
        const angle = offset + step * position
        const [x, z] = plazaPoint(radius, angle)
        slotIndexes.push(slots.length)
        slots.push({ index: slots.length, country: group.countries[placed + position], x, z, angle })
      }
      placed += count
      outermost = Math.max(outermost, radius)
      ring++
    }
    sectors.push({
      region: group.region,
      start,
      length,
      innerRadius: FIRST_RING_RADIUS,
      outerRadius: FIRST_RING_RADIUS + (ring - 1) * RING_GAP,
      slotIndexes,
    })
    start += length + SECTOR_GAP_RADIANS
  })
  return { slots, sectors, radius: outermost + PLAZA_MARGIN }
}

export function seededShare(key: string, seed: number): number {
  let hash = seed
  for (const character of key) hash = Math.imul(hash ^ character.charCodeAt(0), SEED_MULTIPLIER) + seed
  hash ^= hash >>> HASH_SHIFT_HIGH
  hash = Math.imul(hash, HASH_MIX)
  hash ^= hash >>> HASH_SHIFT_LOW
  return (hash >>> 0) / UNSIGNED_RANGE
}

export function facingOutward(angle: number): number {
  return angle + Math.PI / 2
}
