import { ChevronRight, CircleCheck } from 'lucide-react'
import type { FlagReveal } from '@/api/flags'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameReveal } from '../globe-game/GlobeGameReveal'
import { flagUrl } from '../silhouettes/silhouetteGameConstants'
import type { SilhouetteCountryOption } from '../silhouettes/silhouetteWorld'
import { FlagCard } from './FlagCard'
import type { FlagOutcome } from './useFlagGame'

interface FlagRevealCardProps {
  copy: TranslationKeys['flagGame']
  regions: Record<string, string>
  language: SilhouetteLocale
  reveal: FlagReveal
  outcome: FlagOutcome
  xpAwarded: number
  attempts: number
  guesses: SilhouetteCountryOption[]
  busy: boolean
  complete: boolean
  onNext: () => void
}

const CAPITAL_SEPARATOR = ', '

export function FlagRevealCard({
  copy, regions, language, reveal, outcome, xpAwarded, attempts, guesses, busy, complete, onNext,
}: FlagRevealCardProps) {
  const solved = outcome === 'solved'
  const name = reveal.names[language]
  const summary = !solved ? copy.shownSummary : xpAwarded > 0 ? interpolate(copy.solvedSummary, { count: attempts }) : copy.repeatSummary
  const facts: [string, string][] = [
    [copy.regionLabel, regions[reveal.subregion] ?? reveal.subregion],
    ...(reveal.capitals ? [[copy.capitalLabel, reveal.capitals.map(capital => capital[language]).join(CAPITAL_SEPARATOR)] as [string, string]] : []),
  ]

  return <GlobeGameReveal accent="flag" testId="flag-reveal" titleId="flag-reveal-title" solved={solved}
    status={solved ? copy.solvedTitle : copy.shownTitle} name={name}
    xp={solved && xpAwarded > 0 ? interpolate(copy.solvedXp, { xp: xpAwarded }) : null} summary={summary} photo={null}
    media={<div className="mx-4 mt-4 flex h-32 shrink-0 items-center justify-center sm:mx-6 sm:h-40">
      <FlagCard src={flagUrl(reveal.countryCode)} alt={interpolate(copy.flagOf, { name })} eager className="h-full max-w-full" />
    </div>}
    nextLabel={copy.next} next={!complete} busy={busy} onNext={onNext}>
    {complete && <div className="mt-4 rounded-2xl bg-emerald-300/10 px-4 py-3 ring-1 ring-emerald-200/20">
      <p className="text-sm font-bold text-emerald-100">{copy.completeTitle}</p>
      <p className="mt-1 text-sm text-emerald-50/70">{copy.completeBody}</p>
    </div>}
    {guesses.length > 0 && <section aria-labelledby="flag-guesses-title" className="mt-5">
      <h3 id="flag-guesses-title" className="mb-2.5 text-sm font-semibold text-white/85">{copy.guessesTitle}</h3>
      <ol className="flex flex-wrap items-center gap-x-1 gap-y-1.5">
        {guesses.map((country, index) => <li key={country.iso} className="inline-flex items-center gap-1">
          {index > 0 && <ChevronRight size={13} aria-hidden="true" className="text-white/25" />}
          <span className="inline-flex items-center gap-1.5 rounded-full border border-rose-300/20 bg-rose-400/[.08] py-1 pl-1 pr-2.5 text-xs text-rose-100/85">
            <img src={flagUrl(country.iso)} alt="" aria-hidden="true" className="h-3.5 w-5 rounded-[3px] object-cover ring-1 ring-white/20" />
            {country.names[language]}
          </span>
        </li>)}
        {solved && <li className="inline-flex items-center gap-1">
          <ChevronRight size={13} aria-hidden="true" className="text-white/25" />
          <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-300/25 bg-emerald-300/10 py-1 pl-1 pr-2.5 text-xs font-semibold text-emerald-100">
            <img src={flagUrl(reveal.countryCode)} alt="" aria-hidden="true" className="h-3.5 w-5 rounded-[3px] object-cover ring-1 ring-white/20" />
            {name} <CircleCheck size={12} aria-hidden="true" />
          </span>
        </li>}
      </ol>
    </section>}
    <dl className="mt-5 grid gap-2 [@media(max-height:760px)]:mt-4">
      {facts.map(([label, value]) => <div key={label} className="flex items-baseline justify-between gap-4 rounded-xl bg-white/[.04] px-3.5 py-2.5 ring-1 ring-white/[.06]">
        <dt className="text-xs text-white/45">{label}</dt>
        <dd className="text-right text-sm font-semibold text-white/90">{value}</dd>
      </div>)}
    </dl>
  </GlobeGameReveal>
}
