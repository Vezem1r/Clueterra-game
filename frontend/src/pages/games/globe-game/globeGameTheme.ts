export type GlobeGameAccent = 'river' | 'silhouette' | 'flag' | 'quiz'

export interface GlobeGameTheme {
  primary: string
  focus: string
  input: string
  option: string
  badge: string
  label: string
  link: string
  nodeFlowing: string
  nodeNext: string
  stream: string
  clue: string
  clueTitle: string
  clueIcon: string
}

export const GLOBE_GAME_THEMES: Record<GlobeGameAccent, GlobeGameTheme> = {
  river: {
    primary: 'bg-gradient-to-r from-sky-300 to-cyan-300 text-[#04131d] shadow-[0_18px_52px_rgba(56,189,248,.22),inset_0_1px_0_rgba(255,255,255,.35)]',
    focus: 'focus-visible:outline-sky-300',
    input: 'focus:border-sky-300/60 focus:ring-sky-300/25',
    option: 'bg-sky-300/10 text-white',
    badge: 'bg-sky-300/15 text-sky-200 ring-sky-300/30',
    label: 'text-sky-200/70',
    link: 'text-sky-200/75 hover:text-sky-100',
    nodeFlowing: 'border-sky-300/45 bg-[#0b1824] text-sky-200 shadow-[0_0_14px_rgba(56,189,248,.3)]',
    nodeNext: 'border-dashed border-sky-300/60 bg-[#0b1220] text-sky-300',
    stream: 'stroke-sky-300/70 [filter:drop-shadow(0_0_4px_rgba(56,189,248,.7))]',
    clue: 'bg-sky-300/[.06] ring-sky-300/25 hover:bg-sky-300/[.11] hover:ring-sky-300/50',
    clueTitle: 'text-sky-50',
    clueIcon: 'text-sky-300/80',
  },
  silhouette: {
    primary: 'bg-gradient-to-r from-cyan-300 to-teal-200 text-[#03141c] shadow-[0_18px_52px_rgba(103,232,249,.22),inset_0_1px_0_rgba(255,255,255,.35)]',
    focus: 'focus-visible:outline-cyan-300',
    input: 'focus:border-cyan-300/60 focus:ring-cyan-300/25',
    option: 'bg-cyan-300/10 text-white',
    badge: 'bg-cyan-300/15 text-cyan-100 ring-cyan-300/30',
    label: 'text-cyan-100/70',
    link: 'text-cyan-200/75 hover:text-cyan-100',
    nodeFlowing: 'border-cyan-300/45 bg-[#0a1a22] text-cyan-100 shadow-[0_0_14px_rgba(103,232,249,.3)]',
    nodeNext: 'border-dashed border-cyan-300/60 bg-[#0a141e] text-cyan-200',
    stream: 'stroke-cyan-300/70 [filter:drop-shadow(0_0_4px_rgba(103,232,249,.7))]',
    clue: 'bg-cyan-300/[.06] ring-cyan-300/25 hover:bg-cyan-300/[.11] hover:ring-cyan-300/50',
    clueTitle: 'text-cyan-50',
    clueIcon: 'text-cyan-200/80',
  },
  flag: {
    primary: 'bg-gradient-to-r from-amber-200 to-rose-300 text-[#1f0b10] shadow-[0_18px_52px_rgba(251,191,36,.22),inset_0_1px_0_rgba(255,255,255,.35)]',
    focus: 'focus-visible:outline-amber-200',
    input: 'focus:border-amber-200/60 focus:ring-amber-200/25',
    option: 'bg-amber-200/10 text-white',
    badge: 'bg-amber-200/15 text-amber-100 ring-amber-200/30',
    label: 'text-amber-100/70',
    link: 'text-amber-200/75 hover:text-amber-100',
    nodeFlowing: 'border-amber-200/45 bg-[#1d150b] text-amber-100 shadow-[0_0_14px_rgba(251,191,36,.3)]',
    nodeNext: 'border-dashed border-amber-200/60 bg-[#15100a] text-amber-200',
    stream: 'stroke-amber-200/70 [filter:drop-shadow(0_0_4px_rgba(251,191,36,.7))]',
    clue: 'bg-amber-200/[.06] ring-amber-200/25 hover:bg-amber-200/[.11] hover:ring-amber-200/50',
    clueTitle: 'text-amber-50',
    clueIcon: 'text-amber-200/80',
  },
  quiz: {
    primary: 'bg-gradient-to-r from-orange-200 to-emerald-200 text-[#10140b] shadow-[0_18px_52px_rgba(253,186,116,.22),inset_0_1px_0_rgba(255,255,255,.35)]',
    focus: 'focus-visible:outline-orange-200',
    input: 'focus:border-orange-200/60 focus:ring-orange-200/25',
    option: 'bg-orange-200/10 text-white',
    badge: 'bg-orange-200/15 text-orange-100 ring-orange-200/30',
    label: 'text-orange-100/70',
    link: 'text-orange-200/75 hover:text-orange-100',
    nodeFlowing: 'border-orange-200/45 bg-[#1b140d] text-orange-100 shadow-[0_0_14px_rgba(253,186,116,.3)]',
    nodeNext: 'border-dashed border-orange-200/60 bg-[#140f0a] text-orange-200',
    stream: 'stroke-orange-200/70 [filter:drop-shadow(0_0_4px_rgba(253,186,116,.7))]',
    clue: 'bg-orange-200/[.06] ring-orange-200/25 hover:bg-orange-200/[.11] hover:ring-orange-200/50',
    clueTitle: 'text-orange-50',
    clueIcon: 'text-orange-200/80',
  },
}

export const GLOBE_GAME_PANEL_WIDTH = 'lg:w-[27rem]'
export const GLOBE_GAME_SHEET_SEPARATOR = ' · '
