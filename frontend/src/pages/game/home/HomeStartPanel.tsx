import type { ReactNode } from 'react'
import { useState } from 'react'
import { Hash, Zap, Map } from 'lucide-react'
import { StreakFreezeBanner } from '@/components/game/StreakFreezeBanner'
import { StreakStatusStrip } from '@/components/game/StreakStatusStrip'
import { LegalModal } from '@/components/ui/LegalModal'
import { useTranslation } from '@/i18n'
import type { ProfileDto } from '@/types'
import { cn } from '@/utils/cn'
import { getProfileDifficultyRecommendation } from '../difficultyRecommendation'
import { HomeDifficultySlider } from './HomeDifficultySlider'
import { HomeGuestLegalNotice } from './HomeGuestLegalNotice'
import { HomeModeStatsStrip } from './HomeModeStatsStrip'
import { HomePanelShell } from './HomePanelShell'
import type { HomeDifficultyOption, HomeDifficultyValue } from './types'

interface HomeStartPanelProps {
  canPrimaryAction: boolean
  difficulty: HomeDifficultyValue
  guestActionError: string
  isAuthenticated: boolean
  isBusy: boolean
  legalReady: boolean
  onDifficultyChange: (value: HomeDifficultyValue) => void
  onLogin: () => void
  onPrimaryAction: () => void
  onRegister: () => void
  options: HomeDifficultyOption[]
  profile: ProfileDto | null
}

interface HeroPillProps {
  icon: ReactNode
  label: string
  borderColor: string
  bgColor: string
  textColor: string
}

function HeroPill({ icon, label, borderColor, bgColor, textColor }: HeroPillProps) {
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-[11px] font-semibold"
      style={{ borderColor, background: bgColor, color: textColor }}
    >
      {icon}
      {label}
    </span>
  )
}

