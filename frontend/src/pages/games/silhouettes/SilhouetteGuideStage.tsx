import type { ReactNode } from 'react'
import { cn } from '@/utils/cn'

export interface SilhouetteGuideImage {
  src: string
  width: number
  height: number
}

interface SilhouetteGuideStageProps {
  children: ReactNode
  className?: string
}

interface SilhouetteGuideShotProps {
  image: SilhouetteGuideImage
  alt: string
  cover?: boolean
  eager?: boolean
  className?: string
}

export function SilhouetteGuideStage({ children, className }: SilhouetteGuideStageProps) {
  return <div className={cn('relative isolate flex items-center justify-center overflow-hidden rounded-[1.75rem] border border-cyan-200/10 bg-[#060b14] bg-[radial-gradient(circle_at_50%_45%,rgba(103,232,249,.13),transparent_62%),linear-gradient(rgba(255,255,255,.03)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.03)_1px,transparent_1px)] bg-[length:100%_100%,32px_32px,32px_32px] shadow-[0_30px_80px_rgba(0,0,0,.45)]', className)}>
    {children}
  </div>
}

export function SilhouetteGuideShot({ image, alt, cover = false, eager = false, className }: SilhouetteGuideShotProps) {
  return <img src={image.src} width={image.width} height={image.height} alt={alt}
    loading={eager ? 'eager' : 'lazy'} decoding="async"
    className={cn(cover
      ? 'h-full w-full object-cover'
      : 'h-auto max-h-full w-auto max-w-full rounded-[1.25rem] border border-white/[.1] object-contain shadow-[0_24px_60px_rgba(0,0,0,.6)]', className)} />
}
