import { Lightbulb, Lock, MapIcon, PenLine, type LucideIcon } from 'lucide-react'
import type { RiverQuestion } from '@/api/rivers'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { GlobeGameIntro } from '../globe-game/GlobeGameIntro'
import { GlobeGameTrail } from '../globe-game/GlobeGameTrail'
import { GLOBE_GAME_THEMES } from '../globe-game/globeGameTheme'
import { RIVER_CLUE_SLOTS, RIVER_GUIDE_PATH, RIVER_GUIDE_QUERY, RIVER_KIND_ICONS } from './riverGameConstants'

const STEP_ICONS: LucideIcon[] = [MapIcon, Lightbulb, PenLine]

interface RiverIntroProps {
  copy: TranslationKeys['riverGame']
  legalNotice: string
  busy: boolean
  ready: boolean
  isGuest: boolean
  current: RiverQuestion | null
  draft: string
  onStart: () => void
  onOpenPrivacy: () => void
  onOpenTerms: () => void
}

export function RiverIntro({
  copy, legalNotice, busy, ready, isGuest, current, draft, onStart, onOpenPrivacy, onOpenTerms,
}: RiverIntroProps) {
  const label = GLOBE_GAME_THEMES.river.label

  return <GlobeGameIntro accent="river" titleId="river-title" lead={copy.lead} detail={copy.leadDetail} actions
    actionLabel={current ? interpolate(copy.resume, { count: current.roundNumber }) : copy.start}
    preparingLabel={copy.preparing} howToPlay={copy.howToPlay} guidePath={RIVER_GUIDE_PATH} guideQuery={RIVER_GUIDE_QUERY}
    legalNotice={legalNotice} busy={busy} ready={ready} isGuest={isGuest}
    onStart={onStart} onOpenPrivacy={onOpenPrivacy} onOpenTerms={onOpenTerms}
    facts={[{ question: copy.guide.riversTitle, answer: copy.guide.riversBody }, ...copy.guide.faq]}
    steps={copy.steps.map((step, index) => ({
      key: step.title,
      state: 'flowing',
      icon: STEP_ICONS[index],
      content: <>
        <p className="text-sm font-semibold text-white/90">{step.title}</p>
        <p className="text-sm text-white/50">{step.body}</p>
      </>,
    }))}
    resume={current && {
      round: current.roundNumber,
      title: copy.resumeTitle,
      summary: draft ? interpolate(copy.resumeDraft, { draft }) : interpolate(copy.resumeSummary, { attempts: current.attempts, clues: current.hintsUsed }),
      media: <GlobeGameTrail accent="river" stretch className="mt-5 hidden min-h-0 flex-1 sm:flex lg:[@media(max-height:890px)]:hidden"
        stops={RIVER_CLUE_SLOTS.map((slot, index) => {
          const hint = current.revealedHints.find(item => item.position === index + 1)
          return {
            key: slot,
            state: hint ? 'flowing' : 'dry',
            icon: hint ? RIVER_KIND_ICONS[hint.kind] : Lock,
            content: <>
              <p className={cn('text-xs font-medium', hint ? label : 'pt-2 text-sm text-white/35')}>{hint ? copy.clueKinds[hint.kind] : copy.clueSlots[slot]}</p>
              {hint && <p className="line-clamp-1 text-sm text-white/80">{hint.text}</p>}
            </>,
          }
        })} />,
    }} />
}
