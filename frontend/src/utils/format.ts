const MS_PER_MINUTE = 60_000
const MS_PER_SECOND = 1_000

export function formatDurationMs(durationMs?: number | null): string {
  if (!durationMs || durationMs <= 0) return '0:00'
  const minutes = Math.floor(durationMs / MS_PER_MINUTE)
  const seconds = Math.floor((durationMs % MS_PER_MINUTE) / MS_PER_SECOND)
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

export function formatDecimal(value: number): string {
  if (Number.isInteger(value)) return String(value)
  return value.toFixed(1).replace(/\.0$/, '')
}

export function formatPercent(value: number): string {
  return `${formatDecimal(value)}%`
}

export function interpolate(template: string, vars: Record<string, string | number>): string {
  return Object.keys(vars).reduce(
    (acc, key) => acc.replaceAll(`{${key}}`, String(vars[key])),
    template
  )
}
