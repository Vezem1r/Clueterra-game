import { Info } from 'lucide-react'
import { Tooltip } from '@/components/ui/Tooltip'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'

interface RiverMapProps {
  copy: TranslationKeys['riverGame']
  imageUrl: string
  alt: string
  caption?: string
  className?: string
}

export function RiverMap({ copy, imageUrl, alt, caption, className }: RiverMapProps) {
  return <div data-testid="river-map" className={cn('flex min-h-0 items-center justify-center', className)}>
    <div className="relative w-full">
      <div aria-hidden="true" className="pointer-events-none absolute -inset-10 -z-10 rounded-[3rem] bg-[radial-gradient(closest-side,rgba(56,189,248,.16),transparent)] blur-2xl" />
      <figure className="relative h-[clamp(140px,28dvh,250px)] w-full overflow-hidden rounded-[1.75rem] border border-white/[.1] bg-river-deep shadow-[0_32px_90px_rgba(0,0,0,.5)] lg:aspect-[3/2] lg:h-auto">
        <img src={imageUrl} alt="" aria-hidden="true" className="absolute inset-0 h-full w-full scale-125 object-cover opacity-40 blur-2xl" />
        <img key={imageUrl} src={imageUrl} alt={alt} fetchPriority="high" className="relative h-full w-full object-contain motion-safe:animate-[river-reveal_.4s_ease-out]" />
        {caption && <figcaption className="absolute left-3 top-3 rounded-full bg-black/55 px-3.5 py-1.5 text-xs font-semibold text-white/85 backdrop-blur-md sm:left-4 sm:top-4 sm:text-sm">
          {caption}
        </figcaption>}
        <Tooltip content={copy.mapInfo} placement="top" maxWidth={300} className="absolute bottom-3 left-3 sm:bottom-4 sm:left-4">
          <button type="button" aria-label={copy.mapInfoLabel} className="flex h-9 w-9 items-center justify-center rounded-full bg-black/55 text-white/80 ring-1 ring-white/15 backdrop-blur-md transition-colors hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-300">
            <Info size={17} aria-hidden="true" />
          </button>
        </Tooltip>
      </figure>
    </div>
  </div>
}
