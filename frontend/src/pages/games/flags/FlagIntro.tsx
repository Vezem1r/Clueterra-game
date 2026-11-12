import { Flag, Lightbulb, Search, type LucideIcon } from 'lucide-react'
import type { FlagQuestion } from '@/api/flags'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameIntro } from '../globe-game/GlobeGameIntro'
import { FlagCard } from './FlagCard'
import { FLAG_GUIDE_PATH, FLAG_GUIDE_QUERY } from './flagGameConstants'

const STEP_ICONS: LucideIcon[] = [Search, Lightbulb, Flag]

interface FlagIntroProps {
  copy: TranslationKeys['flagGame']
  legalNotice: string
  busy: boolean
  ready: boolean
  isGuest: boolean
  complete: boolean
  current: FlagQuestion | null
  onStart: () => void
  onOpenPrivacy: () => void
  onOpenTerms: () => void
}

export function FlagIntro({
  copy, legalNotice, busy, ready, isGuest, complete, current, onStart, onOpenPrivacy, onOpenTerms,
}: FlagIntroProps) {
  return <GlobeGameIntro accent="flag" titleId="flag-title" actions={!complete}
    lead={complete ? copy.completeTitle : copy.lead} detail={complete ? copy.completeBody : copy.leadDetail}
    actionLabel={current ? interpolate(copy.resume, { count: current.round }) : copy.start}
    preparingLabel={copy.preparing} howToPlay={copy.howToPlay} guidePath={FLAG_GUIDE_PATH} guideQuery={FLAG_GUIDE_QUERY}
    legalNotice={legalNotice} busy={busy} ready={ready} isGuest={isGuest}
    onStart={onStart} onOpenPrivacy={onOpenPrivacy} onOpenTerms={onOpenTerms}
    facts={[{ question: copy.guide.countriesTitle, answer: copy.guide.countriesBody }, ...copy.guide.faq]}
    steps={complete ? [] : copy.steps.map((step, index) => ({
      key: step.title,
      state: 'flowing',
      icon: STEP_ICONS[index],
      content: <>
        <p className="text-sm font-semibold text-white/90">{step.title}</p>
        <p className="text-sm text-white/50">{step.body}</p>
      </>,
    }))}
    resume={current && {
      round: current.round,
      title: copy.resumeTitle,
      summary: interpolate(copy.resumeSummary, { attempts: current.attempts, clues: current.hintsUsed, total: current.clues.length }),
      media: <div className="mx-auto mt-5 hidden h-28 items-center justify-center sm:flex [@media(max-height:820px)]:hidden">
        <FlagCard src={current.flagUrl} alt={copy.flagLabel} className="h-full" />
      </div>,
    }} />
}
