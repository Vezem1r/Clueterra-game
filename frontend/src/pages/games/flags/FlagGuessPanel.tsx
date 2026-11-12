import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Maximize2 } from 'lucide-react'
import type { FlagQuestion } from '@/api/flags'
import type { SilhouetteLocale } from '@/api/silhouettes'
import { Modal } from '@/components/ui/Modal'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameActions } from '../globe-game/GlobeGameActions'
import { GlobeGameAnswerField } from '../globe-game/GlobeGameAnswerField'
import { GlobeGameMisses } from '../globe-game/GlobeGameMisses'
import { GlobeGamePanel } from '../globe-game/GlobeGamePanel'
import { GlobeGameRoundBar } from '../globe-game/GlobeGameRoundBar'
import { GlobeGameSuggestions } from '../globe-game/GlobeGameSuggestions'
import { SILHOUETTE_TIER_BADGE } from '../silhouettes/silhouetteGameConstants'
import type { SilhouetteCountryOption } from '../silhouettes/silhouetteWorld'
import { FlagCard } from './FlagCard'
import { FlagClues } from './FlagClues'
import { FLAG_PERCENT } from './flagGameConstants'

interface FlagGuessPanelProps {
  copy: TranslationKeys['flagGame']
  tiers: Record<number, string>
  regions: Record<string, string>
  language: SilhouetteLocale
  question: FlagQuestion
  guess: string
  suggestions: SilhouetteCountryOption[]
  wrongGuesses: SilhouetteCountryOption[]
  busy: boolean
  onGuessChange: (value: string) => void
  onPick: (country: SilhouetteCountryOption) => void
  onReveal: () => void
  onSkip: () => void
  onShowAnswer: () => void
}

const SUGGESTIONS_ID = 'flag-suggestions'

export function FlagGuessPanel({
  copy, tiers, regions, language, question, guess, suggestions, wrongGuesses, busy,
  onGuessChange, onPick, onReveal, onSkip, onShowAnswer,
}: FlagGuessPanelProps) {
  const [active, setActive] = useState(0)
  const [needsPick, setNeedsPick] = useState(false)
  const [zoomed, setZoomed] = useState(false)
  const wrong = new Set(wrongGuesses.map(country => country.iso))
  const options = suggestions.filter(country => !wrong.has(country.iso))
  const highlighted = Math.min(active, Math.max(0, options.length - 1))
  const xpShare = question.baseXp > 0 ? Math.round(question.maximumXp / question.baseXp * FLAG_PERCENT) : 0
  const lastWrong = wrongGuesses.at(-1)
  const missesRef = useRef<HTMLUListElement>(null)

  useEffect(() => {
    if (wrongGuesses.length > 0) missesRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [wrongGuesses.length])

  function submit() {
    const choice = options[highlighted]
    if (!choice) {
      setNeedsPick(guess.trim().length > 0)
      return
    }
    setNeedsPick(false)
    onPick(choice)
  }

  function navigate(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive(Math.min(options.length - 1, highlighted + 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive(Math.max(0, highlighted - 1))
    }
  }

  return <GlobeGamePanel as="section" data-testid="flag-answer-panel" aria-labelledby="flag-question-title" className="p-4 sm:p-6">
    <GlobeGameRoundBar round={interpolate(copy.round, { count: question.round })}
      badge={tiers[question.tier]} badgeClassName={SILHOUETTE_TIER_BADGE[question.tier]}
      xp={question.maximumXp > 0 ? interpolate(copy.worthXp, { xp: question.maximumXp }) : copy.noXp} xpShare={xpShare} />

    <h2 id="flag-question-title" className="mt-5 shrink-0 text-[1.6rem] font-black leading-tight tracking-[-.03em] text-white max-sm:sr-only sm:text-[2rem] lg:[@media(max-height:900px)]:sr-only">
      {copy.question}
    </h2>

    <div className="relative mt-4 flex h-[min(13rem,24dvh)] min-h-24 w-full items-center justify-center max-sm:mt-3">
      <FlagCard src={question.flagUrl} alt={copy.flagLabel} eager className="h-full max-w-full" />
      <button type="button" onClick={() => setZoomed(true)} aria-label={copy.flagZoom}
        className="group absolute inset-0 flex items-end justify-end rounded-2xl p-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-200">
        <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-xl bg-black/45 text-white/70 ring-1 ring-white/10 backdrop-blur-md transition-colors group-hover:bg-black/60 group-hover:text-white">
          <Maximize2 size={15} />
        </span>
      </button>
    </div>
    <Modal ariaLabel={copy.flagLabel} isOpen={zoomed} onClose={() => setZoomed(false)} title="" className="max-w-2xl">
      <FlagCard src={question.flagUrl} alt={copy.flagLabel} eager className="w-full" />
    </Modal>

    <GlobeGameAnswerField accent="flag" className="mt-4 shrink-0 max-sm:mt-3" misses={wrongGuesses.length}
      submitLabel={copy.submit} submitDisabled={busy || !guess.trim()} onSubmit={submit}
      inputProps={{
        'aria-label': copy.answerLabel, value: guess, placeholder: copy.answerPlaceholder, role: 'combobox',
        'aria-expanded': options.length > 0, 'aria-controls': SUGGESTIONS_ID, onKeyDown: navigate,
        onChange: event => {
          setActive(0)
          setNeedsPick(false)
          onGuessChange(event.target.value)
        },
      }}>
      <GlobeGameSuggestions accent="flag" id={SUGGESTIONS_ID} label={copy.answerLabel} placement="above" disabled={busy}
        items={options.map(country => ({ key: country.iso, label: country.names[language] }))} highlighted={highlighted}
        onPick={index => onPick(options[index])} onHighlight={setActive} />
    </GlobeGameAnswerField>

    <div className="-mx-4 mt-1 min-h-20 flex-1 overflow-y-auto px-4 py-2.5 [mask-image:linear-gradient(to_bottom,transparent,#000_10px,#000_calc(100%-10px),transparent)] sm:-mx-6 sm:mt-3 sm:px-6 lg:[@media(max-height:800px)]:mt-2">
      <GlobeGameMisses listRef={missesRef} notice={needsPick ? copy.pickFromList : null}
        misses={wrongGuesses.map(country => ({ key: country.iso, label: country.names[language] }))}
        announcement={lastWrong ? interpolate(copy.incorrect, { guess: lastWrong.names[language] }) : null}
        attempts={question.attempts > 0 ? interpolate(copy.attempts, { count: question.attempts }) : null} />
      <FlagClues copy={copy} regions={regions} language={language} hints={question.hints} clues={question.clues}
        hintsUsed={question.hintsUsed} busy={busy} onReveal={onReveal} />
    </div>

    <GlobeGameActions accent="flag" skipLabel={copy.skip} showAnswerLabel={copy.showAnswer} busy={busy} onSkip={onSkip} onShowAnswer={onShowAnswer} />
  </GlobeGamePanel>
}
