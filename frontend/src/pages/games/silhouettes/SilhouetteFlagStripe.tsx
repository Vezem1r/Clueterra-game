import type { SilhouetteFlagColor } from '@/api/silhouettes'
import { cn } from '@/utils/cn'

interface FlagStripeProps {
  colors: SilhouetteFlagColor[]
  className?: string
}

export function FlagStripe({ colors, className }: FlagStripeProps) {
  return <span aria-hidden="true" className={cn('flex shrink-0 overflow-hidden ring-1 ring-white/20', className)}>
    {colors.map(entry => <span key={entry.color} className="h-full" style={{ backgroundColor: entry.color, flexGrow: entry.share }} />)}
  </span>
}
