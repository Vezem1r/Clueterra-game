import { useEffect, useRef } from 'react'
import { Lock } from 'lucide-react'
import type { FlagClue, FlagClueKind, FlagHints } from '@/api/flags'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameClueButton, GlobeGameClueLocked, GlobeGameClueValue } from '../globe-game/GlobeGameClue'
import { GlobeGameClues } from '../globe-game/GlobeGameClues'
import type { GlobeGameTrailStop } from '../globe-game/GlobeGameTrail'
import { FLAG_HINT_ICONS } from './flagGameConstants'

interface FlagCluesProps {
  copy: TranslationKeys['flagGame']
  regions: Record<string, string>
  language: SilhouetteLocale
  hints: FlagHints
  clues: FlagClue[]
  hintsUsed: number
  busy: boolean
  onReveal: () => void
}

const CAPITAL_SEPARATOR = ', '

function clueValue(copy: FlagCluesProps['copy'], regions: Record<string, string>, language: SilhouetteLocale,
  kind: FlagClueKind, hints: FlagHints) {
  if (kind === 'REGION' && hints.region) return regions[hints.region.subregion] ?? hints.region.subregion
  if (kind === 'INITIAL' && hints.initials) {
    return <span>
      {copy.initialPrefix} <span className="ml-1 inline-flex size-7 items-center justify-center rounded-lg bg-amber-200/15 font-black text-amber-50 ring-1 ring-amber-200/30">
        {hints.initials[language]}
      </span>
    </span>
  }
  if (kind === 'CAPITAL' && hints.capitals) {
    return <span className="font-semibold">{hints.capitals.map(capital => capital[language]).join(CAPITAL_SEPARATOR)}</span>
  }
  return null
}

export function FlagClues({ copy, regions, language, hints, clues, hintsUsed, busy, onReveal }: FlagCluesProps) {
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
        icon: FLAG_HINT_ICONS[clue.kind],
        content: <GlobeGameClueValue accent="flag" label={copy.clueSlots[clue.kind]} animate>
          {clueValue(copy, regions, language, clue.kind, hints)}
        </GlobeGameClueValue>,
      }
    }
    if (index === hintsUsed) {
      return {
        key: clue.kind,
        state: 'next',
        icon: FLAG_HINT_ICONS[clue.kind],
        content: <GlobeGameClueButton accent="flag" title={copy.openClue[clue.kind]} detail={copy.clueReveals[clue.kind]} busy={busy}
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
    <GlobeGameClues accent="flag" titleId="flag-clues-title" title={copy.cluesTitle} stops={stops}
      progress={interpolate(copy.cluesOpen, { count: hintsUsed, total: clues.length })} />
  </div>
}
