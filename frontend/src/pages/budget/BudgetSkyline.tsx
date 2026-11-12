import type { BudgetAnsweredQuestion } from '@/api/budget'
import { formatNumber } from './budgetFormatters'

interface BudgetSkylineProps {
  answers: BudgetAnsweredQuestion[]
  challengeDate: string
  compact?: boolean
}

interface BuildingStyle {
  widthPercent: number
  heightFactor: number
  heightOffset: number
  clipPath?: string
  rounded: string
}

const BUILDING_STYLES: BuildingStyle[] = [
  { widthPercent: 72, heightFactor: 1.08, heightOffset: 8, rounded: 'rounded-t-sm' },
  { widthPercent: 88, heightFactor: 0.92, heightOffset: 2, clipPath: 'polygon(14% 0,86% 0,100% 100%,0 100%)', rounded: '' },
  { widthPercent: 100, heightFactor: 0.82, heightOffset: 0, rounded: 'rounded-t-xl' },
  { widthPercent: 62, heightFactor: 1.18, heightOffset: 12, clipPath: 'polygon(50% 0,100% 18%,100% 100%,0 100%,0 18%)', rounded: '' },
  { widthPercent: 96, heightFactor: 0.88, heightOffset: 5, rounded: 'rounded-t-md' },
  { widthPercent: 82, heightFactor: 1.02, heightOffset: 6, clipPath: 'polygon(0 9%,72% 0,100% 15%,100% 100%,0 100%)', rounded: '' },
  { widthPercent: 90, heightFactor: 0.96, heightOffset: 4, rounded: 'rounded-t-[42%]' },
  { widthPercent: 76, heightFactor: 1.12, heightOffset: 10, clipPath: 'polygon(22% 0,78% 0,78% 10%,90% 10%,90% 22%,100% 22%,100% 100%,0 100%,0 22%,10% 22%,10% 10%,22% 10%)', rounded: '' },
]

export function BudgetSkyline({ answers, challengeDate, compact = false }: BudgetSkylineProps) {
  const seed = dateSeed(challengeDate)
  return (
    <div className={`relative flex items-end justify-center ${compact ? 'h-40 gap-2' : 'h-64 gap-3 sm:gap-4'}`}>
      <div className="pointer-events-none absolute inset-x-4 bottom-3 h-24 rounded-full bg-emerald-300/[.08] blur-3xl" />
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-white/20 to-transparent" />
      {answers.map((answer, index) => (
        <SkylineBuilding
          key={answer.question.variantId}
          answer={answer}
          index={index}
          styleId={positiveModulo(seed + index * 5, BUILDING_STYLES.length)}
          compact={compact}
        />
      ))}
    </div>
  )
}

function SkylineBuilding({ answer, index, styleId, compact }: {
  answer: BudgetAnsweredQuestion
  index: number
  styleId: number
  compact: boolean
}) {
  const accuracy = Math.max(0, Math.min(100, answer.accuracyPercent))
  const style = BUILDING_STYLES[styleId]
  const rawHeight = compact ? 18 + accuracy * 0.94 : 24 + accuracy * 1.66
  const height = rawHeight * style.heightFactor + (compact ? style.heightOffset * 0.55 : style.heightOffset)
  const windowCount = Math.max(3, Math.min(compact ? 15 : 27, Math.round(height / (compact ? 12 : 14)) * 3))
  const litWindowRatio = 0.08 + accuracy * 0.0092
  const tone = buildingTone(accuracy)

  return (
    <div className={`relative z-[1] flex min-w-0 flex-1 flex-col items-center ${compact ? 'max-w-16' : 'max-w-24'}`}>
      <div className="relative flex w-full items-end justify-center" style={{ height: `${height}px` }}>
        <BuildingSideDetails styleId={styleId} compact={compact} tone={tone} />
        <div
          className={`relative h-full border shadow-[0_0_28px_var(--building-glow)] ${style.rounded} ${tone}`}
          style={{ width: `${style.widthPercent}%`, clipPath: style.clipPath }}
          aria-label={`Question ${index + 1}: ${formatNumber(accuracy, 1)}% accuracy`}
        >
          <RoofDecoration styleId={styleId} compact={compact} />
          <div className={`absolute grid grid-cols-3 content-around overflow-hidden ${compact ? 'inset-x-1.5 bottom-2 top-3 gap-1' : 'inset-x-2.5 bottom-3 top-4 gap-1.5'}`}>
            {Array.from({ length: windowCount }, (_, windowIndex) => (
              <span
                key={windowIndex}
                className={isWindowLit(windowIndex, styleId, litWindowRatio)
                  ? 'h-1 rounded-[1px] bg-amber-100/75 shadow-[0_0_5px_rgba(253,230,138,.32)]'
                  : 'h-1 rounded-[1px] bg-black/25'}
              />
            ))}
          </div>
          <ArchitectureDetails styleId={styleId} compact={compact} />
        </div>
      </div>
      <span className={`mt-2 font-black tabular-nums ${compact ? 'text-[9px]' : 'text-[11px]'} ${accuracy <= 0 ? 'text-red-300' : 'text-white/68'}`}>
        {formatNumber(accuracy, accuracy % 1 === 0 ? 0 : 1)}%
      </span>
    </div>
  )
}

