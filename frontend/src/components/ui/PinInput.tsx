import { useRef, useCallback } from 'react'
import { cn } from '@/utils/cn'

const DEFAULT_LENGTH = 6

interface PinInputProps {
  length?: number
  value: string
  onChange: (value: string) => void
  onComplete?: (value: string) => void
  disabled?: boolean
  error?: boolean
  autoFocus?: boolean
}

export function PinInput({
  length = DEFAULT_LENGTH,
  value,
  onChange,
  onComplete,
  disabled = false,
  error = false,
  autoFocus = true,
}: PinInputProps) {
  const inputRefs = useRef<(HTMLInputElement | null)[]>([])
  const digits = value.padEnd(length, '').split('').slice(0, length)

  const focusIndex = useCallback((idx: number) => {
    const clamped = Math.max(0, Math.min(idx, length - 1))
    inputRefs.current[clamped]?.focus()
  }, [length])

  const updateValue = useCallback((newDigits: string[]) => {
    const next = newDigits.join('').slice(0, length)
    onChange(next)
    if (next.length === length && onComplete) {
      setTimeout(() => onComplete(next), 0)
    }
  }, [length, onChange, onComplete])

  const handleChange = useCallback((idx: number, char: string) => {
    if (!/^\d$/.test(char)) return
    const next = [...digits]
    next[idx] = char
    updateValue(next)
    if (idx < length - 1) focusIndex(idx + 1)
  }, [digits, length, focusIndex, updateValue])

  const handleKeyDown = useCallback((idx: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      e.preventDefault()
      const next = [...digits]
      if (digits[idx]) {
        next[idx] = ''
        updateValue(next)
      } else if (idx > 0) {
        next[idx - 1] = ''
        updateValue(next)
        focusIndex(idx - 1)
      }
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault()
      focusIndex(idx - 1)
    } else if (e.key === 'ArrowRight') {
      e.preventDefault()
      focusIndex(idx + 1)
    }
  }, [digits, focusIndex, updateValue])

  const handlePaste = useCallback((e: React.ClipboardEvent) => {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length)
    if (!pasted) return
    onChange(pasted)
    if (pasted.length === length && onComplete) {
      setTimeout(() => onComplete(pasted), 0)
    }
    focusIndex(Math.min(pasted.length, length - 1))
  }, [length, onChange, onComplete, focusIndex])

  return (
    <div className="flex items-center justify-center gap-2.5">
      {Array.from({ length }).map((_, idx) => (
        <input
          key={idx}
          ref={el => { inputRefs.current[idx] = el }}
          type="text"
          inputMode="numeric"
          maxLength={1}
          value={digits[idx] || ''}
          disabled={disabled}
          autoFocus={autoFocus && idx === 0}
          onChange={e => {
            const char = e.target.value.slice(-1)
            if (char) handleChange(idx, char)
          }}
          onKeyDown={e => handleKeyDown(idx, e)}
          onPaste={handlePaste}
          onFocus={e => e.target.select()}
          className={cn(
            'w-11 h-13 sm:w-12 sm:h-14 rounded-xl text-center text-xl font-bold font-mono',
            'bg-white/[.04] border transition-all duration-200 outline-none',
            'text-white placeholder:text-white/10',
            disabled && 'opacity-40 cursor-not-allowed',
            error
              ? 'border-error/40 focus:border-error/60 focus:ring-1 focus:ring-error/20'
              : 'border-white/[.08] focus:border-accent/50 focus:ring-1 focus:ring-accent/20'
          )}
        />
      ))}
    </div>
  )
}
