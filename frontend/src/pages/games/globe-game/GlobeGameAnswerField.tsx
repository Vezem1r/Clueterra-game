import type { FormEvent, InputHTMLAttributes, ReactNode } from 'react'
import { ArrowRight, Search } from 'lucide-react'
import { Input } from '@/components/ui/Input'
import { cn } from '@/utils/cn'
import { GLOBE_GAME_THEMES, type GlobeGameAccent } from './globeGameTheme'

export interface GlobeGameAnswerFieldProps {
  accent: GlobeGameAccent
  inputProps: InputHTMLAttributes<HTMLInputElement>
  submitLabel: string
  submitDisabled: boolean
  misses: number
  onSubmit: () => void
  className?: string
  children?: ReactNode
}

export function GlobeGameAnswerField({
  accent, inputProps, submitLabel, submitDisabled, misses, onSubmit, className, children,
}: GlobeGameAnswerFieldProps) {
  const theme = GLOBE_GAME_THEMES[accent]

  function submit(event: FormEvent) {
    event.preventDefault()
    onSubmit()
  }

  return <form onSubmit={submit} className={cn('relative', className)}>
    <div key={misses} className={cn('relative flex gap-2', misses > 0 && 'motion-safe:animate-river-shake')}>
      <Search size={18} aria-hidden="true" className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-white/35" />
      <Input autoComplete="off" spellCheck={false} {...inputProps}
        className={cn('h-12 rounded-2xl border-white/[.1] bg-black/30 pl-11 text-base text-white placeholder:text-white/35 sm:h-14 lg:[@media(max-height:800px)]:h-12', theme.input)} />
      <button type="submit" disabled={submitDisabled} aria-label={submitLabel}
        className={cn('flex size-12 shrink-0 items-center justify-center rounded-2xl transition hover:brightness-105 focus-visible:outline-2 focus-visible:outline-offset-2 disabled:bg-none disabled:bg-white/[.06] disabled:text-white/35 disabled:shadow-none sm:size-14 lg:[@media(max-height:800px)]:size-12', theme.primary, theme.focus)}>
        <ArrowRight size={21} aria-hidden="true" />
      </button>
    </div>
    {children}
  </form>
}