function RoofDecoration({ styleId, compact }: { styleId: number; compact: boolean }) {
  if (styleId === 0 || styleId === 3) {
    return (
      <span className={`absolute bottom-full left-1/2 w-px -translate-x-1/2 bg-current opacity-80 ${compact ? 'h-4' : 'h-7'}`}>
        <span className="absolute -left-1 -top-1 h-2 w-2 rounded-full bg-amber-300 shadow-[0_0_8px_rgba(252,211,77,.7)]" />
      </span>
    )
  }
  if (styleId === 4) {
    return (
      <>
        <span className={`absolute bottom-full left-[21%] w-2 rounded-t border border-b-0 border-current opacity-70 ${compact ? 'h-3' : 'h-5'}`} />
        <span className={`absolute bottom-full right-[18%] w-2 rounded-t border border-b-0 border-current opacity-70 ${compact ? 'h-5' : 'h-8'}`} />
      </>
    )
  }
  if (styleId === 6) {
    return <span className={`absolute bottom-full left-1/2 -translate-x-1/2 rounded-t-full border border-b-0 border-current opacity-70 ${compact ? 'h-2 w-5' : 'h-4 w-8'}`} />
  }
  return null
}

function BuildingSideDetails({ styleId, compact, tone }: { styleId: number; compact: boolean; tone: string }) {
  if (styleId === 2) {
    return (
      <>
        <span className={`absolute bottom-0 left-0 w-[22%] rounded-t border opacity-75 ${tone} ${compact ? 'h-[36%]' : 'h-[42%]'}`} />
        <span className={`absolute bottom-0 right-0 w-[22%] rounded-t border opacity-70 ${tone} ${compact ? 'h-[50%]' : 'h-[58%]'}`} />
      </>
    )
  }
  if (styleId === 4) {
    return (
      <>
        <span className={`absolute bottom-0 left-[7%] h-[82%] w-[30%] rounded-t border opacity-75 ${tone}`} />
        <span className={`absolute bottom-0 right-[7%] h-[67%] w-[30%] rounded-t border opacity-70 ${tone}`} />
      </>
    )
  }
  return null
}

function ArchitectureDetails({ styleId, compact }: { styleId: number; compact: boolean }) {
  if (styleId === 1) {
    return <span className="absolute inset-y-0 left-1/2 w-px bg-white/15" />
  }
  if (styleId === 5) {
    return (
      <>
        <span className={`absolute -left-2 bottom-[12%] w-2 rounded-l-md border border-r-0 border-current opacity-70 ${compact ? 'h-7' : 'h-12'}`} />
        <span className={`absolute -right-2 bottom-[35%] w-2 rounded-r-md border border-l-0 border-current opacity-70 ${compact ? 'h-6' : 'h-10'}`} />
      </>
    )
  }
  if (styleId === 7) {
    return (
      <>
        <span className="absolute inset-x-[18%] top-[20%] h-px bg-white/20" />
        <span className="absolute inset-x-[10%] top-[39%] h-px bg-white/15" />
      </>
    )
  }
  return null
}

function isWindowLit(index: number, styleId: number, ratio: number) {
  const deterministicValue = positiveModulo(index * 37 + styleId * 19 + 11, 101) / 100
  return deterministicValue < ratio
}

function dateSeed(challengeDate: string) {
  return [...challengeDate].reduce(
    (seed, character) => positiveModulo(seed * 31 + character.charCodeAt(0), BUILDING_STYLES.length),
    17,
  )
}

function positiveModulo(value: number, modulus: number) {
  return ((value % modulus) + modulus) % modulus
}

function buildingTone(accuracy: number) {
  if (accuracy <= 0) {
    return 'border-red-300/55 bg-gradient-to-br from-red-500/75 to-red-950/95 text-red-300 [--building-glow:rgba(239,68,68,.28)]'
  }
  if (accuracy < 30) {
    return 'border-orange-300/40 bg-gradient-to-br from-orange-500/60 to-orange-950/90 text-orange-300 [--building-glow:rgba(249,115,22,.22)]'
  }
  if (accuracy < 60) {
    return 'border-amber-300/40 bg-gradient-to-br from-amber-400/55 to-amber-950/90 text-amber-300 [--building-glow:rgba(251,191,36,.2)]'
  }
  if (accuracy < 85) {
    return 'border-emerald-300/40 bg-gradient-to-br from-emerald-400/55 to-emerald-950/90 text-emerald-300 [--building-glow:rgba(52,211,153,.23)]'
  }
  return 'border-emerald-200/65 bg-gradient-to-br from-emerald-300/80 to-emerald-800/95 text-emerald-200 [--building-glow:rgba(110,231,183,.34)]'
}
