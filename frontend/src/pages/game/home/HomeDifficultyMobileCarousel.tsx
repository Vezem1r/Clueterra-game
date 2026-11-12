import { useEffect, useRef } from 'react'
import { Lock } from 'lucide-react'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import type { HomeDifficultyOption, HomeDifficultyValue } from './types'

interface HomeDifficultyMobileCarouselProps {
  onChange: (value: HomeDifficultyValue) => void
  options: HomeDifficultyOption[]
  recommendedValue?: HomeDifficultyValue | null
  value: HomeDifficultyValue
}

export function HomeDifficultyMobileCarousel({
  onChange,
  options,
  recommendedValue,
  value,
}: HomeDifficultyMobileCarouselProps) {
  const { t } = useTranslation()
  const trackRef = useRef<HTMLDivElement | null>(null)
  const scrollTimeoutRef = useRef<number | null>(null)

  useEffect(() => {
    const track = trackRef.current
    if (!track) return

    requestAnimationFrame(() => {
      if (track.scrollWidth <= track.clientWidth + 4) return

      const nextIndex = options.findIndex((option) => option.value === value)
      const nextSlide = track.children.item(nextIndex) as HTMLElement | null
      if (!nextSlide) return

      const targetLeft = nextSlide.offsetLeft
      if (Math.abs(track.scrollLeft - targetLeft) < 4) return

      track.scrollTo({ left: targetLeft, behavior: 'smooth' })
    })
  }, [options, value])

  useEffect(() => () => {
    if (scrollTimeoutRef.current !== null) {
      window.clearTimeout(scrollTimeoutRef.current)
    }
  }, [])

  const handleScroll = () => {
    const track = trackRef.current
    if (!track) return

    if (scrollTimeoutRef.current !== null) {
      window.clearTimeout(scrollTimeoutRef.current)
    }

    scrollTimeoutRef.current = window.setTimeout(() => {
      requestAnimationFrame(() => {
        if (track.scrollWidth <= track.clientWidth + 4) return

        const slides = Array.from(track.children) as HTMLElement[]
        const viewportCenter = track.scrollLeft + track.clientWidth / 2

        const closest = slides.reduce<{ distance: number; index: number } | null>((best, slide, index) => {
          const center = slide.offsetLeft + slide.offsetWidth / 2
          const distance = Math.abs(center - viewportCenter)
          if (!best || distance < best.distance) {
            return { distance, index }
          }
          return best
        }, null)

        const nextValue = closest ? options[closest.index]?.value : null
        if (nextValue && nextValue !== value) {
          onChange(nextValue)
        }
      })
    }, 100)
  }

  return (
    <div className="sm:hidden">
      <div
        ref={trackRef}
        onScroll={handleScroll}
        className="flex snap-x snap-mandatory overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {options.map((option) => {
          const active = option.value === value
          const recommended = option.value === recommendedValue && !option.locked

          return (
            <div key={option.value} className="min-w-0 w-full shrink-0 snap-start">
              <button
                type="button"
                onClick={() => onChange(option.value)}
                aria-pressed={active}
                className="relative flex min-h-[12rem] w-full min-w-0 max-w-full flex-col overflow-hidden rounded-[1.65rem] border px-5 py-5 text-left transition-all duration-200"
                style={{
                  background: active
                    ? 'rgba(255,255,255,0.075)'
                    : recommended
                      ? 'rgba(255,255,255,0.052)'
                      : 'rgba(255,255,255,0.03)',
                  borderColor: active || recommended ? `${option.accent}55` : 'rgba(255,255,255,0.07)',
                  boxShadow: active
                    ? `0 0 40px ${option.accent}22, inset 0 1px 0 rgba(255,255,255,.09)`
                    : recommended
                      ? `0 0 28px ${option.accent}1f, inset 0 1px 0 rgba(255,255,255,.06)`
                      : 'inset 0 1px 0 rgba(255,255,255,.04)',
                }}
              >
                <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(170deg,rgba(255,255,255,.05),transparent_50%)]" />
                <div
                  className="pointer-events-none absolute inset-x-0 top-0 h-px"
                  style={{
                    background: active
                      ? `linear-gradient(90deg, transparent, ${option.accent}dd, transparent)`
                      : 'linear-gradient(90deg, transparent, rgba(255,255,255,.1), transparent)',
                  }}
                />
                {null}

                <div className="relative flex min-w-0 items-start justify-between gap-3">
                  <div
                    className="flex h-[3.2rem] w-[3.2rem] shrink-0 items-center justify-center rounded-[1.1rem] border"
                    style={{
                      background: active
                        ? option.grad
                        : 'linear-gradient(135deg, rgba(255,255,255,.08), rgba(10,14,24,.3))',
                      borderColor: active ? `${option.accent}55` : 'rgba(255,255,255,.09)',
                      boxShadow: active ? `0 6px 20px ${option.accent}40` : 'none',
                    }}
                  >
                    <option.Icon
                      size={21}
                      className={active && !option.locked ? 'text-white' : 'text-white/55'}
                    />
                  </div>

                  {option.locked ? (
                    <div className="inline-flex h-7 w-7 items-center justify-center rounded-full border border-white/[.1] bg-white/[.06] text-white/40">
                      <Lock size={13} />
                    </div>
                  ) : (
                    <div className="flex min-w-0 max-w-[58%] flex-wrap justify-end gap-1.5">
                      {recommended ? (
                        <span
                          className="max-w-full truncate rounded-full border px-2 py-0.5 text-[8px] font-black uppercase tracking-[.1em]"
                          style={{
                            borderColor: `${option.accent}55`,
                            background: `${option.accent}18`,
                            color: option.accent,
                          }}
                        >
                          {t.home.recommended}
                        </span>
                      ) : null}
                      <span
                        className="max-w-full truncate rounded-full border border-white/[.1] bg-white/[.05] px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[.16em] text-white/42"
                      >
                        {option.goal}
                      </span>
                    </div>
                  )}
                </div>

                <div className="relative mt-4 flex-1">
                  <p className={cn('text-[1.55rem] font-black leading-tight tracking-tight', active ? 'text-white' : 'text-white/65')}>
                    {option.label}
                  </p>
                  <p className={cn('mt-1.5 text-[0.83rem] leading-relaxed', active ? 'text-white/58' : 'text-white/34')}>
                    {option.locked ? t.home.signInToUnlock : option.description}
                  </p>
                </div>

                <div className="relative mt-4 flex min-w-0 items-center justify-between gap-3 border-t border-white/[.07] pt-3.5 text-[11px]">
                  <span className={cn('min-w-0 truncate font-semibold', active ? 'text-white/68' : 'text-white/38')}>
                    {option.countries} {t.home.modeStatCountries.toLowerCase()}
                  </span>
                  <span className={cn('min-w-0 truncate text-right', active ? 'text-white/48' : 'text-white/26')}>
                    {option.totalHints} {t.home.modeStatTotalClues.toLowerCase()}
                  </span>
                </div>
              </button>
            </div>
          )
        })}
      </div>

      <div className="mt-1 flex items-center justify-center">
        {options.map((option) => {
          const active = option.value === value

          return (
            <button
              key={`dot-${option.value}`}
              type="button"
              onClick={() => onChange(option.value)}
              aria-label={option.label}
              aria-pressed={active}
              className="flex h-6 min-w-6 items-center justify-center px-2"
            >
              <span
                aria-hidden="true"
                className={cn('rounded-full transition-all duration-200', active ? 'h-2 w-8' : 'h-2 w-2')}
                style={{
                  background: active ? option.grad : 'rgba(255,255,255,0.16)',
                  boxShadow: active ? `0 0 10px ${option.accent}80` : 'none',
                }}
              />
            </button>
          )
        })}
      </div>
    </div>
  )
}
