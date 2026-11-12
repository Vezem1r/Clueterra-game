import type { Country } from '@/types'
import { cn } from '@/utils/cn'
import { getFlagUrl } from '@/utils/country'

export type CountryFlagSize = 'xs' | 'sm' | 'md' | 'lg'

interface CountryFlagProps {
  country: Pick<Country, 'nameEn' | 'isoAlpha2' | 'flagUrl'>
  size?: CountryFlagSize
  alt?: string
  className?: string
}

const SIZE_CLASSES: Record<CountryFlagSize, { box: string; text: string; cdn: 'w40' | 'w80' | 'w160' }> = {
  xs: { box: 'w-9 h-7 rounded-md', text: 'text-[10px]', cdn: 'w40' },
  sm: { box: 'w-12 h-9 rounded-lg', text: 'text-xs', cdn: 'w80' },
  md: { box: 'w-16 h-12 rounded-xl', text: 'text-sm', cdn: 'w160' },
  lg: { box: 'w-20 h-14 sm:w-24 sm:h-16 rounded-xl', text: 'text-base', cdn: 'w160' },
}

export function CountryFlag({ country, size = 'md', alt, className }: CountryFlagProps) {
  const { box, text, cdn } = SIZE_CLASSES[size]
  const src = getFlagUrl(country, cdn)
  const label = alt ?? country.nameEn

  if (src) {
    return (
      <img
        src={src}
        alt={label}
        loading="lazy"
        className={cn(
          box,
          'object-cover border border-white/[.08] shadow-lg shadow-black/25 shrink-0 bg-white/[.04]',
          className
        )}
      />
    )
  }

  return (
    <div
      aria-label={label}
      role="img"
      className={cn(
        box,
        text,
        'bg-white/[.06] border border-white/[.1] flex items-center justify-center font-bold text-white/35 shrink-0',
        className
      )}
    >
      {country.isoAlpha2}
    </div>
  )
}
