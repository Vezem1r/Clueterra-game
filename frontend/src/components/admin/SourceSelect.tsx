import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Check, ChevronDown, Search } from 'lucide-react'
import type { SourceDetail } from '@/api/admin'
import { cn } from '@/utils/cn'

type SourceInput = string | Pick<SourceDetail, 'name' | 'displayName'>

interface SourceOption {
  value: string
  label: string
  rawName: string
}

interface SourceSelectProps {
  value: string
  onChange: (value: string) => void
  sources: SourceInput[]
  className?: string
  size?: 'sm' | 'md'
  placeholder?: string
  allowAll?: boolean
  allLabel?: string
  searchPlaceholder?: string
  emptyStateLabel?: string
}

function humanizeSourceName(name: string): string {
  return name
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ')
}

function normalizeSource(source: SourceInput): SourceOption {
  if (typeof source === 'string') {
    return {
      value: source,
      label: humanizeSourceName(source),
      rawName: source,
    }
  }

  return {
    value: source.name,
    label: source.displayName?.trim() || humanizeSourceName(source.name),
    rawName: source.name,
  }
}

function matchesSource(option: SourceOption, query: string): boolean {
  if (!query) return true
  const needle = query.trim().toLowerCase()
  if (!needle) return true

  return option.label.toLowerCase().includes(needle)
    || option.rawName.toLowerCase().includes(needle)
    || option.rawName.replace(/_/g, ' ').toLowerCase().includes(needle)
}

