import { ArrowRight, Car, Flag, MapPin, Plane } from 'lucide-react'
import type { QuizStop } from '@/api/quiz'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { flagUrl } from '../silhouettes/silhouetteGameConstants'
import type { CountryPool } from '../silhouettes/silhouetteWorld'
import { hidesFlag } from './quizFacts'

interface QuizStopHeadingProps {
  copy: TranslationKeys['quizGame']
  regions: Record<string, string>
  stop: QuizStop
  pool: CountryPool | null
  language: SilhouetteLocale
  titleId: string
}

export function QuizStopHeading({ copy, regions, stop, pool, language, titleId }: QuizStopHeadingProps) {
  const from = stop.fromCountryCode ? pool?.byIso.get(stop.fromCountryCode)?.names[language] ?? null : null
  const ArrivalIcon = stop.arrivalMode === 'FLY' ? Plane : stop.arrivalMode === 'DRIVE' ? Car : MapPin
  const arrival = stop.arrivalMode ? copy.arrival[stop.arrivalMode] : copy.arrival.start

  return <div className="mt-4 shrink-0 max-sm:mt-3">
    <h2 id={titleId} className="flex min-w-0 items-center gap-3 text-[1.7rem] font-black leading-[1.05] tracking-[-.035em] text-white sm:text-[2rem]">
      {hidesFlag(stop)
        ? <span aria-hidden="true" className="flex h-6 w-9 shrink-0 items-center justify-center rounded-[4px] border border-dashed border-white/30 text-white/40 sm:h-7 sm:w-10"><Flag size={14} /></span>
        : <img src={flagUrl(stop.country.countryCode)} alt="" aria-hidden="true"
          className="h-6 w-9 shrink-0 rounded-[4px] object-cover shadow-[0_4px_14px_rgba(0,0,0,.45)] ring-1 ring-white/30 sm:h-7 sm:w-10" />}
      <span className="min-w-0 truncate">{stop.country.names[language]}</span>
    </h2>
    <p className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-white/55">
      <span className="inline-flex items-center gap-1.5 rounded-full bg-orange-200/10 px-2.5 py-1 font-semibold text-orange-100 ring-1 ring-orange-200/20">
        <ArrivalIcon size={13} aria-hidden="true" />
        {from
          ? <><span className="sr-only">{arrival}:</span> {from} <ArrowRight size={12} aria-hidden="true" className="text-orange-100/60" /></>
          : arrival}
      </span>
      <span>{regions[stop.country.subregion] ?? stop.country.subregion}</span>
    </p>
  </div>
}
