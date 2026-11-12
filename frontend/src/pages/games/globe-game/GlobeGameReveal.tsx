import { useEffect, useRef, type ReactNode } from 'react'
import { ArrowRight, CircleCheck, Eye, MapPin } from 'lucide-react'
import { cn } from '@/utils/cn'
import { GlobeGamePanel } from './GlobeGamePanel'
import { GLOBE_GAME_THEMES, type GlobeGameAccent } from './globeGameTheme'

export interface GlobeGameRevealPhoto {
  url: string
  alt: string
  credit: string | null
  sourceUrl: string | null
  place?: string | null
}

export interface GlobeGameRevealProps {
  accent: GlobeGameAccent
  testId: string
  titleId: string
  solved: boolean
  status: string
  name: string
  flagUrl?: string
  xp: string | null
  summary: string
  photo: GlobeGameRevealPhoto | null
  media?: ReactNode
  nextLabel: string
  next: boolean
  busy: boolean
  extraAction?: ReactNode
  onNext: () => void
  children: ReactNode
}

export function GlobeGameReveal({
  accent, testId, titleId, solved, status, name, flagUrl, xp, summary, photo, media, nextLabel, next, busy, extraAction, onNext, children,
}: GlobeGameRevealProps) {
  const nextButton = useRef<HTMLButtonElement>(null)
  const theme = GLOBE_GAME_THEMES[accent]
  const StatusIcon = solved ? CircleCheck : Eye

  useEffect(() => {
    nextButton.current?.focus({ preventScroll: true })
  }, [])

  const heading = <>
    <p className={cn('flex items-center gap-1.5 text-sm font-semibold', solved ? 'text-river-delta' : 'text-river-silt')}>
      <StatusIcon size={16} aria-hidden="true" /> {status}
    </p>
    <div className="mt-1.5 flex items-end justify-between gap-3">
      <h2 id={titleId} className="flex min-w-0 items-center gap-3 text-balance break-words text-[2rem] font-black leading-[1.05] tracking-[-.035em] text-white [text-shadow:0_2px_24px_rgba(0,0,0,.55)] sm:text-[2.4rem]">
        {flagUrl && <img src={flagUrl} alt="" aria-hidden="true" className="h-6 w-9 shrink-0 rounded-[4px] object-cover shadow-[0_4px_14px_rgba(0,0,0,.45)] ring-1 ring-white/30 sm:h-7 sm:w-10" />}
        <span className="min-w-0">{name}</span>
      </h2>
      {xp && <span className="mb-1.5 shrink-0 rounded-full bg-amber-300/15 px-3 py-1 text-base font-black tabular-nums text-river-silt ring-1 ring-amber-200/30 backdrop-blur-md">
        {xp}
      </span>}
    </div>
  </>

  return <GlobeGamePanel as="section" data-testid={testId} aria-labelledby={titleId} className="motion-safe:animate-river-reveal">
    <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
      {photo
        ? <figure className="relative aspect-[2/1] shrink-0 sm:aspect-[16/10] xl:aspect-auto xl:min-h-44 xl:flex-1">
          <img src={photo.url} alt={photo.alt} className="absolute inset-0 h-full w-full object-cover" />
          <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_top,rgba(9,11,21,1)_0%,rgba(9,11,21,.75)_28%,transparent_62%)]" />
          {photo.place && <p className="absolute left-3 top-3 flex max-w-[45%] items-center gap-1 truncate rounded-full bg-black/45 px-2.5 py-1 text-[11px] font-medium text-white/85 backdrop-blur-md">
            <MapPin size={12} aria-hidden="true" className="shrink-0" /> <span className="truncate">{photo.place}</span>
          </p>}
          {photo.credit && <figcaption className={cn('absolute right-3 top-3 truncate rounded-full bg-black/45 px-2.5 py-1 text-[11px] text-white/70 backdrop-blur-md', photo.place ? 'max-w-[45%]' : 'max-w-[80%]')}>
            {photo.sourceUrl
              ? <a href={photo.sourceUrl} target="_blank" rel="noopener noreferrer" className="underline-offset-2 hover:text-white hover:underline">{photo.credit}</a>
              : photo.credit}
          </figcaption>}
          <div className="absolute inset-x-0 bottom-0 px-4 sm:px-6">{heading}</div>
        </figure>
        : <>
          {media}
          <div className="px-4 pt-5 sm:px-6 sm:pt-6">{heading}</div>
        </>}

      <div className="shrink-0 px-4 pb-5 sm:px-6">
        <p className="mt-2 text-sm text-white/55">{summary}</p>
        {children}
      </div>
    </div>

    {next && <div className="flex shrink-0 gap-2 border-t border-white/[.06] p-4 sm:px-6">
      <button ref={nextButton} type="button" disabled={busy} onClick={onNext}
        className={cn('flex min-h-12 flex-1 items-center justify-center gap-2 rounded-2xl font-bold transition hover:brightness-105 focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50', theme.primary, theme.focus)}>
        {nextLabel} <ArrowRight size={18} aria-hidden="true" />
      </button>
      {extraAction}
    </div>}
  </GlobeGamePanel>
}
