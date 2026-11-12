import { memo, useState, useRef, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Search, MapPin } from 'lucide-react'
import type { Country } from '@/types'
import { useCountrySearch } from '@/hooks/useCountrySearch'
import { useDelayedBusy } from '@/hooks/useDelayedBusy'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'

const DEBOUNCE_MS = 300

interface CountrySearchProps {
  dropdownDirection?: 'down' | 'up'
  onSelect: (country: Country) => void
  disabled?: boolean
}

export const CountrySearch = memo(function CountrySearch({ onSelect, disabled, dropdownDirection = 'down' }: CountrySearchProps) {
  const { t } = useTranslation()
  const [focused, setFocused] = useState(false)
  const [caretVisible, setCaretVisible] = useState(false)
  const [inputValue, setInputValue] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const { searchQuery, setSearchQuery, searchResults, isSearching, clearSearch } = useCountrySearch()
  const showSubmitBusy = useDelayedBusy(Boolean(disabled))
  const opensUpward = dropdownDirection === 'up'
  const dropdownMotionY = opensUpward ? 8 : -8
  const keepDropdownVisible = inputValue.trim().length >= 2 && !disabled

  useEffect(() => {
    const timer = setTimeout(() => setSearchQuery(inputValue), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [inputValue, setSearchQuery])

  const handleSelect = (country: Country) => {
    onSelect(country)
    setInputValue('')
    clearSearch()
    inputRef.current?.blur()
  }

  return (
    <div className="relative w-full">
      {/* input */}
      <div className={cn(
        'relative flex items-center gap-3 p-3.5 sm:gap-4 sm:p-5 lg:p-6 rounded-2xl',
        'bg-white/[.025] border transition-[border-color,background-color,box-shadow] duration-200',
        focused ? 'border-accent/40 bg-white/[.04] shadow-[0_0_20px_rgba(124,92,252,.12)]' : 'border-white/[.06]',
        disabled && 'cursor-wait'
      )}>
        <Search
          size={18}
          className={cn('shrink-0 transition-colors sm:w-5 sm:h-5', focused ? 'text-accent' : 'text-white/25')}
        />
        <input
          ref={inputRef}
          type="text"
          value={inputValue}
          onChange={e => setInputValue(e.target.value)}
          onFocus={() => { setFocused(true); setCaretVisible(true) }}
          onBlur={() => { setCaretVisible(false); setTimeout(() => setFocused(false), 200) }}
          onKeyDown={e => { if (e.key === 'Enter' && searchResults.length > 0) handleSelect(searchResults[0]) }}
          placeholder={t.play.searchCountry}
          disabled={disabled}
          style={{ caretColor: caretVisible ? undefined : 'transparent' }}
          className="flex-1 bg-transparent text-white text-base lg:text-lg
                     placeholder:text-white/20 focus:outline-none
                     disabled:cursor-not-allowed"
        />
        <div className="h-4 w-4 shrink-0" aria-hidden>
          {(isSearching || showSubmitBusy) && (
            <div className="h-4 w-4 animate-spin rounded-full border-2 border-white/15 border-t-accent" />
          )}
        </div>
      </div>

      {/* results */}
      <AnimatePresence>
        {keepDropdownVisible && searchResults.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: dropdownMotionY }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: dropdownMotionY }}
            transition={{ duration: 0.15 }}
            className={cn(
              'absolute left-0 z-30 w-full p-1.5 rounded-2xl',
              'border border-white/[.08] bg-surface-elevated shadow-2xl shadow-black/50',
              'max-h-[min(22rem,calc(100dvh-9rem))] overflow-y-auto',
              opensUpward ? 'bottom-full mb-2 origin-bottom' : 'mt-3 origin-top'
            )}
          >
            {searchResults.slice(0, 6).map(country => (
              <button
                key={country.id}
                onClick={() => handleSelect(country)}
                className={cn(
                  'w-full flex items-center gap-3 p-3 sm:gap-4 sm:p-4 rounded-xl',
                  'text-left transition-[background-color,transform] duration-150 group',
                  'hover:bg-white/[.06] active:scale-[.985] active:bg-accent/10'
                )}
              >
                <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-lg sm:rounded-xl bg-white/[.05] flex items-center justify-center text-lg sm:text-xl shrink-0">
                  {country.flagEmoji || <MapPin size={14} className="text-white/30" />}
                </div>
                <div className="flex-1 min-w-0">
                  <p className={cn(
                    'text-sm font-medium truncate transition-colors',
                    'text-white/80 group-hover:text-accent'
                  )}>
                    {country.nameEn}
                  </p>
                  <p className="text-[11px] text-white/30 truncate">{country.region}</p>
                </div>
                <span className="text-[10px] text-white/20 font-mono uppercase shrink-0">
                  {country.isoAlpha2}
                </span>
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>

      {/* empty state */}
      <AnimatePresence>
        {keepDropdownVisible && searchQuery.length >= 2 && searchResults.length === 0 && !isSearching && (
          <motion.div
            initial={{ opacity: 0, y: dropdownMotionY }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: dropdownMotionY }}
            className={cn(
              'absolute left-0 z-30 w-full rounded-2xl border border-white/[.08] bg-surface-elevated p-6 text-center',
              opensUpward ? 'bottom-full mb-2 origin-bottom' : 'mt-3 origin-top'
            )}
          >
            <p className="text-white/30 text-sm">{t.common.noResults}</p>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
})
