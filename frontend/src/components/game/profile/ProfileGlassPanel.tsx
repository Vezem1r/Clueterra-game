import type { ReactNode } from 'react'
import { cn } from '@/utils/cn'

interface ProfileGlassPanelProps {
  children: ReactNode
  className?: string
}

export function ProfileGlassPanel({ children, className }: ProfileGlassPanelProps) {
  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-[2rem] border border-white/[.08] bg-[linear-gradient(180deg,rgba(255,255,255,.055),rgba(9,13,24,.96))] shadow-[0_22px_64px_rgba(3,7,18,.28)] md:backdrop-blur-2xl md:backdrop-saturate-150',
        className
      )}
    >
      <div className="pointer-events-none absolute inset-x-8 top-0 h-px bg-gradient-to-r from-transparent via-white/18 to-transparent" />
      {children}
    </div>
  )
}
