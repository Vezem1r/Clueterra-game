import { BookmarkPlus, Eye } from 'lucide-react'
import { cn } from '@/utils/cn'
import { GLOBE_GAME_THEMES, type GlobeGameAccent } from './globeGameTheme'

export interface GlobeGameActionsProps {
  accent: GlobeGameAccent
  skipLabel: string
  showAnswerLabel: string
  busy: boolean
  onSkip: () => void
  onShowAnswer: () => void
}

export function GlobeGameActions({ accent, skipLabel, showAnswerLabel, busy, onSkip, onShowAnswer }: GlobeGameActionsProps) {
  return <div className="mt-3 grid shrink-0 grid-cols-2 gap-2 border-t border-white/[.06] pt-3 sm:pt-4 lg:[@media(max-height:800px)]:pt-3">
    <button type="button" disabled={busy} onClick={onSkip}
      className={cn('flex min-h-11 items-center justify-center gap-2 rounded-2xl bg-white/[.05] px-2 text-center text-xs font-semibold text-white/75 transition-colors hover:bg-white/[.09] hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-40 sm:min-h-12 sm:px-3 sm:text-sm', GLOBE_GAME_THEMES[accent].focus)}>
      <BookmarkPlus size={16} className="shrink-0 text-white/50" aria-hidden="true" /> {skipLabel}
    </button>
    <button type="button" disabled={busy} onClick={onShowAnswer}
      className="flex min-h-11 items-center justify-center gap-2 rounded-2xl border border-amber-200/20 bg-transparent px-2 text-center text-xs font-semibold text-amber-100/90 transition-colors hover:border-amber-200/40 hover:bg-amber-200/[.08] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-200 disabled:opacity-40 sm:min-h-12 sm:px-3 sm:text-sm">
      <Eye size={16} className="shrink-0 text-amber-200/80" aria-hidden="true" /> {showAnswerLabel}
    </button>
  </div>
}
