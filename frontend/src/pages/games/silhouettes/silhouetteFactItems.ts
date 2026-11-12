import { Car, Landmark, Mountain, Ruler, Sparkles, Users, Waves, type LucideIcon } from 'lucide-react'
import type { SilhouetteFacts as Facts, SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'

type FactsCopy = TranslationKeys['silhouetteGame']['facts']

export interface FactStat {
  key: string
  icon: LucideIcon
  label: string
  value: string
}

export interface FactNote {
  key: string
  icon: LucideIcon
  text: string
  notable: boolean
}

const SMALL_AREA_KM2 = 10
const SMALL_AREA_DIGITS = 2

export function factStats(copy: FactsCopy, facts: Facts, language: SilhouetteLocale): FactStat[] {
  const population = new Intl.NumberFormat(language, { notation: 'compact', maximumFractionDigits: 1 })
  const elevation = new Intl.NumberFormat(language)
  const area = (value: number) => new Intl.NumberFormat(language, {
    maximumFractionDigits: value < SMALL_AREA_KM2 ? SMALL_AREA_DIGITS : 0,
  }).format(value)
  return [
    facts.capital && { key: 'capital', icon: Landmark, label: copy.capital, value: facts.capital[language] },
    facts.population && { key: 'population', icon: Users, label: copy.population, value: population.format(facts.population.value) },
    facts.areaKm2 && { key: 'area', icon: Ruler, label: copy.area, value: interpolate(copy.areaValue, { value: area(facts.areaKm2.value) }) },
    facts.highestPoint && {
      key: 'peak', icon: Mountain, label: copy.highestPoint,
      value: interpolate(copy.elevationValue, { name: facts.highestPoint.name[language], value: elevation.format(facts.highestPoint.elevationM) }),
    },
  ].filter((item): item is FactStat => Boolean(item))
}

export function factNotes(copy: FactsCopy, facts: Facts): FactNote[] {
  const rank = facts.areaRank
    ? interpolate(facts.areaRank.direction === 'LARGEST' ? copy.areaLargest : copy.areaSmallest, { rank: facts.areaRank.rank })
    : facts.populationRank
      ? interpolate(facts.populationRank.direction === 'LARGEST' ? copy.populationLargest : copy.populationSmallest, { rank: facts.populationRank.rank })
      : null
  return [
    rank && { key: 'rank', icon: Sparkles, text: rank, notable: true },
    facts.landlocked && { key: 'landlocked', icon: Waves, text: copy.landlocked, notable: false },
    facts.drivesLeft && { key: 'left', icon: Car, text: copy.drivesLeft, notable: false },
  ].filter((item): item is FactNote => Boolean(item))
}
