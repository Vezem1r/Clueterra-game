import type { MouseEvent, ReactNode } from 'react'
import { BookOpen, ChevronDown, LoaderCircle, Play } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { HomeGuestLegalNotice } from '@/pages/game/home/HomeGuestLegalNotice'
import { cn } from '@/utils/cn'
import { GlobeGamePanel } from './GlobeGamePanel'
import { GlobeGameTrail, type GlobeGameTrailStop } from './GlobeGameTrail'
import { GLOBE_GAME_THEMES, type GlobeGameAccent } from './globeGameTheme'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'

export interface GlobeGameResume {
  round: number
  title: string
  summary: string
  media?: ReactNode
}

export interface GlobeGameFact {
  question: string
  answer: string
}

export interface GlobeGameIntroProps {
  accent: GlobeGameAccent
  titleId: string
  lead: string
  detail: string
  steps: GlobeGameTrailStop[]
  resume: GlobeGameResume | null
  facts?: GlobeGameFact[]
  actions: boolean
  actionLabel: string
  preparingLabel: string
  howToPlay: string
  guidePath: string
  guideQuery: string
  legalNotice: string
  busy: boolean
  ready: boolean
  isGuest: boolean
  onStart: () => void
  onOpenPrivacy: () => void
  onOpenTerms: () => void
}

const GUIDE_PARAM = 'guide'
const RESUME_TITLE_ID = 'globe-game-resume-title'

export function GlobeGameIntro({
  accent, titleId, lead, detail, steps, resume, facts = [], actions, actionLabel, preparingLabel, howToPlay, guidePath, guideQuery,
  legalNotice, busy, ready, isGuest, onStart, onOpenPrivacy, onOpenTerms,
}: GlobeGameIntroProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const theme = GLOBE_GAME_THEMES[accent]
  const localize = useLocalizedPath()

  function openGuide(event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault()
    const params = new URLSearchParams(location.search)
    params.set(GUIDE_PARAM, guideQuery)
    navigate({ pathname: location.pathname, search: `?${params.toString()}` })
  }

  return <GlobeGamePanel as="section" aria-labelledby={titleId} className="p-4 sm:p-6">
    <div className="-mx-4 flex min-h-0 flex-1 flex-col overflow-y-auto px-4 pb-1 sm:-mx-6 sm:px-6">
      <div className={cn(resume && 'max-sm:hidden [@media(max-height:700px)]:hidden lg:[@media(max-height:820px)]:hidden')}>
        <h2 className="text-balance text-[1.35rem] font-bold leading-[1.2] tracking-[-.02em] text-white sm:text-[1.5rem]">{lead}</h2>
        <p className="mt-2.5 max-w-[40ch] text-[15px] leading-relaxed text-white/55 [@media(max-height:640px)]:hidden">{detail}</p>
      </div>
      {resume
        ? <section aria-labelledby={RESUME_TITLE_ID} className="mt-5 flex min-h-0 flex-1 flex-col max-sm:mt-0 [@media(max-height:700px)]:mt-0 lg:[@media(max-height:820px)]:mt-0">
          <div className="flex items-center gap-3">
            <span aria-hidden="true" className={cn('flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-black tabular-nums ring-1', theme.badge)}>{resume.round}</span>
            <div className="min-w-0">
              <h2 id={RESUME_TITLE_ID} className="text-sm font-semibold text-white">{resume.title}</h2>
              <p className="truncate text-sm text-white/50">{resume.summary}</p>
            </div>
          </div>
          {resume.media}
        </section>
        : steps.length > 0 && <GlobeGameTrail accent={accent} stretch={facts.length === 0} stops={steps}
          className={cn('mt-7 hidden flex-col sm:flex lg:[@media(max-height:860px)]:hidden', facts.length === 0 ? 'min-h-0 flex-1' : 'shrink-0')} />}
      {!resume && facts.length > 0 && <div className="mt-5 divide-y divide-white/[.06] border-y border-white/[.06] [@media(max-height:700px)]:hidden">
        {facts.map(fact => <details key={fact.question} className="group py-2.5">
          <summary className={cn('flex cursor-pointer list-none items-center justify-between gap-3 rounded-md text-sm font-semibold text-white/70 transition-colors hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 [&::-webkit-details-marker]:hidden', theme.focus)}>
            {fact.question}
            <ChevronDown size={15} aria-hidden="true" className="shrink-0 text-white/40 transition-transform group-open:rotate-180" />
          </summary>
          <p className="mt-2 text-sm leading-relaxed text-white/50">{fact.answer}</p>
        </details>)}
      </div>}
    </div>

    {actions && <div className="shrink-0 pt-5">
      <div className="flex gap-2">
        <button type="button" onClick={onStart} disabled={busy || !ready}
          className={cn('inline-flex min-h-[52px] flex-1 items-center justify-center gap-2.5 whitespace-nowrap rounded-2xl px-5 text-base font-bold transition hover:brightness-105 focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50 sm:min-h-14', theme.primary, theme.focus)}>
          {busy ? <LoaderCircle size={18} className="animate-spin" aria-hidden="true" /> : <Play size={17} fill="currentColor" aria-hidden="true" />}
          {busy ? preparingLabel : actionLabel}
        </button>
        <Link to={localize(guidePath)} onClick={openGuide}
          className={cn('inline-flex min-h-[52px] items-center justify-center gap-2 rounded-2xl bg-white/[.05] px-4 text-sm font-semibold text-white/75 ring-1 ring-white/[.08] transition-colors hover:bg-white/[.09] hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 sm:min-h-14', theme.focus)}>
          <BookOpen size={16} aria-hidden="true" /> <span className="whitespace-nowrap max-[379px]:sr-only">{howToPlay}</span>
        </Link>
      </div>
      {isGuest && <p className="mt-3 text-xs leading-relaxed text-white/40">
        <HomeGuestLegalNotice actionLabel={actionLabel} onOpenPrivacy={onOpenPrivacy} onOpenTerms={onOpenTerms} template={legalNotice} />
      </p>}
    </div>}
  </GlobeGamePanel>
}
