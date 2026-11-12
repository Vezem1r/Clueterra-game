import type { CSSProperties, ReactNode } from 'react'
import { cn } from '@/utils/cn'

interface HomePanelShellProps {
  children: ReactNode
  className?: string
  style?: CSSProperties
}

export function HomePanelShell({ children, className, style }: HomePanelShellProps) {
  return (
    <section
      id="home-action-panel"
      className={cn(
        'relative mx-2 flex w-full max-w-[54rem] min-w-0 flex-col overflow-hidden rounded-[2.4rem] border border-white/[.11] bg-[rgba(9,11,21,.94)] md:bg-[rgba(9,11,21,.60)] px-4 py-4 md:backdrop-blur-[48px] sm:mx-auto sm:px-6 sm:py-5 lg:px-8',
        className
      )}
      style={{
        boxShadow: [
          '0 32px 90px rgba(0,0,0,.48)',
          '0 0 0 1px rgba(255,255,255,.05)',
          '0 -1px 60px rgba(52,211,153,.09)',
          '0 1px 80px rgba(124,92,252,.07)',
        ].join(', '),
        ...style,
      }}
    >
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-[linear-gradient(90deg,transparent_8%,rgba(255,255,255,.28)_50%,transparent_92%)]" />
      <div className="pointer-events-none absolute inset-px rounded-[calc(2.4rem-1px)] bg-[linear-gradient(160deg,rgba(255,255,255,.045)_0%,transparent_25%)]" />

      <div className="pointer-events-none absolute inset-0 overflow-hidden rounded-[2.4rem]">
        <div className="animate-glass-sweep absolute inset-y-0 w-[22%] bg-gradient-to-r from-transparent via-white/[.036] to-transparent" />
      </div>

      <div className="relative flex h-full flex-col">{children}</div>
    </section>
  )
}
