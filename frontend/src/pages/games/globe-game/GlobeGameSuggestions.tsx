import { cn } from '@/utils/cn'
import { GLOBE_GAME_THEMES, type GlobeGameAccent } from './globeGameTheme'

export interface GlobeGameSuggestion {
  key: string
  label: string
}

export interface GlobeGameSuggestionsProps {
  accent: GlobeGameAccent
  id?: string
  label: string
  items: GlobeGameSuggestion[]
  highlighted?: number
  placement: 'above' | 'below'
  disabled?: boolean
  onPick: (index: number) => void
  onHighlight?: (index: number) => void
}

export function GlobeGameSuggestions({
  accent, id, label, items, highlighted, placement, disabled, onPick, onHighlight,
}: GlobeGameSuggestionsProps) {
  const theme = GLOBE_GAME_THEMES[accent]
  if (items.length === 0) return null

  return <ul id={id} role="listbox" aria-label={label}
    className={cn('absolute inset-x-0 z-20 overflow-hidden rounded-2xl border border-white/[.1] bg-[#0f1220] py-1 shadow-[0_20px_50px_rgba(0,0,0,.55)]',
      placement === 'above' ? 'bottom-[calc(100%+6px)]' : 'top-[calc(100%+6px)]')}>
    {items.map((item, index) => <li key={item.key} role="option" aria-selected={index === highlighted}>
      <button type="button" disabled={disabled} onClick={() => onPick(index)} onMouseEnter={() => onHighlight?.(index)}
        className={cn('block w-full px-4 py-2.5 text-left text-sm text-white/80 hover:text-white focus-visible:outline-none', index === highlighted ? theme.option : 'hover:bg-white/[.05]')}>
        {item.label}
      </button>
    </li>)}
  </ul>
}
