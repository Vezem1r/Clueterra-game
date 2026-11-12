import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { SILHOUETTE_COMPASS_SECTOR_DEGREES } from './silhouetteGameConstants'
import type { SilhouetteWrongGuess } from './useSilhouetteGame'

type SilhouetteCopy = TranslationKeys['silhouetteGame']

function compassPoint(copy: SilhouetteCopy, bearingDeg: number) {
  return copy.compass[Math.round(bearingDeg / SILHOUETTE_COMPASS_SECTOR_DEGREES) % copy.compass.length]
}

export function silhouetteMissAnnouncement(copy: SilhouetteCopy, language: SilhouetteLocale, guess: SilhouetteWrongGuess) {
  const name = guess.country.names[language]
  if (!guess.miss) return interpolate(copy.incorrect, { guess: name })
  return interpolate(copy.incorrectMiss, {
    guess: name,
    distance: new Intl.NumberFormat(language).format(guess.miss.distanceKm),
    direction: compassPoint(copy, guess.miss.bearingDeg),
  })
}

export function silhouetteMissDistance(copy: SilhouetteCopy, language: SilhouetteLocale, distanceKm: number) {
  return interpolate(copy.missDistance, { distance: new Intl.NumberFormat(language).format(distanceKm) })
}
