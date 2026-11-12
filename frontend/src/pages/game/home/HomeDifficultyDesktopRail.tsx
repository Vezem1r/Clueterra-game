import { Lock } from 'lucide-react'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import type { HomeDifficultyOption, HomeDifficultyValue } from './types'

interface HomeDifficultyDesktopRailProps {
  onChange: (value: HomeDifficultyValue) => void
  options: HomeDifficultyOption[]
  recommendedValue?: HomeDifficultyValue | null
  value: HomeDifficultyValue
}

export function HomeDifficultyDesktopRail({
  onChange,
  options,
  recommendedValue,
  value,
}: HomeDifficultyDesktopRailProps) {
  const { t } = useTranslation()

  return (
    <div className="hidden grid-cols-3 gap-3 sm:grid">
      {options.map((option) => {
        const active = option.value === value
        const recommended = option.value === recommendedValue && !option.locked

        return (
          <button
            key={option.value}
            type="button"
            onClick={() => onChange(option.value)}
            aria-pressed={active}
            className={cn(
              'group relative flex min-h-[9.5rem] flex-col items-center justify-center overflow-hidden rounded-[1.65rem] border px-4 py-5 text-center transition-all duration-200',
              active ? '-translate-y-[2px] scale-[1.01]' : 'hover:-translate-y-0.5 hover:scale-[1.005]'
            )}
            style={{
              background: active
                ? 'rgba(255,255,255,0.085)'
                : recommended
                  ? 'rgba(255,255,255,0.058)'
                  : 'rgba(255,255,255,0.048)',
              borderColor: active || recommended ? `${option.accent}66` : 'rgba(255,255,255,0.11)',
              boxShadow: active
                ? `0 0 40px ${option.accent}28, inset 0 1px 0 rgba(255,255,255,.11)`
                : recommended
                  ? `0 0 28px ${option.accent}1f, inset 0 1px 0 rgba(255,255,255,.07)`
                  : 'inset 0 1px 0 rgba(255,255,255,.05)',
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

            {active ? (
              <div
                className="animate-card-glow pointer-events-none absolute -right-6 -top-6 h-28 w-28 rounded-full blur-3xl"
                style={{ background: option.accent, opacity: 0.45 }}
              />
            ) : null}

            {option.locked ? (
              <div className="absolute right-3 top-3 inline-flex h-7 w-7 items-center justify-center rounded-full border border-white/[.1] bg-white/[.06] text-white/40">
                <Lock size={13} />
              </div>
            ) : null}

            {recommended ? (
              <div
                className="relative z-10 mb-2 max-w-full rounded-full border px-2 py-0.5 text-[8px] font-black uppercase tracking-[.1em]"
                style={{
                  borderColor: `${option.accent}55`,
                  background: `${option.accent}18`,
                  color: option.accent,
                }}
              >
                {t.home.recommended}
              </div>
            ) : null}

            <div
              className="flex h-[2.8rem] w-[2.8rem] items-center justify-center rounded-[0.9rem] border"
              style={{
                background: active
                  ? option.grad
                  : 'linear-gradient(135deg, rgba(255,255,255,.08), rgba(10,14,24,.3))',
                borderColor: active ? `${option.accent}55` : 'rgba(255,255,255,.09)',
                boxShadow: active ? `0 6px 20px ${option.accent}40` : 'none',
              }}
            >
              <option.Icon
                size={20}
                className={active && !option.locked ? 'text-white' : 'text-white/50'}
              />
            </div>

            <p className={cn('mt-3 text-[1.05rem] font-bold tracking-tight', active ? 'text-white' : 'text-white/58')}>
              {option.label}
            </p>
            <p className={cn('mt-1.5 text-[0.76rem] leading-relaxed', active ? 'text-white/60' : 'text-white/40')}>
              {option.locked ? t.home.signInToUnlock : option.description}
            </p>
          </button>
        )
      })}
    </div>
  )
}
