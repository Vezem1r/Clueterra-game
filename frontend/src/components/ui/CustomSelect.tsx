import { useState, useRef, useEffect, useCallback } from 'react'
import { createPortal } from 'react-dom'
import { ChevronDown, Check } from 'lucide-react'
import { cn } from '@/utils/cn'

export interface SelectOption {
  value: string
  label: string
}

interface CustomSelectProps {
  value: string
  onChange: (value: string) => void
  options: SelectOption[]
  className?: string
  size?: 'sm' | 'md'
  placeholder?: string
}

export function CustomSelect({
  value,
  onChange,
  options,
  className,
  size = 'md',
  placeholder,
}: CustomSelectProps) {
  const [open, setOpen] = useState(false)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const [pos, setPos] = useState<{ top?: number; bottom?: number; left: number; minWidth: number }>({ left: 0, minWidth: 0 })

  const selected = options.find(o => o.value === value)

  const MAX_DROPDOWN_H = 256

  const updatePos = useCallback(() => {
    if (!buttonRef.current) return
    const r = buttonRef.current.getBoundingClientRect()
    const spaceBelow = window.innerHeight - r.bottom - 8
    const spaceAbove = r.top - 8
    const openUp = spaceBelow < MAX_DROPDOWN_H && spaceAbove > spaceBelow
    if (openUp) {
      setPos({ bottom: window.innerHeight - r.top + 4, left: r.left, minWidth: r.width })
    } else {
      setPos({ top: r.bottom + 4, left: r.left, minWidth: r.width })
    }
  }, [])

  const handleToggle = () => {
    if (!open) updatePos()
    setOpen(o => !o)
  }

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      const t = e.target as Node
      if (
        buttonRef.current && !buttonRef.current.contains(t) &&
        dropdownRef.current && !dropdownRef.current.contains(t)
      ) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  useEffect(() => {
    if (!open) return
    const onScroll = () => updatePos()
    window.addEventListener('scroll', onScroll, true)
    return () => window.removeEventListener('scroll', onScroll, true)
  }, [open, updatePos])

  const handleSelect = (val: string) => {
    onChange(val)
    setOpen(false)
  }

  return (
    <div className={cn('relative shrink-0', className)}>
      <button
        ref={buttonRef}
        type="button"
        onClick={handleToggle}
        className={cn(
          'flex items-center gap-2 rounded-lg border transition-all cursor-pointer',
          'bg-white/[.04] border-white/[.08] text-white',
          'hover:bg-white/[.06] hover:border-white/[.14]',
          open && 'bg-white/[.06] border-white/[.14]',
          size === 'sm' ? 'h-8 px-2.5 text-xs' : 'h-9 px-3 text-sm',
        )}
      >
        <span className={cn('truncate max-w-[160px]', !selected && 'text-white/35')}>
          {selected ? selected.label : (placeholder ?? 'Select...')}
        </span>
        <ChevronDown
          size={12}
          className={cn('text-white/30 shrink-0 transition-transform', open && 'rotate-180')}
        />
      </button>

      {open && createPortal(
        <div
          ref={dropdownRef}
          className="fixed z-[100] rounded-xl border border-white/[.08] bg-[#0e0e1a] shadow-2xl shadow-black/60 overflow-hidden"
          style={{ top: pos.top, bottom: pos.bottom, left: pos.left, minWidth: pos.minWidth }}
        >
          <div className="py-1 max-h-64 overflow-y-auto scrollbar-thin">
            {options.map(opt => {
              const isSelected = opt.value === value
              return (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => handleSelect(opt.value)}
                  className={cn(
                    'w-full flex items-center gap-2.5 px-3 py-2 text-left transition-colors cursor-pointer',
                    'text-sm text-white/70 hover:text-white hover:bg-white/[.05]',
                    isSelected && 'text-accent bg-accent/[.08]',
                  )}
                >
                  <span className="flex-1 truncate">{opt.label}</span>
                  {isSelected && <Check size={13} className="text-accent shrink-0" />}
                </button>
              )
            })}
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}
