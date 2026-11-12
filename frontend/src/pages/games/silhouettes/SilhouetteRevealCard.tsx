import { ChevronRight, CircleCheck } from 'lucide-react'
import type { SilhouetteLocale, SilhouetteReveal } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameReveal } from '../globe-game/GlobeGameReveal'
import { SilhouetteFacts } from './SilhouetteFacts'
import { flagUrl } from './silhouetteGameConstants'
import { silhouetteMissDistance } from './silhouetteMiss'
import { SilhouetteShape } from './SilhouetteShape'
import type { SilhouetteWrongGuess } from './useSilhouetteGame'

export type SilhouetteOutcome = 'solved' | 'shown'

interface SilhouetteRevealCardProps {
  copy: TranslationKeys['silhouetteGame']
  language: SilhouetteLocale
  reveal: SilhouetteReveal
  outcome: SilhouetteOutcome
  xpAwarded: number
  attempts: number
  guesses: SilhouetteWrongGuess[]
  busy: boolean
  complete: boolean
  onNext: () => void
}

export function SilhouetteRevealCard({
  copy, language, reveal, outcome, xpAwarded, attempts, guesses, busy, complete, onNext,
}: SilhouetteRevealCardProps) {
  const solved = outcome === 'solved'
  const photo = reveal.photo
  const name = reveal.names[language]
  const credit = photo?.licenseLabel
    ? photo.authorName
      ? interpolate(copy.photoCredit, { author: photo.authorName, license: photo.licenseLabel })
      : interpolate(copy.photoCreditUnknown, { license: photo.licenseLabel })
    : null
  const summary = !solved ? copy.shownSummary : xpAwarded > 0 ? interpolate(copy.solvedSummary, { count: attempts }) : copy.repeatSummary

  return <GlobeGameReveal accent="silhouette" testId="silhouette-reveal" titleId="silhouette-reveal-title" solved={solved}
    status={solved ? copy.solvedTitle : copy.shownTitle} name={name} flagUrl={flagUrl(reveal.countryCode)}
    xp={solved && xpAwarded > 0 ? interpolate(copy.solvedXp, { xp: xpAwarded }) : null} summary={summary}
    photo={photo && {
      url: photo.url, alt: interpolate(copy.photoAlt, { name }), credit, sourceUrl: photo.sourcePageUrl,
      place: photo.landmarkNames?.[language] ?? null,
    }}
    media={<SilhouetteShape copy={copy} shapePath={reveal.shapePath} revealed className="mx-4 mt-4 aspect-[3/1] shrink-0 sm:mx-6 sm:aspect-[2/1]" />}
    nextLabel={copy.next} next={!complete} busy={busy} onNext={onNext}>
    {complete && <div className="mt-4 rounded-2xl bg-emerald-300/10 px-4 py-3 ring-1 ring-emerald-200/20">
      <p className="text-sm font-bold text-emerald-100">{copy.completeTitle}</p>
      <p className="mt-1 text-sm text-emerald-50/70">{copy.completeBody}</p>
    </div>}
    {guesses.length > 0 && <section aria-labelledby="silhouette-guesses-title" className="mt-5">
      <h3 id="silhouette-guesses-title" className="mb-2.5 text-sm font-semibold text-white/85">{copy.guessesTitle}</h3>
      <ol className="flex flex-wrap items-center gap-x-1 gap-y-1.5">
        {guesses.map(({ country, miss }, index) => <li key={country.iso} className="inline-flex items-center gap-1">
          {index > 0 && <ChevronRight size={13} aria-hidden="true" className="text-white/25" />}
          <span className="inline-flex items-center gap-1.5 rounded-full border border-rose-300/20 bg-rose-400/[.08] py-1 pl-1 pr-2.5 text-xs text-rose-100/85">
            <img src={flagUrl(country.iso)} alt="" aria-hidden="true" className="h-3.5 w-5 rounded-[3px] object-cover ring-1 ring-white/20" />
            {country.names[language]}
            {miss && <span className="tabular-nums text-rose-50/55">{silhouetteMissDistance(copy, language, miss.distanceKm)}</span>}
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
    <div className="mt-5 [@media(max-height:760px)]:mt-4">
      <SilhouetteFacts copy={copy} facts={reveal.facts} language={language} />
    </div>
    <p className="mt-4 text-[11px] text-white/35">{copy.factsSource}</p>
  </GlobeGameReveal>
}
