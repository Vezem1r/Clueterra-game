import type { SilhouetteFacts as Facts, SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { GlobeGameClueValue } from '../globe-game/GlobeGameClue'
import { GlobeGameClues } from '../globe-game/GlobeGameClues'
import { factNotes, factStats } from './silhouetteFactItems'

interface SilhouetteFactsProps {
  copy: TranslationKeys['silhouetteGame']
  facts: Facts
  language: SilhouetteLocale
}

export function SilhouetteFacts({ copy, facts, language }: SilhouetteFactsProps) {
  const notes = factNotes(copy.facts, facts)

  return <GlobeGameClues accent="silhouette" titleId="silhouette-facts-title" title={copy.factsTitle}
    stops={factStats(copy.facts, facts, language).map(stat => ({
      key: stat.key,
      state: 'flowing',
      icon: stat.icon,
      content: <GlobeGameClueValue accent="silhouette" label={stat.label}>{stat.value}</GlobeGameClueValue>,
    }))}>
    {notes.length > 0 && <ul className="mt-4 flex flex-wrap gap-1.5">
      {notes.map(note => <li key={note.key} className={cn('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs ring-1',
        note.notable ? 'bg-amber-300/10 text-amber-100 ring-amber-200/25' : 'bg-white/[.04] text-white/70 ring-white/10')}>
        <note.icon size={13} aria-hidden="true" /> {note.text}
      </li>)}
    </ul>}
  </GlobeGameClues>
}
