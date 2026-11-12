import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Maximize2 } from 'lucide-react'
import type { SilhouetteLocale, SilhouetteQuestion } from '@/api/silhouettes'
import { Modal } from '@/components/ui/Modal'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameActions } from '../globe-game/GlobeGameActions'
import { GlobeGameAnswerField } from '../globe-game/GlobeGameAnswerField'
import { GlobeGameMisses } from '../globe-game/GlobeGameMisses'
import { GlobeGamePanel } from '../globe-game/GlobeGamePanel'
import { GlobeGameRoundBar } from '../globe-game/GlobeGameRoundBar'
import { GlobeGameSuggestions } from '../globe-game/GlobeGameSuggestions'
import { SilhouetteClues } from './SilhouetteClues'
import { SilhouetteShape } from './SilhouetteShape'
import { SILHOUETTE_PERCENT, SILHOUETTE_TIER_BADGE } from './silhouetteGameConstants'
import { silhouetteMissAnnouncement } from './silhouetteMiss'
import type { SilhouetteCountryOption } from './silhouetteWorld'
import type { SilhouetteWrongGuess } from './useSilhouetteGame'

interface SilhouetteGuessPanelProps {
  copy: TranslationKeys['silhouetteGame']
  language: SilhouetteLocale
  question: SilhouetteQuestion
  guess: string
  suggestions: SilhouetteCountryOption[]
  wrongGuesses: SilhouetteWrongGuess[]
  busy: boolean
  onGuessChange: (value: string) => void
  onPick: (country: SilhouetteCountryOption) => void
  onReveal: () => void
  onSkip: () => void
  onShowAnswer: () => void
}

const SUGGESTIONS_ID = 'silhouette-suggestions'

export function SilhouetteGuessPanel({
  copy, language, question, guess, suggestions, wrongGuesses, busy,
  onGuessChange, onPick, onReveal, onSkip, onShowAnswer,
}: SilhouetteGuessPanelProps) {
  const [active, setActive] = useState(0)
  const [needsPick, setNeedsPick] = useState(false)
  const [zoomed, setZoomed] = useState(false)
  const wrong = new Set(wrongGuesses.map(entry => entry.country.iso))
  const options = suggestions.filter(country => !wrong.has(country.iso))
  const highlighted = Math.min(active, Math.max(0, options.length - 1))
  const xpShare = question.baseXp > 0 ? Math.round(question.maximumXp / question.baseXp * SILHOUETTE_PERCENT) : 0
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

  return <GlobeGamePanel as="section" data-testid="silhouette-answer-panel" aria-labelledby="silhouette-question-title" className="p-4 sm:p-6">
    <GlobeGameRoundBar round={interpolate(copy.round, { count: question.round })}
      badge={copy.tiers[question.tier]} badgeClassName={SILHOUETTE_TIER_BADGE[question.tier]}
      xp={question.maximumXp > 0 ? interpolate(copy.worthXp, { xp: question.maximumXp }) : copy.noXp} xpShare={xpShare} />

    <h2 id="silhouette-question-title" className="mt-5 shrink-0 text-[1.6rem] font-black leading-tight tracking-[-.03em] text-white max-sm:sr-only sm:text-[2rem] lg:[@media(max-height:900px)]:sr-only">
      {copy.question}
    </h2>

    <div className="relative mt-4 h-[min(15rem,26dvh)] min-h-24 w-full max-lg:h-[min(13rem,23dvh)] max-sm:mt-3">
      <SilhouetteShape copy={copy} shapePath={question.shapePath} className="h-full w-full" />
      <button type="button" onClick={() => setZoomed(true)} aria-label={copy.shapeZoom}
        className="group absolute inset-0 flex items-end justify-end rounded-2xl p-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-300">
        <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-xl bg-black/45 text-white/70 ring-1 ring-white/10 backdrop-blur-md transition-colors group-hover:bg-black/60 group-hover:text-white">
          <Maximize2 size={15} />
        </span>
      </button>
    </div>
    <Modal ariaLabel={copy.shapeLabel} isOpen={zoomed} onClose={() => setZoomed(false)} title="" className="max-w-xl">
      <SilhouetteShape copy={copy} shapePath={question.shapePath} className="aspect-square w-full" />
    </Modal>

    <GlobeGameAnswerField accent="silhouette" className="mt-4 shrink-0 max-sm:mt-3" misses={wrongGuesses.length}
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
      <GlobeGameSuggestions accent="silhouette" id={SUGGESTIONS_ID} label={copy.answerLabel} placement="above" disabled={busy}
        items={options.map(country => ({ key: country.iso, label: country.names[language] }))} highlighted={highlighted}
        onPick={index => onPick(options[index])} onHighlight={setActive} />
    </GlobeGameAnswerField>

    <div className="-mx-4 mt-1 min-h-20 flex-1 overflow-y-auto px-4 py-2.5 [mask-image:linear-gradient(to_bottom,transparent,#000_10px,#000_calc(100%-10px),transparent)] sm:-mx-6 sm:mt-3 sm:px-6 lg:[@media(max-height:800px)]:mt-2">
      <GlobeGameMisses listRef={missesRef} notice={needsPick ? copy.pickFromList : null}
        misses={wrongGuesses.map(entry => ({ key: entry.country.iso, label: entry.country.names[language], title: silhouetteMissAnnouncement(copy, language, entry) }))}
        announcement={lastWrong ? silhouetteMissAnnouncement(copy, language, lastWrong) : null}
        attempts={question.attempts > 0 ? interpolate(copy.attempts, { count: question.attempts }) : null} />
      <SilhouetteClues copy={copy} language={language} hints={question.hints} clues={question.clues}
        hintsUsed={question.hintsUsed} busy={busy} onReveal={onReveal} />
    </div>

    <GlobeGameActions accent="silhouette" skipLabel={copy.skip} showAnswerLabel={copy.showAnswer} busy={busy} onSkip={onSkip} onShowAnswer={onShowAnswer} />
  </GlobeGamePanel>
}
