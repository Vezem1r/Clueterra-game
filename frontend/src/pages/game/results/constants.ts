export const CARD_RADIUS_OUTER = 'rounded-[1.75rem]'
export const CARD_RADIUS_INNER = 'rounded-[1.25rem]'
export const SURFACE_PANEL = 'bg-white/[.035] border border-white/[.07]'
export const SURFACE_INLAY = 'bg-black/15 border border-white/[.06]'

export const STAGGER_DELAY_MS = 80
export const SECTION_DELAY_PULSE = 0.26
export const SECTION_DELAY_COUNTRIES = 0.34

export const LABEL_TRACKING = 'tracking-[.16em]'
export const LABEL_TEXT = 'text-[10px] leading-tight uppercase'

export type ResultsTone = 'emerald' | 'amber' | 'sky' | 'violet' | 'rose' | 'neutral'

export function getToneSurfaceClasses(tone: ResultsTone): string {
  switch (tone) {
    case 'amber':
      return 'bg-amber-300/[.07] border-amber-200/12 text-amber-100/80'
    case 'emerald':
      return 'bg-emerald-300/[.07] border-emerald-200/12 text-emerald-100/80'
    case 'sky':
      return 'bg-sky-300/[.07] border-sky-200/12 text-sky-100/80'
    case 'violet':
      return 'bg-violet-300/[.07] border-violet-200/12 text-violet-100/80'
    case 'rose':
      return 'bg-rose-300/[.07] border-rose-200/12 text-rose-100/80'
    default:
      return 'bg-white/[.035] border-white/[.07] text-white/75'
  }
}

export function getToneChipClasses(tone: ResultsTone): string {
  switch (tone) {
    case 'amber':
      return 'bg-amber-300/10 border-amber-200/15 text-amber-200/75'
    case 'emerald':
      return 'bg-emerald-300/10 border-emerald-200/15 text-emerald-200/75'
    case 'sky':
      return 'bg-sky-300/10 border-sky-200/15 text-sky-200/75'
    case 'violet':
      return 'bg-violet-300/10 border-violet-200/15 text-violet-200/75'
    case 'rose':
      return 'bg-rose-300/10 border-rose-200/15 text-rose-200/75'
    default:
      return 'bg-white/[.05] border-white/[.08] text-white/70'
  }
}
