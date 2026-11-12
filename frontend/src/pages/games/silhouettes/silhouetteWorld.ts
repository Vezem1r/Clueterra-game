import { useEffect, useState } from 'react'
import type { FeatureCollection, MultiPolygon } from 'geojson'
import type { SilhouetteBounds, SilhouetteFlagColor, SilhouetteLocale, SilhouetteNames, SilhouettePoint } from '@/api/silhouettes'
import { SILHOUETTE_SUGGESTION_LIMIT } from './silhouetteGameConstants'
import { SILHOUETTE_WORLD_URL } from './mapAssets'

export interface SilhouetteWorldProperties {
  iso: string | null
  pool: boolean
  subregion?: string
  names?: SilhouetteNames
  aliases?: string[]
  center?: SilhouettePoint
  bounds?: SilhouetteBounds
  colors?: SilhouetteFlagColor[]
}

export type SilhouetteWorldCollection = FeatureCollection<MultiPolygon, SilhouetteWorldProperties>

export interface SilhouetteCountryOption {
  iso: string
  names: SilhouetteNames
  subregion?: string
  center: SilhouettePoint
  bounds: SilhouetteBounds
  colors: SilhouetteFlagColor[]
  keys: string[]
}

export interface CountryPool {
  countries: SilhouetteCountryOption[]
  byIso: Map<string, SilhouetteCountryOption>
}

export interface SilhouetteWorld extends CountryPool {
  collection: SilhouetteWorldCollection
}

const DIACRITICS = /[̀-ͯ]/g
const SEPARATORS = /[^\p{L}\p{N}]+/gu

export function normalizeCountryText(value: string) {
  return value.normalize('NFKD').replace(DIACRITICS, '').toLocaleLowerCase().replace(SEPARATORS, ' ').trim()
}

export function toCountryPool(properties: SilhouetteWorldProperties[]): CountryPool {
  const countries = properties.flatMap(({ iso, pool, names, aliases, subregion, center, bounds, colors }) => {
    if (!pool || !iso || !names || !center || !bounds || !colors) return []
    const keys = [...new Set([...Object.values(names), ...(aliases ?? [])].map(normalizeCountryText))]
    return [{ iso, names, subregion, center, bounds, colors, keys }]
  })
  return { countries, byIso: new Map(countries.map(country => [country.iso, country])) }
}

function toWorld(collection: SilhouetteWorldCollection): SilhouetteWorld {
  return { collection, ...toCountryPool(collection.features.map(feature => feature.properties)) }
}

let worldRequest: Promise<SilhouetteWorld> | null = null

export function loadSilhouetteWorld() {
  worldRequest ??= fetch(SILHOUETTE_WORLD_URL)
    .then(response => {
      if (!response.ok) throw new Error(`World map failed: ${response.status}`)
      return response.json() as Promise<SilhouetteWorldCollection>
    })
    .then(toWorld)
    .catch(error => {
      worldRequest = null
      throw error
    })
  return worldRequest
}

export function useSilhouetteWorld() {
  const [world, setWorld] = useState<SilhouetteWorld | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    loadSilhouetteWorld()
      .then(loaded => {
        if (!cancelled) setWorld(loaded)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { world, failed }
}

function matchScore(country: SilhouetteCountryOption, query: string, locale: SilhouetteLocale) {
  const own = normalizeCountryText(country.names[locale])
  if (own === query) return 0
  if (own.startsWith(query)) return 1
  if (country.keys.some(key => key === query)) return 2
  if (country.keys.some(key => key.startsWith(query))) return 3
  if (country.keys.some(key => key.split(' ').some(word => word.startsWith(query)))) return 4
  return Number.POSITIVE_INFINITY
}

export function searchCountries(world: CountryPool | null, text: string, locale: SilhouetteLocale) {
  const query = normalizeCountryText(text)
  if (!world || !query) return []
  return world.countries
    .map(country => ({ country, score: matchScore(country, query, locale) }))
    .filter(entry => Number.isFinite(entry.score))
    .sort((a, b) => a.score - b.score || a.country.names[locale].localeCompare(b.country.names[locale], locale))
    .slice(0, SILHOUETTE_SUGGESTION_LIMIT)
    .map(entry => entry.country)
}
