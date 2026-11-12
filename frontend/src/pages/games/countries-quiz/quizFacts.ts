import type { QuizFact, QuizMetric, QuizOption, QuizQuestion, QuizStop } from '@/api/quiz'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import type { CountryPool } from '../silhouettes/silhouetteWorld'

type QuizCopy = TranslationKeys['quizGame']

const SMALL_AREA_KM2 = 10
const SMALL_AREA_DIGITS = 2
const COMPACT_DIGITS = 1
const DECIMAL_DIGITS = 1
const LIST_SEPARATOR = ', '
const DOLLARS = 'USD'

export function formatPopulation(value: number, language: SilhouetteLocale) {
  return new Intl.NumberFormat(language, { notation: 'compact', maximumFractionDigits: COMPACT_DIGITS }).format(value)
}

function formatAreaNumber(value: number, language: SilhouetteLocale) {
  const digits = value < SMALL_AREA_KM2 ? SMALL_AREA_DIGITS : 0
  return new Intl.NumberFormat(language, { maximumFractionDigits: digits }).format(value)
}

export function formatMetric(copy: QuizCopy, metric: QuizMetric, value: number, language: SilhouetteLocale) {
  const whole = new Intl.NumberFormat(language, { maximumFractionDigits: 0 })
  const decimal = new Intl.NumberFormat(language, { maximumFractionDigits: DECIMAL_DIGITS })
  const units = copy.metricUnits
  switch (metric) {
    case 'POPULATION':
    case 'TOURISTS':
      return formatPopulation(value, language)
    case 'AREA':
      return interpolate(units.AREA, { value: formatAreaNumber(value, language) })
    case 'PEAK':
      return interpolate(units.PEAK, { value: whole.format(value) })
    case 'COASTLINE':
      return interpolate(units.COASTLINE, { value: whole.format(value) })
    case 'LIFE_EXPECTANCY':
    case 'MEDIAN_AGE':
      return interpolate(units.YEARS, { value: decimal.format(value) })
    case 'WEALTH':
      return new Intl.NumberFormat(language, { style: 'currency', currency: DOLLARS, maximumFractionDigits: 0 }).format(value)
    case 'HERITAGE':
      return whole.format(value)
    case 'FOREST':
      return interpolate(units.PERCENT, { value: decimal.format(value) })
  }
}

export function countryName(pool: CountryPool | null, iso: string, language: SilhouetteLocale) {
  return pool?.byIso.get(iso)?.names[language] ?? iso
}

export function optionCountry(option: QuizOption, pool: CountryPool | null, language: SilhouetteLocale) {
  if (option.names) return option.names[language]
  return option.countryCode ? countryName(pool, option.countryCode, language) : null
}

export function optionLabel(option: QuizOption, pool: CountryPool | null, language: SilhouetteLocale) {
  if (option.type === 'NUMBER' && option.value != null) return new Intl.NumberFormat(language).format(option.value)
  if (option.type === 'TEXT') return option.text ?? null
  return optionCountry(option, pool, language)
}

export function optionValue(copy: QuizCopy, fact: QuizFact | undefined, index: number, language: SilhouetteLocale) {
  if (fact?.type !== 'VALUES') return null
  const value = fact.values[index]
  return value == null ? null : formatMetric(copy, fact.metric, value, language)
}

export function factText(copy: QuizCopy, regions: Record<string, string>, fact: QuizFact, country: string,
  language: SilhouetteLocale, pool: CountryPool | null) {
  const facts = copy.facts
  switch (fact.type) {
    case 'POPULATION':
      return interpolate(fact.year ? facts.POPULATION_YEAR : facts.POPULATION,
        { value: formatPopulation(fact.value, language), country, year: fact.year ?? '' })
    case 'AREA':
      return interpolate(facts.AREA, { value: formatAreaNumber(fact.value, language), country })
    case 'HIGHEST_POINT':
      return interpolate(facts.HIGHEST_POINT, { name: fact.names[language], value: new Intl.NumberFormat(language).format(fact.elevation), country })
    case 'CAPITAL':
      return interpolate(facts.CAPITAL, { name: fact.names[language], country })
    case 'CURRENCY':
      return interpolate(facts.CURRENCY, { name: fact.names[language], code: fact.code, country })
    case 'DRIVES_LEFT':
      return interpolate(facts.DRIVES_LEFT, { country: fact.iso ? countryName(pool, fact.iso, language) : country })
    case 'LANDLOCKED':
      return interpolate(facts.LANDLOCKED, { country })
    case 'NEIGHBOURS':
      return interpolate(facts.NEIGHBOURS, {
        country,
        count: fact.countries.length,
        names: fact.countries.map(iso => countryName(pool, iso, language)).join(LIST_SEPARATOR),
      })
    case 'REGION':
      return interpolate(facts.REGION, { region: regions[fact.subregion] ?? fact.subregion, country })
    case 'VALUES':
      return facts.VALUES
  }
}

export function hidesFlag(stop: QuizStop) {
  return stop.questions.some(question => question.kind === 'FLAG' && question.answer == null)
}

export function answerCountryCode(question: QuizQuestion) {
  const answer = question.answer
  return answer ? question.options[answer.answerIndex]?.countryCode ?? null : null
}
