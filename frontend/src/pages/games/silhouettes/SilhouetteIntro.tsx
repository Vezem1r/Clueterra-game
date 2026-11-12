import { Globe2, Lightbulb, ScanSearch, type LucideIcon } from 'lucide-react'
import type { SilhouetteQuestion } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameIntro } from '../globe-game/GlobeGameIntro'
import { SILHOUETTE_GUIDE_PATH, SILHOUETTE_GUIDE_QUERY } from './silhouetteGameConstants'
import { SilhouetteShape } from './SilhouetteShape'

const STEP_ICONS: LucideIcon[] = [ScanSearch, Lightbulb, Globe2]

interface SilhouetteIntroProps {
  copy: TranslationKeys['silhouetteGame']
  legalNotice: string
  busy: boolean
  ready: boolean
  isGuest: boolean
  complete: boolean
  current: SilhouetteQuestion | null
  onStart: () => void
  onOpenPrivacy: () => void
  onOpenTerms: () => void
}

export function SilhouetteIntro({
  copy, legalNotice, busy, ready, isGuest, complete, current, onStart, onOpenPrivacy, onOpenTerms,
}: SilhouetteIntroProps) {
  return <GlobeGameIntro accent="silhouette" titleId="silhouette-title" actions={!complete}
    lead={complete ? copy.completeTitle : copy.lead} detail={complete ? copy.completeBody : copy.leadDetail}
    actionLabel={current ? interpolate(copy.resume, { count: current.round }) : copy.start}
    preparingLabel={copy.preparing} howToPlay={copy.howToPlay} guidePath={SILHOUETTE_GUIDE_PATH} guideQuery={SILHOUETTE_GUIDE_QUERY}
    legalNotice={legalNotice} busy={busy} ready={ready} isGuest={isGuest}
    onStart={onStart} onOpenPrivacy={onOpenPrivacy} onOpenTerms={onOpenTerms}
    facts={[
      { question: copy.guide.countriesTitle, answer: copy.guide.countriesBody },
      { question: copy.guide.bordersTitle, answer: copy.guide.bordersBody },
      ...copy.guide.faq,
    ]}
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
      media: <SilhouetteShape copy={copy} shapePath={current.shapePath}
        className="mx-auto mt-5 hidden aspect-square w-full max-w-[14rem] sm:block [@media(max-height:820px)]:hidden" />,
    }} />
}
