import type { CSSProperties } from 'react'
import { cn } from '@/utils/cn'

type GameAuroraBackdropVariant = 'default' | 'hero' | 'play' | 'light'

interface AuroraOrbConfig {
  blobClassName: string
  style: CSSProperties
  wrapperClassName: string
}

interface GameAuroraBackdropProps {
  className?: string
  variant?: GameAuroraBackdropVariant
}

interface AuroraVariantConfig {
  gradient: CSSProperties
  overlay: CSSProperties
  orbs: AuroraOrbConfig[]
  patternClassName: string
}

const VARIANT_CONFIG: Record<GameAuroraBackdropVariant, AuroraVariantConfig> = {
  default: {
    gradient: {
      background:
        'radial-gradient(circle at 18% 12%, rgba(52,211,153,.12), transparent 32%), radial-gradient(circle at 82% 8%, rgba(251,191,36,.1), transparent 30%), radial-gradient(circle at 50% 70%, rgba(59,130,246,.08), transparent 38%)',
    },
    overlay: {
      background:
        'radial-gradient(circle at 50% -10%, rgba(255,255,255,.03), transparent 36%), linear-gradient(180deg, rgba(8,8,12,0) 0%, rgba(8,8,12,.28) 100%)',
    },
    orbs: [],
    patternClassName: 'opacity-70',
  },
  hero: {
    gradient: {
      background:
        'radial-gradient(circle at 14% 10%, rgba(52,211,153,.17), transparent 28%), radial-gradient(circle at 86% 12%, rgba(251,191,36,.12), transparent 25%), radial-gradient(circle at 50% 82%, rgba(56,189,248,.12), transparent 34%), linear-gradient(180deg, rgba(8,8,12,.02) 0%, rgba(8,8,12,.3) 100%)',
    },
    overlay: {
      background:
        'radial-gradient(circle at 50% 22%, rgba(255,255,255,.035), transparent 32%), linear-gradient(180deg, rgba(8,8,12,0) 0%, rgba(8,8,12,.46) 100%)',
    },
    orbs: [
      {
        wrapperClassName: '-left-28 top-[-9rem] h-[32rem] w-[32rem]',
        blobClassName: 'animate-aurora-drift-slow',
        style: {
          background: 'radial-gradient(circle, rgba(16,185,129,.24) 0%, rgba(16,185,129,.08) 34%, transparent 72%)',
        },
      },
      {
        wrapperClassName: 'right-[-8rem] top-[4rem] h-[28rem] w-[28rem]',
        blobClassName: 'animate-aurora-drift-fast',
        style: {
          background: 'radial-gradient(circle, rgba(124,92,252,.22) 0%, rgba(124,92,252,.08) 34%, transparent 72%)',
        },
      },
      {
        wrapperClassName: 'left-[22%] bottom-[-12rem] h-[30rem] w-[30rem]',
        blobClassName: 'animate-aurora-drift-slow',
        style: {
          background: 'radial-gradient(circle, rgba(56,189,248,.18) 0%, rgba(56,189,248,.07) 32%, transparent 74%)',
        },
      },
    ],
    patternClassName: 'opacity-80',
  },
  play: {
    gradient: {
      background:
        'radial-gradient(circle at 18% 18%, rgba(16,185,129,.16), transparent 30%), radial-gradient(circle at 82% 14%, rgba(124,92,252,.18), transparent 28%), radial-gradient(circle at 50% 56%, rgba(56,189,248,.11), transparent 34%), radial-gradient(circle at 50% 100%, rgba(248,113,113,.06), transparent 26%)',
    },
    overlay: {
      background:
        'radial-gradient(ellipse at 50% 38%, rgba(255,255,255,.02), transparent 30%), linear-gradient(180deg, rgba(8,8,12,0) 0%, rgba(8,8,12,.5) 100%)',
    },
    orbs: [
      {
        wrapperClassName: '-left-24 top-[10%] h-[26rem] w-[26rem]',
        blobClassName: 'animate-aurora-drift-slow',
        style: {
          background: 'radial-gradient(circle, rgba(52,211,153,.2) 0%, rgba(52,211,153,.07) 34%, transparent 72%)',
        },
      },
      {
        wrapperClassName: 'right-[-7rem] top-[18%] h-[24rem] w-[24rem]',
        blobClassName: 'animate-aurora-drift-fast',
        style: {
          background: 'radial-gradient(circle, rgba(124,92,252,.22) 0%, rgba(124,92,252,.08) 34%, transparent 72%)',
        },
      },
      {
        wrapperClassName: 'left-[38%] bottom-[-10rem] h-[22rem] w-[22rem]',
        blobClassName: 'animate-aurora-drift-slow',
        style: {
          background: 'radial-gradient(circle, rgba(14,165,233,.16) 0%, rgba(14,165,233,.06) 34%, transparent 72%)',
        },
      },
    ],
    patternClassName: 'opacity-75',
  },
  light: {
    gradient: {
      background:
        'radial-gradient(circle at 16% 16%, rgba(125,211,252,.12), transparent 28%), radial-gradient(circle at 84% 10%, rgba(167,139,250,.12), transparent 26%), radial-gradient(circle at 50% 78%, rgba(74,222,128,.09), transparent 34%), linear-gradient(180deg, rgba(8,8,12,.03) 0%, rgba(8,8,12,.2) 100%)',
    },
    overlay: {
      background:
        'radial-gradient(circle at 50% 24%, rgba(255,255,255,.03), transparent 34%), linear-gradient(180deg, rgba(8,8,12,0) 0%, rgba(8,8,12,.24) 100%)',
    },
    orbs: [
      {
        wrapperClassName: '-left-24 top-[-6rem] h-[26rem] w-[26rem]',
        blobClassName: 'animate-aurora-drift-slow',
        style: {
          background: 'radial-gradient(circle, rgba(14,165,233,.18) 0%, rgba(14,165,233,.06) 34%, transparent 72%)',
        },
      },
      {
        wrapperClassName: 'right-[-7rem] top-[3rem] h-[24rem] w-[24rem]',
        blobClassName: 'animate-aurora-drift-fast',
        style: {
          background: 'radial-gradient(circle, rgba(168,85,247,.16) 0%, rgba(168,85,247,.06) 34%, transparent 72%)',
        },
      },
      {
        wrapperClassName: 'left-[28%] bottom-[-10rem] h-[22rem] w-[22rem]',
        blobClassName: 'animate-aurora-drift-slow',
        style: {
          background: 'radial-gradient(circle, rgba(34,197,94,.14) 0%, rgba(34,197,94,.05) 34%, transparent 72%)',
        },
      },
    ],
    patternClassName: 'opacity-55',
  },
}

export function GameAuroraBackdrop({ className, variant = 'default' }: GameAuroraBackdropProps) {
  const config = VARIANT_CONFIG[variant]

  return (
    <div className={cn('fixed inset-0 overflow-hidden pointer-events-none', className)} aria-hidden>
      <div className="absolute inset-0" style={config.gradient} />
      {config.orbs.map((orb, index) => (
        <div key={`${variant}-orb-${index}`} className={cn('absolute hidden md:block', orb.wrapperClassName)}>
          <div className={cn('h-full w-full rounded-full blur-[120px] hidden md:block', orb.blobClassName)} style={orb.style} />
        </div>
      ))}
      <div className={cn('absolute inset-0 topo-pattern', config.patternClassName)} />
      <div className="absolute inset-0" style={config.overlay} />
    </div>
  )
}