export function HomeStartPanel({
  canPrimaryAction,
  difficulty,
  guestActionError,
  isAuthenticated,
  isBusy,
  legalReady,
  onDifficultyChange,
  onLogin,
  onPrimaryAction,
  onRegister,
  options,
  profile,
}: HomeStartPanelProps) {
  const { t } = useTranslation()
  const [legalModal, setLegalModal] = useState<'terms' | 'privacy' | null>(null)
  const selected = options.find((option) => option.value === difficulty) ?? options[0]
  const lockedModeSelected = !isAuthenticated && selected.locked
  const primaryDisabled = !canPrimaryAction || isBusy || (!isAuthenticated && !legalReady) || lockedModeSelected
  const recommendedDifficulty = getProfileDifficultyRecommendation(profile)

  const heroPills: HeroPillProps[] = [
    {
      icon: <Zap size={11} />,
      label: isAuthenticated ? t.home.heroPillAuth : t.home.heroPillGuest,
      borderColor: 'rgba(56,189,248,0.35)',
      bgColor: 'rgba(56,189,248,0.10)',
      textColor: 'rgba(125,211,252,0.92)',
    },
    {
      icon: <Map size={11} />,
      label: t.home.heroPillRealClues,
      borderColor: 'rgba(52,211,153,0.35)',
      bgColor: 'rgba(52,211,153,0.10)',
      textColor: 'rgba(110,231,183,0.92)',
    },
    {
      icon: <Hash size={11} />,
      label: `${selected.hintsPerCountry} ${t.home.heroCluesLabel}`,
      borderColor: 'rgba(251,191,36,0.35)',
      bgColor: 'rgba(251,191,36,0.09)',
      textColor: 'rgba(253,224,132,0.92)',
    },
  ]

  return (
    <HomePanelShell>
      <div className="mx-auto flex w-full max-w-[43rem] flex-1 flex-col">
        <div className="pb-4 text-center">
          <h1 className="text-[1.62rem] font-black leading-[1.07] tracking-[-0.03em] sm:text-[1.95rem]">
            <span className="mb-2 block text-[10px] font-bold uppercase leading-none tracking-[.22em] text-emerald-300/65">
              {t.home.eyebrow}
            </span>{' '}
            <span className="bg-gradient-to-br from-white via-white to-white/55 bg-clip-text text-transparent">
              {t.home.headline}
            </span>
          </h1>

          <p className="mx-auto mt-2 max-w-[34rem] text-sm leading-relaxed text-white/48 sm:text-[0.95rem]">
            {isAuthenticated ? t.home.difficultyBodyAuth : t.home.difficultyBody}
          </p>

          <div className="mt-3 flex flex-wrap items-center justify-center gap-1.5">
            {heroPills.map((pill) => (
              <HeroPill key={pill.label} {...pill} />
            ))}
          </div>

          {profile && profile.currentStreak > 0 && (
            <div className="mt-5 flex flex-wrap items-center justify-center gap-2 sm:mt-6 sm:flex-nowrap">
              <StreakStatusStrip profile={profile} variant="home" />
              <StreakFreezeBanner profile={profile} />
            </div>
          )}
        </div>

        <div className="my-[0.5625rem] flex items-center gap-3">
          <div className="h-px flex-1 bg-gradient-to-r from-transparent to-white/[.09]" />
          <p className="text-[0.56rem] font-bold uppercase tracking-[.32em] text-white/22">
            {t.home.difficultyTitle}
          </p>
          <div className="h-px flex-1 bg-gradient-to-l from-transparent to-white/[.09]" />
        </div>

        <HomeDifficultySlider
          onChange={onDifficultyChange}
          options={options}
          recommendedValue={recommendedDifficulty}
          value={difficulty}
        />

        <div className="mt-4">
          <HomeModeStatsStrip option={selected} />
        </div>

        {lockedModeSelected ? (
          <div
            className="mt-2.5 rounded-[1.6rem] border border-white/[.08] px-4 py-4"
            style={{ background: 'rgba(255,255,255,0.04)', boxShadow: 'inset 0 1px 0 rgba(255,255,255,.05)' }}
          >
            <p className="text-sm font-semibold text-white">{t.home.guestLockedTitle}</p>
            <p className="mt-1.5 text-sm leading-relaxed text-white/46">{t.home.guestLockedBody}</p>
            <div className="mt-3.5 grid gap-2 sm:grid-cols-2">
              <button
                type="button"
                onClick={onLogin}
                className="inline-flex min-h-[48px] items-center justify-center rounded-[1.1rem] border border-white/[.1] bg-white/[.05] px-4 text-sm font-semibold text-white/78 transition-all hover:bg-white/[.09] hover:text-white"
              >
                {t.nav.signIn}
              </button>
              <button
                type="button"
                onClick={onRegister}
                className="inline-flex min-h-[48px] items-center justify-center rounded-[1.1rem] px-4 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5 active:translate-y-0"
                style={{ background: selected.grad, boxShadow: selected.shadow }}
              >
                {t.nav.register}
              </button>
            </div>
          </div>
        ) : (
          <div className="relative mt-4">
            <div
              className="animate-glow-breathe pointer-events-none absolute inset-x-8 -bottom-1 top-2 rounded-[1.4rem] blur-2xl hidden sm:block"
              style={{ background: selected.grad }}
            />
            <button
              type="button"
              onClick={onPrimaryAction}
              disabled={primaryDisabled}
              className={cn(
                'relative inline-flex min-h-[60px] w-full items-center justify-center gap-3 rounded-[1.4rem] px-5 text-[1.05rem] font-bold text-white transition-all hover:-translate-y-0.5 hover:brightness-110 active:translate-y-0',
                'disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 disabled:hover:brightness-100'
              )}
              style={{
                background: selected.grad,
                boxShadow: `${selected.shadow}, inset 0 1px 0 rgba(255,255,255,.18)`,
              }}
            >
              {isBusy ? (
                <>
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                  {t.home.starting}
                </>
              ) : (
                t.home.playNow
              )}
            </button>
          </div>
        )}

        {!isAuthenticated && !lockedModeSelected ? (
          <p data-guest-only className="mt-2.5 text-center text-xs leading-relaxed text-white/36">
            <HomeGuestLegalNotice
              actionLabel={t.home.playNow}
              onOpenPrivacy={() => setLegalModal('privacy')}
              onOpenTerms={() => setLegalModal('terms')}
              template={t.home.guestLegalNotice}
            />
          </p>
        ) : null}

        {guestActionError ? (
          <div className="mt-3 rounded-2xl border border-error/20 bg-error/10 px-4 py-3 text-sm text-error">
            {guestActionError}
          </div>
        ) : null}
      </div>

      <LegalModal type={legalModal} onClose={() => setLegalModal(null)} />
    </HomePanelShell>
  )
}
