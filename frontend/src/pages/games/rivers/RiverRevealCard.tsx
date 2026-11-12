import { ExternalLink } from 'lucide-react'
import type { RiverReveal } from '@/api/rivers'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameReveal } from '../globe-game/GlobeGameReveal'
import { RiverFacts } from './RiverFacts'

export type RiverOutcome = 'solved' | 'shown'

interface RiverRevealCardProps {
  copy: TranslationKeys['riverGame']
  reveal: RiverReveal
  outcome: RiverOutcome
  xpAwarded: number
  attempts: number
  busy: boolean
  onNext: () => void
}

export function RiverRevealCard({ copy, reveal, outcome, xpAwarded, attempts, busy, onNext }: RiverRevealCardProps) {
  const solved = outcome === 'solved'
  const photo = reveal.photo
  const credit = photo && photo.licenseLabel
    ? photo.authorName
      ? interpolate(copy.photoCredit, { author: photo.authorName, license: photo.licenseLabel })
      : interpolate(copy.photoCreditUnknown, { license: photo.licenseLabel })
    : null
  const summary = !solved ? copy.shownSummary
    : xpAwarded > 0 ? interpolate(copy.solvedSummary, { count: attempts })
      : copy.repeatSummary

  return <GlobeGameReveal accent="river" testId="river-reveal" titleId="river-reveal-title" solved={solved}
    status={solved ? copy.solvedTitle : copy.shownTitle} name={reveal.name}
    xp={solved && xpAwarded > 0 ? interpolate(copy.solvedXp, { xp: xpAwarded }) : null} summary={summary}
    photo={photo && { url: photo.url, alt: interpolate(copy.photoAlt, { name: reveal.name }), credit, sourceUrl: photo.sourcePageUrl }}
    nextLabel={copy.next} next busy={busy} onNext={onNext}
    extraAction={reveal.wikipediaUrl && <a href={reveal.wikipediaUrl} target="_blank" rel="noopener noreferrer"
      className="flex min-h-12 items-center justify-center gap-2 rounded-2xl bg-white/[.05] px-4 text-sm font-semibold text-white/75 ring-1 ring-white/[.08] transition-colors hover:bg-white/[.09] hover:text-white">
      {copy.readMore} <ExternalLink size={14} aria-hidden="true" />
    </a>}>
    <div className="mt-5 [@media(max-height:760px)]:mt-4">
      <RiverFacts copy={copy} facts={reveal.facts} />
    </div>
  </GlobeGameReveal>
}