export function SourceSelect({
  value,
  onChange,
  sources,
  className,
  size = 'md',
  placeholder = 'Select source...',
  allowAll = false,
  allLabel = 'All Sources',
  searchPlaceholder = 'Search sources...',
  emptyStateLabel = 'No sources found',
}: SourceSelectProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const buttonRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const searchRef = useRef<HTMLInputElement>(null)
  const [pos, setPos] = useState<{ top?: number; bottom?: number; left: number; width: number }>({ left: 0, width: 0 })

  const unique = new Set<string>()
  const options = sources
    .map(normalizeSource)
    .filter(option => {
      if (unique.has(option.value)) return false
      unique.add(option.value)
      return true
    })

  const selected = options.find(option => option.value === value)
    ?? (value
      ? {
        value,
        label: humanizeSourceName(value),
        rawName: value,
      }
      : null)
  const selectedLabel = allowAll && value === ''
    ? allLabel
    : selected?.label ?? placeholder

  const filteredOptions = options.filter(option => matchesSource(option, query))

  const MAX_DROPDOWN_H = 280

  const updatePos = () => {
    if (!buttonRef.current) return
    const rect = buttonRef.current.getBoundingClientRect()
    const spaceBelow = window.innerHeight - rect.bottom - 8
    const spaceAbove = rect.top - 8
    const openUp = spaceBelow < MAX_DROPDOWN_H && spaceAbove > spaceBelow
    if (openUp) {
      setPos({ bottom: window.innerHeight - rect.top + 4, left: rect.left, width: rect.width })
    } else {
      setPos({ top: rect.bottom + 4, left: rect.left, width: rect.width })
    }
  }

  const handleToggle = () => {
    if (!open) {
      updatePos()
      setQuery('')
    }
    setOpen(current => !current)
  }

  const close = () => {
    setOpen(false)
    setQuery('')
  }

  const handleSelect = (nextValue: string) => {
    onChange(nextValue)
    close()
  }

  useEffect(() => {
    if (!open) return

    searchRef.current?.focus()

    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node
      if (
        buttonRef.current && !buttonRef.current.contains(target)
        && dropdownRef.current && !dropdownRef.current.contains(target)
      ) {
        close()
      }
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        close()
      }
    }

    const handleViewportChange = () => updatePos()

    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    window.addEventListener('scroll', handleViewportChange, true)
    window.addEventListener('resize', handleViewportChange)

    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('scroll', handleViewportChange, true)
      window.removeEventListener('resize', handleViewportChange)
    }
  }, [open])

  return (
    <div className={cn('relative shrink-0', className)}>
      <button
        ref={buttonRef}
        type="button"
        onClick={handleToggle}
        className={cn(
          'w-full flex items-center gap-2 rounded-lg border transition-all cursor-pointer',
          'bg-white/[.04] border-white/[.08] text-white',
          'hover:bg-white/[.06] hover:border-white/[.14]',
          open && 'bg-white/[.06] border-white/[.14]',
          size === 'sm' ? 'h-8 px-2.5 text-xs' : 'h-9 px-3 text-sm',
        )}
      >
        <span className={cn('truncate flex-1 text-left', !selected && value !== '' && 'text-white/35')}>
          {selectedLabel}
        </span>
        <ChevronDown
          size={12}
          className={cn('text-white/30 shrink-0 transition-transform', open && 'rotate-180')}
        />
      </button>

      {open && createPortal(
        <div
          ref={dropdownRef}
          className="fixed z-[100] overflow-hidden rounded-2xl border border-white/[.08] bg-[#0d0d16] shadow-2xl shadow-black/60"
          style={{ top: pos.top, bottom: pos.bottom, left: pos.left, width: Math.max(pos.width, 260) }}
        >
          <div className="border-b border-white/[.06] bg-white/[.02] p-2">
            <div className="flex items-center gap-2 rounded-xl border border-white/[.08] bg-black/20 px-2.5">
              <Search size={13} className="shrink-0 text-white/25" />
              <input
                ref={searchRef}
                type="text"
                value={query}
                onChange={event => setQuery(event.target.value)}
                placeholder={searchPlaceholder}
                className={cn(
                  'w-full bg-transparent text-white placeholder:text-white/25 focus:outline-none',
                  size === 'sm' ? 'h-8 text-base sm:text-xs' : 'h-9 text-base sm:text-sm',
                )}
              />
            </div>
          </div>

          <div className="max-h-80 overflow-y-auto py-1 scrollbar-thin">
            {allowAll && (
              <button
                type="button"
                onClick={() => handleSelect('')}
                className={cn(
                  'w-full px-3 py-2.5 text-left transition-colors cursor-pointer',
                  'hover:bg-white/[.05]',
                  value === '' ? 'bg-accent/[.08] text-accent' : 'text-white/70',
                )}
              >
                <div className="flex items-center gap-2.5">
                  <span className="flex-1 truncate text-sm font-medium">{allLabel}</span>
                  {value === '' && <Check size={13} className="shrink-0" />}
                </div>
              </button>
            )}

            {allowAll && filteredOptions.length > 0 && (
              <div className="mx-3 my-1 h-px bg-white/[.06]" />
            )}

            {filteredOptions.map(option => {
              const isSelected = option.value === value
              const showRawName = option.rawName !== option.label

              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => handleSelect(option.value)}
                  className={cn(
                    'w-full px-3 py-2.5 text-left transition-colors cursor-pointer',
                    'hover:bg-white/[.05]',
                    isSelected ? 'bg-accent/[.08] text-accent' : 'text-white/70',
                  )}
                >
                  <div className="flex items-start gap-2.5">
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-medium">
                        {option.label}
                      </div>
                      {showRawName && (
                        <div className={cn(
                          'mt-0.5 truncate font-mono text-[10px]',
                          isSelected ? 'text-accent/70' : 'text-white/28',
                        )}>
                          {option.rawName}
                        </div>
                      )}
                    </div>
                    {isSelected && <Check size={13} className="mt-0.5 shrink-0" />}
                  </div>
                </button>
              )
            })}

            {filteredOptions.length === 0 && (
              <div className="px-4 py-6 text-center">
                <div className="text-sm text-white/28">{emptyStateLabel}</div>
              </div>
            )}
          </div>

          <div className="border-t border-white/[.06] px-3 py-2 text-[11px] text-white/22">
            {filteredOptions.length} source{filteredOptions.length === 1 ? '' : 's'}
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}
