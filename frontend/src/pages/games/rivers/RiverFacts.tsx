import type { RiverFact } from '@/api/rivers'
import type { TranslationKeys } from '@/i18n'
import { GlobeGameClueValue } from '../globe-game/GlobeGameClue'
import { GlobeGameClues } from '../globe-game/GlobeGameClues'
import { RIVER_KIND_ICONS } from './riverGameConstants'

interface RiverFactsProps {
  copy: TranslationKeys['riverGame']
  facts: RiverFact[]
}

export function RiverFacts({ copy, facts }: RiverFactsProps) {
  return <GlobeGameClues accent="river" titleId="river-facts-title" title={copy.factsTitle} stops={facts.map(fact => ({
    key: fact.kind,
    state: 'flowing',
    icon: RIVER_KIND_ICONS[fact.kind],
    content: <GlobeGameClueValue accent="river" label={copy.clueKinds[fact.kind]}>{fact.text}</GlobeGameClueValue>,
  }))} />
}
