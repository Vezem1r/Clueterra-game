import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import { createPortal } from 'react-dom'
import { Calendar, ChevronDown, ChevronLeft, ChevronRight, X } from 'lucide-react'
import {
  format, parse, startOfMonth, endOfMonth, startOfWeek, endOfWeek,
  addMonths, subMonths, isSameDay, isSameMonth, isToday,
  eachDayOfInterval,
} from 'date-fns'
import { cn } from '@/utils/cn'

interface DatePickerProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  className?: string
  size?: 'sm' | 'md'
  clearable?: boolean
}

export function DatePicker({
  value,
  onChange,
  placeholder = 'Select date',
  className,
  size = 'md',
  clearable = false,
}: DatePickerProps) {
  const [open, setOpen] = useState(false)
  const [viewDate, setViewDate] = useState(() => new Date())
  const buttonRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const [pos, setPos] = useState<{ top?: number; bottom?: number; left: number; minWidth: number }>({ left: 0, minWidth: 0 })

  const parsed = value ? parse(value, 'yyyy-MM-dd', new Date()) : null
  const displayText = parsed ? format(parsed, 'MMM d, yyyy') : null

  const MAX_DROPDOWN_H = 340

  const updatePos = useCallback(() => {
    if (!buttonRef.current) return
    const r = buttonRef.current.getBoundingClientRect()
    const spaceBelow = window.innerHeight - r.bottom - 8
    const spaceAbove = r.top - 8
    const openUp = spaceBelow < MAX_DROPDOWN_H && spaceAbove > spaceBelow
    const minW = Math.max(r.width, 280)
    if (openUp) {
      setPos({ bottom: window.innerHeight - r.top + 4, left: r.left, minWidth: minW })
    } else {
      setPos({ top: r.bottom + 4, left: r.left, minWidth: minW })
    }
  }, [])

  const handleToggle = () => {
    if (!open) {
      updatePos()
      setViewDate(parsed ?? new Date())
    }
    setOpen(o => !o)
  }

  // Click-outside
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

  // Scroll repositioning
  useEffect(() => {
    if (!open) return
    const onScroll = () => updatePos()
    window.addEventListener('scroll', onScroll, true)
    return () => window.removeEventListener('scroll', onScroll, true)
  }, [open, updatePos])

  // Escape key
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  const days = useMemo(() => {
    const monthStart = startOfMonth(viewDate)
    const monthEnd = endOfMonth(viewDate)
    const calStart = startOfWeek(monthStart, { weekStartsOn: 1 })
    const calEnd = endOfWeek(monthEnd, { weekStartsOn: 1 })
    return eachDayOfInterval({ start: calStart, end: calEnd })
  }, [viewDate])

  const handleSelect = (day: Date) => {
    onChange(format(day, 'yyyy-MM-dd'))
    setOpen(false)
  }

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation()
    onChange('')
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
        <Calendar size={size === 'sm' ? 12 : 14} className="text-white/30 shrink-0" />
        <span className={cn('truncate', !displayText && 'text-white/35')}>
          {displayText ?? placeholder}
        </span>
        {clearable && value ? (
          <X
            size={12}
            className="text-white/30 shrink-0 hover:text-white/60 transition-colors"
            onClick={handleClear}
          />
        ) : (
          <ChevronDown
            size={12}
            className={cn('text-white/30 shrink-0 transition-transform', open && 'rotate-180')}
          />
        )}
      </button>

      {open && createPortal(
        <div
          ref={dropdownRef}
          className="fixed z-[100] rounded-xl border border-white/[.08] bg-[#0e0e1a] shadow-2xl shadow-black/60 overflow-hidden"
          style={{ top: pos.top, bottom: pos.bottom, left: pos.left, minWidth: pos.minWidth }}
        >
          <div className="p-3">
            {/* Header — month navigation */}
            <div className="flex items-center justify-between mb-3">
              <button
                type="button"
                onClick={() => setViewDate(d => subMonths(d, 1))}
                className="p-1.5 rounded-lg text-white/40 hover:text-white/70 hover:bg-white/[.06] transition-colors"
              >
                <ChevronLeft size={14} />
              </button>
              <span className="text-sm font-medium text-white/70">
                {format(viewDate, 'MMMM yyyy')}
              </span>
              <button
                type="button"
                onClick={() => setViewDate(d => addMonths(d, 1))}
                className="p-1.5 rounded-lg text-white/40 hover:text-white/70 hover:bg-white/[.06] transition-colors"
              >
                <ChevronRight size={14} />
              </button>
            </div>

            {/* Weekday labels */}
            <div className="grid grid-cols-7 gap-0.5 mb-1">
              {['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'].map(d => (
                <div key={d} className="h-8 flex items-center justify-center text-[10px] text-white/25 uppercase font-medium">
                  {d}
                </div>
              ))}
            </div>

            {/* Day grid */}
            <div className="grid grid-cols-7 gap-0.5">
              {days.map(day => {
                const inMonth = isSameMonth(day, viewDate)
                const selected = parsed ? isSameDay(day, parsed) : false
                const today = isToday(day)

                return (
                  <button
                    key={day.toISOString()}
                    type="button"
                    onClick={() => handleSelect(day)}
                    className={cn(
                      'h-8 rounded-lg text-xs transition-colors cursor-pointer',
                      'flex items-center justify-center',
                      selected
                        ? 'bg-accent text-white font-medium'
                        : today
                          ? 'ring-1 ring-accent/50 text-accent/80 hover:bg-white/[.06]'
                          : inMonth
                            ? 'text-white/70 hover:bg-white/[.06]'
                            : 'text-white/20 hover:bg-white/[.04]',
                    )}
                  >
                    {format(day, 'd')}
                  </button>
                )
              })}
            </div>
          </div>
        </div>,
        document.body,
      )}
    </div>
  )
}
