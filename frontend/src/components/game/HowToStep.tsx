import type { ReactNode } from 'react'
import { MousePointerClick } from 'lucide-react'

import { cn } from '@/utils/cn'

interface HowToStepProps {
  index: number
  title: string
  body: string
  tryLabel: string
  tryText: string
  accent?: 'violet' | 'emerald'
  wide?: boolean
  children: ReactNode
}

const ACCENT_STYLES = {
  violet: 'border-violet-300/20 bg-violet-400/10 text-violet-200',
  emerald: 'border-emerald-300/20 bg-emerald-300/10 text-emerald-200',
} as const

export function HowToStep({ index, title, body, tryLabel, tryText, accent = 'violet', wide = false, children }: HowToStepProps) {
  return (
    <section className={cn('grid gap-6', !wide && 'lg:grid-cols-[minmax(0,.8fr)_minmax(0,1.2fr)] lg:gap-10')}>
      <div className={cn(wide ? 'max-w-3xl' : 'lg:sticky lg:top-24 lg:self-start')}>
        <span className="font-display text-sm font-bold tabular-nums text-white/25">
          {String(index).padStart(2, '0')}
        </span>
        <h2 className="mt-2 text-2xl font-bold tracking-[-.025em] text-white sm:text-3xl">{title}</h2>
        <p className="mt-3 text-[0.95rem] leading-7 text-white/56">{body}</p>
        <div className="mt-5 flex items-start gap-2.5">
          <span
            className={cn(
              'inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[.14em]',
              ACCENT_STYLES[accent],
            )}
          >
            <MousePointerClick size={12} />
            {tryLabel}
          </span>
          <p className="pt-0.5 text-sm leading-6 text-white/45">{tryText}</p>
        </div>
      </div>

      <div className="min-w-0 rounded-[2rem] border border-white/[.08] bg-[linear-gradient(165deg,rgba(14,17,28,.92),rgba(7,9,16,.96))] p-4 shadow-[0_32px_90px_rgba(0,0,0,.34)] sm:p-6">
        {children}
      </div>
    </section>
  )
}
