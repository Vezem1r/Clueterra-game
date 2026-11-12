import type { GameResults, GameSessionSummary } from '@/types'
import type { BudgetGameSession } from '@/api/budget'

const BASE62_ALPHABET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
const BASE = BigInt(BASE62_ALPHABET.length)
const PNG_CONTENT_TYPE = 'image/png'

const MAX_HINTS_GREEN = 1
const MAX_HINTS_YELLOW = 3

function countrySquare(country: GameResults['countries'][number]): string {
  if (!country.guessed) return '🟥'
  if (country.hintsSeen <= MAX_HINTS_GREEN) return '🟩'
  if (country.hintsSeen <= MAX_HINTS_YELLOW) return '🟨'
  return '🟧'
}

export function encodeBase62(numericId: string): string {
  let value = BigInt(numericId)
  if (value === 0n) return '0'
  let result = ''
  while (value > 0n) {
    result = BASE62_ALPHABET[Number(value % BASE)] + result
    value /= BASE
  }
  return result
}

function shareUrl(sessionId: string): string {
  return `${window.location.origin}/s/${encodeBase62(sessionId)}`
}

export function budgetShareUrl(sessionId: string): string {
  return `${window.location.origin}/s/b/${encodeBase62(sessionId)}`
}

export function budgetShareImageUrl(sessionId: string): string {
  return `${budgetShareUrl(sessionId)}/og.png`
}

async function fetchBudgetShareImage(sessionId: string): Promise<Blob> {
  const response = await fetch(budgetShareImageUrl(sessionId), {
    headers: { Accept: PNG_CONTENT_TYPE },
  })
  if (!response.ok) {
    throw new Error('Budget share card is unavailable')
  }

  const contentType = response.headers.get('content-type')
    ?.split(';', 1)[0]
    .trim()
    .toLowerCase()
  if (contentType !== PNG_CONTENT_TYPE) {
    throw new Error('Budget share card response is not a PNG image')
  }

  const image = await response.blob()
  if (image.type && image.type.toLowerCase() !== PNG_CONTENT_TYPE) {
    throw new Error('Budget share card response is not a PNG image')
  }
  return image
}

export function buildShareText(results: GameResults): string {
  const grid = results.countries.map(countrySquare).join(' ')
  const xp = results.totalXpEarned.toLocaleString('en')

  return [
    grid,
    `${xp} XP — Your turn.`,
    shareUrl(results.id),
  ].join('\n')
}

function buildShareTextFromSummary(summary: GameSessionSummary): string {
  const xp = summary.totalXpEarned.toLocaleString('en')

  return [
    `${xp} XP — Your turn.`,
    shareUrl(summary.id),
  ].join('\n')
}

function budgetSquare(accuracyPercent: number): string {
  if (accuracyPercent <= 0) return '🟥'
  if (accuracyPercent < 30) return '🟧'
  if (accuracyPercent < 60) return '🟨'
  return '🟩'
}

export function buildBudgetShareText(session: BudgetGameSession): string {
  const grid = session.answeredQuestions.map(answer => budgetSquare(answer.accuracyPercent)).join(' ')
  const average = session.answeredQuestions.length === 0
    ? 0
    : session.answeredQuestions.reduce((total, answer) => total + answer.accuracyPercent, 0)
      / session.answeredQuestions.length

  return [
    `BudgetGuessr · ${grid}`,
    `${session.totalXpEarned}/${session.maximumXp} XP · ${average.toFixed(1)}% accuracy`,
    'Can you build a better skyline?',
    budgetShareUrl(session.id),
  ].join('\n')
}

const isMobile = () =>
  /Android|iPhone|iPad|iPod/i.test(navigator.userAgent) ||
  (navigator.maxTouchPoints > 0 && /Mobi/i.test(navigator.userAgent))

async function doShare(text: string): Promise<'shared' | 'copied'> {
  if (navigator.share && isMobile()) {
    try {
      await navigator.share({ text })
      return 'shared'
    } catch {
      // user cancelled — fall through to clipboard
    }
  }

  await navigator.clipboard.writeText(text)
  return 'copied'
}

export async function shareResult(results: GameResults): Promise<'shared' | 'copied'> {
  return doShare(buildShareText(results))
}

export async function shareSummary(summary: GameSessionSummary): Promise<'shared' | 'copied'> {
  return doShare(buildShareTextFromSummary(summary))
}

export async function shareBudgetResult(session: BudgetGameSession): Promise<'shared' | 'copied'> {
  const text = buildBudgetShareText(session)

  if (navigator.share && navigator.canShare) {
    try {
      const image = await fetchBudgetShareImage(session.id)
      const file = new File([image], 'budgetguessr-result.png', { type: PNG_CONTENT_TYPE })
      if (navigator.canShare({ files: [file] })) {
        await navigator.share({ text, files: [file] })
        return 'shared'
      }
    } catch {
      // Text sharing remains available when image generation or native sharing fails.
    }
  }

  return doShare(text)
}

export async function downloadBudgetShareCard(session: BudgetGameSession): Promise<void> {
  const href = URL.createObjectURL(await fetchBudgetShareImage(session.id))
  const link = document.createElement('a')
  link.href = href
  link.download = `budgetguessr-${session.challengeDate}.png`
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(href), 0)
}
