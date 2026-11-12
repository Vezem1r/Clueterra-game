import { useState } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'

import { CountrySearch } from '@/components/game/CountrySearch'
import { useTranslation } from '@/i18n'
import type { Country } from '@/types'
import { getCountryName, getFlagUrlByIso2 } from '@/utils/country'
import { DEMO_ANSWER_ISO, type ClueterraDemoRound } from './useClueterraDemoRound'

interface GuessFeedback {
  correct: boolean
  country: Country
}

export function ClueterraGuessDemo({ round }: { round: ClueterraDemoRound }) {
  const { t, language } = useTranslation()
  const [feedback, setFeedback] = useState<GuessFeedback | null>(null)
  const showFeedback = feedback && (feedback.correct ? round.solved : true)

  const handleSelect = (country: Country) => {
    const correct = country.isoAlpha2.toUpperCase() === DEMO_ANSWER_ISO
    if (correct) round.markSolved()
    setFeedback({ correct, country })
  }

  return (
    <div>
      <CountrySearch onSelect={handleSelect} disabled={round.solved} />

      {showFeedback && feedback && (
        <div
          role="status"
          className={
            feedback.correct
              ? 'mt-4 flex items-center gap-3 rounded-2xl border border-emerald-300/20 bg-emerald-300/[.07] px-4 py-3.5'
              : 'mt-4 flex items-center gap-3 rounded-2xl border border-rose-300/15 bg-rose-400/[.06] px-4 py-3.5'
          }
        >
          {feedback.correct ? (
            <>
              <img
                src={getFlagUrlByIso2(DEMO_ANSWER_ISO, 'w80') ?? undefined}
                alt=""
                className="h-6 w-9 shrink-0 rounded-[4px] object-cover shadow"
              />
              <CheckCircle2 size={18} className="shrink-0 text-emerald-300" />
              <p className="text-sm font-semibold text-emerald-100">
                {t.howToPage.guessCorrect.replace('{country}', getCountryName(feedback.country, language))}
              </p>
            </>
          ) : (
            <>
              <XCircle size={18} className="shrink-0 text-rose-300" />
              <p className="text-sm text-rose-100/80">
                {t.howToPage.guessWrong.replace('{country}', getCountryName(feedback.country, language))}
              </p>
            </>
          )}
        </div>
      )}
    </div>
  )
}
