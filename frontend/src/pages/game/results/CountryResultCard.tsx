import { useState } from 'react'
import { motion } from 'framer-motion'
import { Check, Info, Lightbulb, Maximize2, Trophy, X } from 'lucide-react'
import { useTranslation } from '@/i18n'
import { HintImageModal } from '@/components/game/HintImageModal'
import { CountryFlag } from '@/components/ui/CountryFlag'
import type { GuessAttempt, Hint, ResultCountry } from '@/types'
import { getCountryName } from '@/utils/country'
import { useSettingsStore, type Language } from '@/store/settingsStore'
import {
  CARD_RADIUS_INNER,
  CARD_RADIUS_OUTER,
  LABEL_TEXT,
  LABEL_TRACKING,
  STAGGER_DELAY_MS,
  SURFACE_INLAY,
  SURFACE_PANEL,
} from './constants'

interface CountryResultCardProps {
  country: ResultCountry
  index: number
  onHintClick: (hint: Hint) => void
}

export function CountryResultCard({ country, index, onHintClick }: CountryResultCardProps) {
  const { t } = useTranslation()
  const language = useSettingsStore((state) => state.language)

  return (
    <motion.article
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: (STAGGER_DELAY_MS * index) / 1000 }}
      className={`${CARD_RADIUS_OUTER} ${SURFACE_PANEL} overflow-hidden`}
    >
      <div className="p-5 sm:p-6 lg:p-7">
        <div className="flex flex-col lg:flex-row lg:items-start gap-6">
          <div className="lg:w-72 shrink-0">
            <div className="flex items-center gap-4 mb-5">
              <CountryFlag country={country.correctCountry} size="lg" />
              <div className="min-w-0">
                <p className={`${LABEL_TEXT} ${LABEL_TRACKING} text-white/25 mb-1`}>
                  #{country.order}
                </p>
                <h2 className="text-xl sm:text-2xl font-bold truncate">
                  {getCountryName(country.correctCountry, language)}
                </h2>
                <div
                  className={`inline-flex items-center gap-1.5 mt-2 text-xs font-semibold ${
                    country.guessed ? 'text-emerald-300/80' : 'text-rose-300/80'
                  }`}
                >
                  {country.guessed ? <Check size={13} /> : <X size={13} />}
                  {country.guessed ? t.results.correct : t.results.wrong}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className={`${CARD_RADIUS_INNER} ${SURFACE_INLAY} p-3`}>
                <p className="text-lg font-bold">{country.xpEarned}</p>
                <p className={`${LABEL_TEXT} ${LABEL_TRACKING} text-white/25`}>XP</p>
              </div>
              <div className={`${CARD_RADIUS_INNER} ${SURFACE_INLAY} p-3`}>
                <p className="text-lg font-bold">
                  {country.hintsSeen}/{country.totalHints}
                </p>
                <p className={`${LABEL_TEXT} ${LABEL_TRACKING} text-white/25`}>
                  {t.results.cluesSeen}
                </p>
              </div>
            </div>
          </div>

          <div className="flex-1 grid xl:grid-cols-2 gap-5 min-w-0">
            <HintsColumn hints={country.hints} onHintClick={onHintClick} />
            <AttemptsColumn country={country} attempts={country.attempts} language={language} />
          </div>
        </div>
      </div>
    </motion.article>
  )
}

function HintsColumn({ hints, onHintClick }: { hints: Hint[]; onHintClick: (hint: Hint) => void }) {
  const { t } = useTranslation()
  const [previewHint, setPreviewHint] = useState<Hint | null>(null)

  return (
    <section>
      <header className="flex items-center gap-2 mb-3 text-white/45">
        <Lightbulb size={15} />
        <h3 className="text-sm font-semibold">{t.play.clues}</h3>
      </header>
      <div className="space-y-3">
        {hints.length === 0 ? (
          <p className="text-sm text-white/25">{t.results.noClues}</p>
        ) : null}
        {hints.map((hint, hintIndex) => (
          <div
            key={hint.id}
            className={`relative ${CARD_RADIUS_INNER} ${SURFACE_INLAY} p-4 border border-white/[.05] hover:border-white/[.1] transition-colors`}
          >
            <button
              type="button"
              onClick={() => onHintClick(hint)}
              className="absolute top-3 right-3 p-1.5 rounded-lg text-white/25 hover:text-white/60 hover:bg-white/[.04] transition-colors cursor-pointer"
              aria-label={t.play.hintDetails}
            >
              <Info size={14} />
            </button>
            <div className="flex gap-3">
              {hint.imageUrl ? (
                <button
                  type="button"
                  onClick={() => setPreviewHint(hint)}
                  aria-label={t.play.openImage}
                  title={t.play.openImage}
                  className="group/image relative h-16 w-16 shrink-0 cursor-zoom-in overflow-hidden rounded-xl border border-white/[.08] bg-white/[.04]"
                >
                  <img
                    src={hint.imageUrl}
                    alt=""
                    loading="lazy"
                    className="h-full w-full object-cover transition-transform group-hover/image:scale-105"
                  />
                  <span className="absolute inset-0 flex items-center justify-center bg-black/35 text-white/75 opacity-0 transition-opacity group-hover/image:opacity-100 group-focus-visible/image:opacity-100">
                    <Maximize2 size={16} />
                  </span>
                </button>
              ) : null}
              <div className="min-w-0 pr-9">
                <p className={`${LABEL_TEXT} ${LABEL_TRACKING} text-white/20 mb-1`}>
                  {t.play.clues} {hintIndex + 1}
                </p>
                <p className="text-sm leading-relaxed text-white/70">{hint.hintText}</p>
              </div>
            </div>
          </div>
        ))}
      </div>
      {previewHint?.imageUrl && (
        <HintImageModal
          src={previewHint.imageUrl}
          isOpen
          isPanorama={previewHint.type === 'PANORAMA'}
          onInfoClick={() => {
            setPreviewHint(null)
            onHintClick(previewHint)
          }}
          onClose={() => setPreviewHint(null)}
        />
      )}
    </section>
  )
}

interface AttemptsColumnProps {
  country: ResultCountry
  attempts: GuessAttempt[]
  language: Language
}

function AttemptsColumn({ country, attempts, language }: AttemptsColumnProps) {
  const { t } = useTranslation()

  return (
    <section>
      <header className="flex items-center gap-2 mb-3 text-white/45">
        <Trophy size={15} />
        <h3 className="text-sm font-semibold">{t.results.attempts}</h3>
      </header>
      <div className="space-y-3">
        {attempts.length === 0 ? (
          <p className="text-sm text-white/25">{t.results.noAttempts}</p>
        ) : null}
        {attempts.map((attempt) => (
          <div
            key={`${country.id}-${attempt.attemptNumber}`}
            className={`flex items-center gap-3 ${CARD_RADIUS_INNER} ${SURFACE_INLAY} p-3`}
          >
            <div
              className={`w-8 h-8 rounded-xl flex items-center justify-center ${
                attempt.correct ? 'bg-emerald-400/15 text-emerald-300' : 'bg-rose-400/15 text-rose-300'
              }`}
            >
              {attempt.correct ? <Check size={15} /> : <X size={15} />}
            </div>
            <CountryFlag country={attempt.guessedCountry} size="xs" />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-white/75 truncate">
                {getCountryName(attempt.guessedCountry, language)}
              </p>
              <p className="text-[11px] text-white/25">
                {t.results.attempt} {attempt.attemptNumber} · {attempt.hintsSeen}{' '}
                {t.results.cluesSeen.toLowerCase()}
              </p>
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
