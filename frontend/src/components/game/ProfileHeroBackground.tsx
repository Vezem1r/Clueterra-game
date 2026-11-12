import type { ReactNode } from 'react'
import { cn } from '@/utils/cn'

interface ProfileHeroBackgroundProps {
  children: ReactNode
  className?: string
}

export function ProfileHeroBackground({ children, className }: ProfileHeroBackgroundProps) {
  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-[1.55rem] border border-r-0 border-white/[.06] shadow-[inset_0_1px_0_rgba(255,255,255,.04)]',
        className,
      )}
      style={{
        background: [
          'linear-gradient(112deg, rgba(22, 78, 99, .24) 0%, rgba(7, 18, 30, .16) 34%, rgba(4, 7, 14, .72) 72%)',
          'linear-gradient(145deg, #0d2630 0%, #07131f 46%, #05070f 100%)',
        ].join(', '),
      }}
    >
      <div className="pointer-events-none absolute inset-x-10 top-0 z-[1] h-px bg-gradient-to-r from-transparent via-white/[.13] to-transparent" />
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(90deg,rgba(1,8,14,.04),rgba(1,8,14,.28)_58%,rgba(1,8,14,.18))]" />
      <div className="pointer-events-none absolute inset-y-0 right-0 w-24 bg-gradient-to-l from-[#05070f] via-[#05070f]/42 to-transparent" />
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(180deg,rgba(255,255,255,.035),transparent_34%,rgba(0,0,0,.22))]" />

      <div
        className="pointer-events-none absolute inset-y-0 left-0 w-[48%] opacity-[.16]"
        style={{
          backgroundImage:
            'radial-gradient(circle, rgba(125,211,252,.75) 1px, transparent 1.8px), radial-gradient(circle, rgba(251,191,36,.46) 1px, transparent 1.9px), linear-gradient(32deg, transparent 47%, rgba(125,211,252,.25) 48%, transparent 49%), linear-gradient(148deg, transparent 43%, rgba(125,211,252,.17) 44%, transparent 45%)',
          backgroundPosition: '0 0, 22px 16px, 0 0, 0 0',
          backgroundSize: '44px 34px, 68px 48px, 112px 86px, 128px 96px',
          maskImage: 'radial-gradient(circle at 28% 45%, black 0%, rgba(0,0,0,.78) 42%, transparent 76%)',
        }}
      />

      <div
        className="pointer-events-none absolute inset-0 opacity-[.07]"
        style={{
          backgroundImage: [
            'linear-gradient(rgba(148, 163, 184, .18) 1px, transparent 1px)',
            'linear-gradient(90deg, rgba(148, 163, 184, .13) 1px, transparent 1px)',
          ].join(', '),
          backgroundSize: '86px 86px',
          maskImage: 'linear-gradient(90deg, black 0%, rgba(0,0,0,.74) 42%, transparent 86%)',
        }}
      />

      <div
        className="pointer-events-none absolute inset-0 opacity-[.018]"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.72' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='1'/%3E%3C/svg%3E")`,
          backgroundSize: '160px 160px',
        }}
      />

      <div className="relative z-[2] h-full min-h-0">{children}</div>
    </div>
  )
}
