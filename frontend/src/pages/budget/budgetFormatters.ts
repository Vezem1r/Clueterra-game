import type {
  BudgetChallengeQuestion,
  BudgetPromptProject,
  BudgetQuestionType,
} from '@/api/budget'

export function formatUsd(value: number, compact = false) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: compact ? 'compact' : 'standard',
    maximumFractionDigits: compact ? 1 : 0,
  }).format(value)
}

export function formatNumber(value: number, maximumFractionDigits = 1) {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits }).format(value)
}

export function formatAnswer(
  value: number | null,
  unit: string | null,
  compact = false,
  monthsLabel = 'months',
) {
  if (value == null) return '—'
  if (unit === 'USD' || unit?.startsWith('USD_PER_')) {
    const amount = formatUsd(value, compact)
    return unit === 'USD' ? amount : `${amount} / ${unitLabel(unit)}`
  }
  if (unit === 'PERCENT') return `${formatNumber(value, 1)}%`
  if (unit === 'MONTHS') return `${formatNumber(value, 0)} ${monthsLabel}`
  return `${formatNumber(value)}${unit ? ` ${unitLabel(unit)}` : ''}`
}

export function unitLabel(unit: string) {
  return unit
    .replace('USD_PER_', '')
    .replaceAll('_', ' ')
    .toLocaleLowerCase()
}

export function humanize(value: string) {
  return value
    .replaceAll('_', ' ')
    .toLocaleLowerCase()
    .replace(/^./, first => first.toLocaleUpperCase())
}

export function questionProject(question: BudgetChallengeQuestion): BudgetPromptProject | null {
  return question.prompt.project ?? question.prompt.primaryProject ?? null
}

export function initialGuess(type: BudgetQuestionType) {
  switch (type) {
    case 'BUDGET_OVERRUN': return 50
    case 'HOW_LATE': return 12
    case 'COST_PER_UNIT': return 100_000
    default: return 100_000_000
  }
}

export function sliderBounds(type: BudgetQuestionType) {
  switch (type) {
    case 'BUDGET_OVERRUN': return { min: 0, max: 3 }
    case 'HOW_LATE': return { min: 0, max: 3 }
    case 'COST_PER_UNIT': return { min: 1, max: 10 }
    default: return { min: 5, max: 12 }
  }
}

export function parseBudgetAmount(value: string) {
  const normalized = value.trim().replaceAll(',', '').toLocaleLowerCase()
  const match = normalized.match(/^(\d+(?:\.\d{0,6})?)\s*([kmbt])?$/)
  if (!match) return Number.NaN

  const multiplier = {
    k: 1_000,
    m: 1_000_000,
    b: 1_000_000_000,
    t: 1_000_000_000_000,
  }[match[2] ?? ''] ?? 1
  return Number(match[1]) * multiplier
}

export function formatBudgetInput(value: string) {
  if (!value || /[kmbt]$/i.test(value)) return value.toLocaleUpperCase()
  const [integer, fraction] = value.replaceAll(',', '').split('.')
  const formattedInteger = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return fraction == null ? formattedInteger : `${formattedInteger}.${fraction}`
}
