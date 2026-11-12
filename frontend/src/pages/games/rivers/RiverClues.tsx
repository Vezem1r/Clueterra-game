import { Lock } from 'lucide-react'
import type { RiverHint } from '@/api/rivers'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameClueButton, GlobeGameClueLocked, GlobeGameClueValue } from '../globe-game/GlobeGameClue'
import { GlobeGameClues } from '../globe-game/GlobeGameClues'
import type { GlobeGameTrailStop } from '../globe-game/GlobeGameTrail'
import { RIVER_CLUE_COUNT, RIVER_CLUE_SLOTS, RIVER_KIND_ICONS, RIVER_SLOT_ICONS } from './riverGameConstants'

interface RiverCluesProps {
  copy: TranslationKeys['riverGame']
  title: string
  hints: RiverHint[]
  hintsUsed: number
  busy: boolean
  clueCost?: number
  onReveal?: () => void
}

export function RiverClues({ copy, title, hints, hintsUsed, busy, clueCost, onReveal }: RiverCluesProps) {
  const stops: GlobeGameTrailStop[] = RIVER_CLUE_SLOTS.map((slot, index) => {
    const hint = hints.find(item => item.position === index + 1)
    if (hint) {
      return {
        key: slot,
        state: 'flowing',
        icon: RIVER_KIND_ICONS[hint.kind],
        content: <GlobeGameClueValue accent="river" label={copy.clueKinds[hint.kind]} animate>{hint.text}</GlobeGameClueValue>,
      }
    }
    if (index === hintsUsed && onReveal) {
      return {
        key: slot,
        state: 'next',
        icon: RIVER_SLOT_ICONS[slot],
        content: <GlobeGameClueButton accent="river" title={copy.openClue[slot]} detail={copy.clueReveals[slot]} busy={busy}
          cost={clueCost != null && clueCost > 0 ? interpolate(copy.clueCostXp, { xp: clueCost }) : undefined} onClick={onReveal} />,
      }
    }
    return {
      key: slot,
      state: 'dry',
      icon: Lock,
      hiddenWhenShort: true,
      content: <GlobeGameClueLocked label={copy.clueSlots[slot]} detail={copy.clueReveals[slot]} />,
    }
  })

  return <GlobeGameClues accent="river" titleId="river-clues-title" title={title} stops={stops}
    progress={onReveal ? interpolate(copy.cluesOpen, { count: hintsUsed, total: RIVER_CLUE_COUNT }) : undefined} />
}
