import { useEffect, useRef } from 'react'
import { Lock } from 'lucide-react'
import type { SilhouetteClue, SilhouetteClueKind, SilhouetteHints, SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameClueButton, GlobeGameClueLocked, GlobeGameClueValue } from '../globe-game/GlobeGameClue'
import { GlobeGameClues } from '../globe-game/GlobeGameClues'
import type { GlobeGameTrailStop } from '../globe-game/GlobeGameTrail'
import { FlagStripe } from './SilhouetteFlagStripe'
import { SILHOUETTE_HINT_ICONS } from './silhouetteGameConstants'

interface SilhouetteCluesProps {
  copy: TranslationKeys['silhouetteGame']
  language: SilhouetteLocale
  hints: SilhouetteHints
  clues: SilhouetteClue[]
  hintsUsed: number
  busy: boolean
  onReveal: () => void
}

export interface LandmarkClueProps {
  copy: SilhouetteCluesProps['copy']
  landmark: NonNullable<SilhouetteHints['landmark']>
}

const CAPITAL_SEPARATOR = ', '

function LandmarkClue({ copy, landmark }: LandmarkClueProps) {
  const license = landmark.licenseLabel ?? ''
  const credit = landmark.authorName
    ? interpolate(copy.photoCredit, { author: landmark.authorName, license })
    : interpolate(copy.photoCreditUnknown, { license })
  return <figure className="mt-1.5">
    <img src={landmark.url} alt={copy.landmarkAlt} loading="lazy"
      className="aspect-[16/10] w-full rounded-xl object-cover ring-1 ring-white/10" />
    <figcaption className="mt-1 truncate text-[10px] text-white/35">
      {landmark.licenseUrl
        ? <a href={landmark.licenseUrl} target="_blank" rel="noopener noreferrer" className="hover:text-white/60">{credit}</a>
        : credit}
    </figcaption>
  </figure>
}

function clueValue(copy: SilhouetteCluesProps['copy'], language: SilhouetteLocale, kind: SilhouetteClueKind, hints: SilhouetteHints) {
  if (kind === 'REGION' && hints.region) return copy.regions[hints.region.subregion] ?? hints.region.subregion
  if (kind === 'LOCATION' && hints.location) return copy.locationValue
  if (kind === 'CAPITAL' && hints.capitals) {
    return <span className="font-semibold">{hints.capitals.map(capital => capital[language]).join(CAPITAL_SEPARATOR)}</span>
  }
  if (kind === 'CAPITAL' && hints.flagColors) {
    return <span className="block">
      <span className="text-white/60">{copy.capitalFallback}</span>
      <FlagStripe colors={hints.flagColors} className="mt-1.5 h-4 w-full max-w-[12rem] rounded" />
    </span>
  }
  if (kind === 'LANDMARK' && hints.landmark) return <LandmarkClue copy={copy} landmark={hints.landmark} />
  return null
}

export function SilhouetteClues({ copy, language, hints, clues, hintsUsed, busy, onReveal }: SilhouetteCluesProps) {
  const sectionRef = useRef<HTMLDivElement>(null)
  const shownRef = useRef(hintsUsed)

  useEffect(() => {
    if (hintsUsed > shownRef.current) {
      sectionRef.current?.querySelectorAll('li')[hintsUsed - 1]?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    }
    shownRef.current = hintsUsed
  }, [hintsUsed])

  const stops: GlobeGameTrailStop[] = clues.map((clue, index) => {
    if (index < hintsUsed) {
      return {
        key: clue.kind,
        state: 'flowing',
        icon: SILHOUETTE_HINT_ICONS[clue.kind],
        content: <GlobeGameClueValue accent="silhouette" label={copy.clueSlots[clue.kind]} animate>
          {clueValue(copy, language, clue.kind, hints)}
        </GlobeGameClueValue>,
      }
    }
    if (index === hintsUsed) {
      return {
        key: clue.kind,
        state: 'next',
        icon: SILHOUETTE_HINT_ICONS[clue.kind],
        content: <GlobeGameClueButton accent="silhouette" title={copy.openClue[clue.kind]} detail={copy.clueReveals[clue.kind]} busy={busy}
          cost={clue.costXp > 0 ? interpolate(copy.clueCostXp, { xp: clue.costXp }) : undefined} onClick={onReveal} />,
      }
    }
    return {
      key: clue.kind,
      state: 'dry',
      icon: Lock,
      hiddenWhenShort: true,
      content: <GlobeGameClueLocked label={copy.clueSlots[clue.kind]} detail={copy.clueReveals[clue.kind]} />,
    }
  })

  return <div ref={sectionRef}>
    <GlobeGameClues accent="silhouette" titleId="silhouette-clues-title" title={copy.cluesTitle} stops={stops}
      progress={interpolate(copy.cluesOpen, { count: hintsUsed, total: clues.length })} />
  </div>
}
