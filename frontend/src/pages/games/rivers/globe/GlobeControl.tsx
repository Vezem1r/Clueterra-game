import type { LucideIcon } from 'lucide-react'
import { Tooltip } from '@/components/ui/Tooltip'
import { cn } from '@/utils/cn'

const TOOLTIP_MAX_WIDTH_PX = 260
const TOOLTIP_OFFSET_PX = 14
const CONTROL = 'flex h-10 w-10 items-center justify-center text-white/80 transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-sky-300 disabled:opacity-40'

export interface GlobeControlProps {
  icon: LucideIcon
  label: string
  hint?: string
  placement: 'top' | 'right'
  className?: string
  disabled?: boolean
  pressed?: boolean
  touchHint?: boolean
  onClick?: () => void
}

export function GlobeControl({ icon: Icon, label, hint, placement, className, disabled, pressed, touchHint = false, onClick }: GlobeControlProps) {
  return <Tooltip placement={placement} offset={TOOLTIP_OFFSET_PX} maxWidth={TOOLTIP_MAX_WIDTH_PX} disableTouch={!touchHint}
    contentClassName="!text-left" className={cn('flex', className)}
    content={<>
      <span className="block text-[13px] font-semibold text-white">{label}</span>
      {hint && <span className="mt-1 block leading-relaxed text-white/60">{hint}</span>}
    </>}>
    <button type="button" disabled={disabled} onClick={onClick} aria-label={label} aria-pressed={pressed}
      className={cn(CONTROL, pressed && 'bg-sky-300/15 text-sky-200')}>
      <Icon size={17} aria-hidden="true" />
    </button>
  </Tooltip>
}
