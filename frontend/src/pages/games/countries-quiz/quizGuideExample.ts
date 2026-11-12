import type { QuizAnswerResult, QuizFact, QuizQuestion } from '@/api/quiz'
import type { SilhouetteNames } from '@/api/silhouettes'

const DEMO_ANSWER = 1
const DEMO_XP = 75
const DEMO_POPULATION = 58_990_000
const DEMO_POSITION = 1

export const QUIZ_DEMO_FACT: QuizFact = { type: 'POPULATION', value: DEMO_POPULATION }
export const QUIZ_DEMO_COUNTRY = 'IT'

export function quizDemoQuestion(options: readonly SilhouetteNames[], selected: number | null): QuizQuestion {
  const answer: QuizAnswerResult | null = selected == null ? null : {
    selectedIndex: selected,
    answerIndex: DEMO_ANSWER,
    correct: selected === DEMO_ANSWER,
    xpAwarded: selected === DEMO_ANSWER ? DEMO_XP : 0,
    fact: QUIZ_DEMO_FACT,
  }
  return {
    position: DEMO_POSITION,
    kind: 'CAPITAL',
    subject: null,
    options: options.map(names => ({ type: 'PLACE', names })),
    answer,
  }
}
