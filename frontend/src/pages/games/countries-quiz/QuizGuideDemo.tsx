import { useState } from 'react'
import { Lightbulb } from 'lucide-react'
import type { SilhouetteLocale } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { SilhouetteGuideShot } from '../silhouettes/SilhouetteGuideStage'
import { flagUrl } from '../silhouettes/silhouetteGameConstants'
import { QUIZ_GOLD_EXAMPLE, QUIZ_TRAVEL_EXAMPLE } from './quizGameConstants'
import { factText } from './quizFacts'
import { QuizOptions } from './QuizOptions'
import { QUIZ_DEMO_COUNTRY, QUIZ_DEMO_FACT, quizDemoQuestion } from './quizGuideExample'

export const QUIZ_GUIDE_STEPS = 4

const QUESTION_STEP = 0
const FACT_STEP = 1
const TRAVEL_STEP = 2

interface QuizGuideDemoProps {
  copy: TranslationKeys['quizGame']
  regions: Record<string, string>
  language: SilhouetteLocale
  step: number
}

export function QuizGuideDemo({ copy, regions, language, step }: QuizGuideDemoProps) {
  const guide = copy.guide
  const [selected, setSelected] = useState<number | null>(null)
  const country = guide.demoCountry[language]

  if (step === QUESTION_STEP) {
    return <div className="w-full max-w-md rounded-[1.25rem] border border-white/[.1] bg-[#0b0f14] p-4 shadow-[0_24px_60px_rgba(0,0,0,.6)]">
      <p className="flex items-center gap-2 text-sm font-bold text-white">
        <img src={flagUrl(QUIZ_DEMO_COUNTRY)} alt="" aria-hidden="true" className="h-4 w-6 rounded-[3px] object-cover ring-1 ring-white/25" />
        {interpolate(copy.prompts.CAPITAL, { country })}
      </p>
      <div className="mt-3">
        <QuizOptions copy={copy} question={quizDemoQuestion(guide.demoOptions, selected)} pool={null} language={language}
          busy={false} keyboard={false} onAnswer={setSelected} />
      </div>
    </div>
  }

  if (step === FACT_STEP) {
    return <div className="w-full max-w-md rounded-2xl bg-emerald-300/[.08] px-4 py-3 ring-1 ring-emerald-200/20">
      <p className="text-sm font-bold text-emerald-100">{copy.correct}</p>
      <p className="mt-2 flex gap-2 text-sm leading-relaxed text-white/65">
        <Lightbulb size={15} aria-hidden="true" className="mt-0.5 shrink-0 text-orange-200/70" />
        {factText(copy, regions, QUIZ_DEMO_FACT, country, language, null)}
      </p>
    </div>
  }

  if (step === TRAVEL_STEP) {
    return <SilhouetteGuideShot image={QUIZ_TRAVEL_EXAMPLE} alt={guide.travelAlt} cover />
  }

  return <SilhouetteGuideShot image={QUIZ_GOLD_EXAMPLE} alt={guide.goldAlt} cover />
}
