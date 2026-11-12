import type { CSSProperties, ReactNode } from 'react'
import { ChevronUp } from 'lucide-react'
import { Header } from '@/components/layout/Header'
import type { TranslationKeys } from '@/i18n'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { cn } from '@/utils/cn'
import { GLOBE_GAME_PANEL_WIDTH } from './globeGameTheme'
import type { GlobeGameStageState } from './useGlobeGameStage'

export type GlobeGameSheet = 'intro' | 'play' | 'reveal'

export interface GlobeGameStageProps {
  stage: GlobeGameStageState
  copy: TranslationKeys['globeGame']
  space: string
  guideMode: 'river' | 'silhouette' | 'flag' | 'quiz'
  sheet: GlobeGameSheet
  sheetLabel: string
  globe: ReactNode
  header: ReactNode
  stats: ReactNode
  error: string | null
  children: ReactNode
}

const SPACE_VARIABLE = '--globe-game-space'

const SHEET_HEIGHT: Record<GlobeGameSheet, string> = {
  intro: 'max-h-[min(100%,72dvh)]',
  play: 'max-h-[min(100%,66dvh)] [@media(max-height:640px)]:max-h-[min(100%,60dvh)]',
  reveal: 'max-h-[min(100%,58dvh)]',
}

export function GlobeGameStage({
  stage, copy, space, guideMode, sheet, sheetLabel, globe, header, stats, error, children,
}: GlobeGameStageProps) {
  const intro = sheet === 'intro'
  const { stageRef, headerRef, panelRef, collapsed, collapse, expand } = stage

  return <div ref={stageRef} className="relative h-[100dvh] overflow-hidden text-white"
    style={{ backgroundColor: space, [SPACE_VARIABLE]: space } as CSSProperties}>
    {globe}
    <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 z-10 h-48 bg-gradient-to-b from-(--globe-game-space)/90 via-(--globe-game-space)/45 to-transparent lg:h-56" />
    <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 right-0 z-10 hidden w-[40rem] bg-gradient-to-l from-(--globe-game-space)/70 to-transparent lg:block" />
    <div className="relative z-30"><Header /></div>

    <main className={cn('pointer-events-none absolute inset-x-0 bottom-0 top-16 z-20 flex flex-col px-3 sm:px-6',
      '[html:not([data-cookie-consent])_&]:pb-[calc(7.75rem+env(safe-area-inset-bottom))]',
      intro ? 'pb-3 sm:pb-6 md:pb-24' : 'pb-3 sm:pb-6')}>
      <div ref={headerRef} className="pointer-events-auto pt-2 [text-shadow:0_2px_18px_rgba(0,0,0,.6)] sm:pt-4">{header}</div>
      {error && <div role="alert" className={cn('pointer-events-auto mb-3 rounded-2xl bg-rose-950/80 px-4 py-3 text-sm text-rose-100 ring-1 ring-rose-300/25 backdrop-blur-md lg:ml-auto', GLOBE_GAME_PANEL_WIDTH)}>{error}</div>}
      <div className="flex min-h-0 flex-1 flex-col justify-end lg:flex-row lg:items-center lg:justify-end">
        <div ref={panelRef} className={cn('pointer-events-auto flex min-h-0 w-full flex-col sm:mx-auto sm:max-w-xl lg:mx-0 lg:max-h-full lg:max-w-none',
          GLOBE_GAME_PANEL_WIDTH, SHEET_HEIGHT[sheet])}>
          {collapsed
            ? <button type="button" onClick={expand} aria-expanded={false}
              className="flex w-full items-center gap-3 rounded-[1.4rem] border border-white/[.1] bg-[rgba(9,11,21,.82)] px-4 pb-3 pt-2 text-left shadow-[0_18px_40px_rgba(0,0,0,.45)] backdrop-blur-xl motion-safe:animate-river-reveal">
              <span className="min-w-0 flex-1">
                <span aria-hidden="true" className="mx-auto mb-2 block h-1 w-10 rounded-full bg-white/25" />
                <span className="block truncate text-sm font-semibold text-white">{sheetLabel}</span>
                <span className="block truncate text-xs text-white/50">{copy.showPanel}</span>
              </span>
              <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-white/[.07] text-white/80"><ChevronUp size={18} /></span>
            </button>
            : <button type="button" onClick={collapse} aria-label={copy.hidePanel} aria-expanded
              className="-mb-1 flex h-6 w-full shrink-0 items-center justify-center lg:hidden">
              <span aria-hidden="true" className="h-1 w-10 rounded-full bg-white/35 shadow-[0_1px_6px_rgba(0,0,0,.6)]" />
            </button>}
          <div className={cn('flex min-h-0 flex-1 flex-col', collapsed && 'hidden')}>
            {intro && <div className="mb-3 shrink-0 [@media(max-height:680px)]:hidden">{stats}</div>}
            {children}
          </div>
        </div>
      </div>
    </main>
    {intro && <HomeLegalStrip guideMode={guideMode} />}
  </div>
}
