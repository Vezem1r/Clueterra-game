import type { RiverQuestion } from '@/api/rivers'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameActions } from '../globe-game/GlobeGameActions'
import { GlobeGameAnswerField } from '../globe-game/GlobeGameAnswerField'
import { GlobeGameMisses } from '../globe-game/GlobeGameMisses'
import { GlobeGamePanel } from '../globe-game/GlobeGamePanel'
import { GlobeGameRoundBar } from '../globe-game/GlobeGameRoundBar'
import { GlobeGameSuggestions } from '../globe-game/GlobeGameSuggestions'
import { RiverClues } from './RiverClues'
import { RIVER_DIFFICULTY_BADGE, RIVER_PERCENT } from './riverGameConstants'

interface RiverGuessPanelProps {
  copy: TranslationKeys['riverGame']
  question: RiverQuestion
  guess: string
  suggestions: string[]
  wrongGuesses: string[]
  busy: boolean
  onGuessChange: (value: string) => void
  onPickSuggestion: (value: string) => void
  onSubmit: () => void
  onReveal: () => void
  onSkip: () => void
  onShowAnswer: () => void
}

export function RiverGuessPanel({
  copy, question, guess, suggestions, wrongGuesses, busy,
  onGuessChange, onPickSuggestion, onSubmit, onReveal, onSkip, onShowAnswer,
}: RiverGuessPanelProps) {
  const visibleSuggestions = suggestions.filter(name => name.toLowerCase() !== guess.trim().toLowerCase())
  const lastWrong = wrongGuesses.at(-1)
  const xpShare = question.baseXp > 0 ? Math.round(question.maximumXp / question.baseXp * RIVER_PERCENT) : 0

  return <GlobeGamePanel as="section" data-testid="river-answer-panel" aria-labelledby="river-question-title" className="p-4 sm:p-6">
    <GlobeGameRoundBar round={interpolate(copy.round, { count: question.roundNumber })}
      badge={copy.difficulties[question.difficulty]} badgeClassName={RIVER_DIFFICULTY_BADGE[question.difficulty]}
      xp={question.maximumXp > 0 ? interpolate(copy.worthXp, { xp: question.maximumXp }) : copy.noXp} xpShare={xpShare} />

    <h2 id="river-question-title" className="mt-5 shrink-0 text-[1.6rem] font-black leading-tight tracking-[-.03em] text-white max-sm:sr-only sm:text-[2rem] lg:[@media(max-height:820px)]:sr-only">
      {copy.question}
    </h2>

    <GlobeGameAnswerField accent="river" className="mt-4 shrink-0 max-sm:mt-3.5 lg:[@media(max-height:820px)]:mt-5"
      misses={wrongGuesses.length} submitLabel={copy.submit} submitDisabled={busy || !guess.trim()} onSubmit={onSubmit}
      inputProps={{ 'aria-label': copy.answerLabel, value: guess, placeholder: copy.answerPlaceholder, onChange: event => onGuessChange(event.target.value) }}>
      <GlobeGameSuggestions accent="river" label={copy.answerLabel} placement="below"
        items={visibleSuggestions.map(name => ({ key: name, label: name }))} onPick={index => onPickSuggestion(visibleSuggestions[index])} />
    </GlobeGameAnswerField>

    <div className="-mx-4 mt-1 min-h-0 flex-1 overflow-y-auto px-4 py-2.5 [mask-image:linear-gradient(to_bottom,transparent,#000_10px,#000_calc(100%-10px),transparent)] sm:-mx-6 sm:mt-3 sm:px-6 lg:[@media(max-height:800px)]:mt-2">
      <GlobeGameMisses misses={wrongGuesses.map(name => ({ key: name, label: name }))}
        announcement={lastWrong ? interpolate(copy.incorrect, { guess: lastWrong }) : null}
        attempts={question.attempts > 0 ? interpolate(copy.attempts, { count: question.attempts }) : null} />
      <RiverClues copy={copy} title={copy.cluesTitle} hints={question.revealedHints} hintsUsed={question.hintsUsed} busy={busy}
        clueCost={question.maximumXp > 0 ? question.cluePenaltyXp : undefined} onReveal={onReveal} />
    </div>

    <GlobeGameActions accent="river" skipLabel={copy.skip} showAnswerLabel={copy.showAnswer} busy={busy} onSkip={onSkip} onShowAnswer={onShowAnswer} />
  </GlobeGamePanel>
}
