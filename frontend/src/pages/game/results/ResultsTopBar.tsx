import { ArrowLeft } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from '@/i18n'
import { DifficultyBadge } from '@/components/game/DifficultyBadge'
import type { GameResults } from '@/types'

interface ResultsTopBarProps {
  guessedCountries: number
  level: GameResults['level']
  totalCountries: number
  totalXpEarned: number
}

export function ResultsTopBar({ level, totalXpEarned, guessedCountries, totalCountries }: ResultsTopBarProps) {
  const navigate = useNavigate()
  const { t } = useTranslation()

  return (
    <div className="fixed inset-x-0 top-0 z-30 pointer-events-none">
      <div className="max-w-6xl mx-auto px-5 sm:px-8 lg:px-12 pt-4 sm:pt-5">
        <div className="pointer-events-auto flex items-center justify-between gap-3 rounded-[1.35rem] border border-white/[.08] bg-[linear-gradient(180deg,rgba(7,11,19,.92),rgba(7,11,19,.88))] md:bg-[linear-gradient(180deg,rgba(7,11,19,.56),rgba(7,11,19,.36))] md:backdrop-blur-2xl md:backdrop-saturate-150 shadow-[0_12px_40px_rgba(0,0,0,.22)] px-3 sm:px-4 py-3">
          <button
            type="button"
            onClick={() => navigate('/')}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-xl text-white/45 hover:text-white/80 hover:bg-white/[.05] transition-colors cursor-pointer"
          >
            <ArrowLeft size={18} />
            <span className="text-sm">{t.common.back}</span>
          </button>
          <div className="flex items-center gap-2 sm:gap-3">
            <div className="hidden sm:flex items-center gap-2 rounded-xl border border-white/[.08] bg-white/[.04] px-3 py-2 text-xs text-white/60">
              <span className="font-semibold text-white">{totalXpEarned}</span>
              <span>{t.play.points}</span>
            </div>
            <div className="hidden sm:flex items-center gap-2 rounded-xl border border-white/[.08] bg-white/[.04] px-3 py-2 text-xs text-white/60">
              <span className="font-semibold text-white">{guessedCountries}/{totalCountries}</span>
              <span>{t.results.countries.toLowerCase()}</span>
            </div>
            <DifficultyBadge level={level} size="sm" />
          </div>
        </div>
      </div>
    </div>
  )
}
