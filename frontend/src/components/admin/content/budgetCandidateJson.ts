import { parse, printParseErrorCode, type ParseError } from 'jsonc-parser'
import type {
  BudgetContentCandidate,
  BudgetContentCostClaimInput,
  BudgetContentDescriptionSourceInput,
  BudgetContentMetricInput,
  BudgetContentMilestoneInput,
  BudgetContentUpdateInput,
} from '@/api/budgetContentAdmin'

export const BUDGET_SOURCE_CODES = [
  'WIKIDATA',
  'WIKIPEDIA',
  'NASA',
  'FTA',
  'GAO',
  'CLUETERRA_RESEARCH',
] as const
export const BUDGET_COST_KINDS = [
  'REPORTED_COST',
  'ORIGINAL_BUDGET',
  'APPROVED_BUDGET',
  'FINAL_COST',
  'CURRENT_ESTIMATE',
  'REVISED_BUDGET',
  'EXPENDITURE_TO_DATE',
  'FINANCING_AMOUNT',
  'CONTRACT_VALUE',
] as const
export const BUDGET_METRIC_TYPES = [
  'LENGTH',
  'MAIN_SPAN',
  'HEIGHT',
  'FLOOR_AREA',
  'CAPACITY',
  'POWER',
  'ANNUAL_PASSENGERS',
  'ANNUAL_CARGO_TONNES',
  'CONTAINER_CAPACITY',
  'STORAGE_VOLUME',
  'MASS',
] as const
export const BUDGET_MILESTONE_TYPES = [
  'CONSTRUCTION_START',
  'PLANNED_COMPLETION',
  'CURRENT_COMPLETION_ESTIMATE',
  'ACTUAL_COMPLETION',
  'OPENING',
] as const
export const BUDGET_REGIONS = [
  'AFRICA',
  'ASIA',
  'EUROPE',
  'NORTH_AMERICA',
  'SOUTH_AMERICA',
  'OCEANIA',
  'UNKNOWN',
] as const
export const BUDGET_DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'] as const
export const BUDGET_DATE_PRECISIONS = ['DAY', 'YEAR'] as const
export const BUDGET_EXCHANGE_RATE_BASES = [
  'LATEST_REFERENCE_RATE',
  'ANNUAL_AVERAGE',
  'HISTORICAL_DAILY_RATE',
  'SOURCE_STATED',
] as const
export const BUDGET_EXCHANGE_RATE_PROVIDERS = [
  'NONE',
  'ECB',
  'BUNDESBANK_BBK',
  'WORLD_BANK',
  'BCB',
] as const
export const BUDGET_VERIFICATION_STATUSES = ['SOURCE_BACKED', 'CORROBORATED', 'DISPUTED'] as const

export const BUDGET_NORMALIZED_METRIC_UNITS: Readonly<Record<string, string>> = {
  LENGTH: 'M',
  MAIN_SPAN: 'M',
  HEIGHT: 'M',
  FLOOR_AREA: 'M2',
  CAPACITY: 'SEAT',
  POWER: 'MW',
  ANNUAL_PASSENGERS: 'PASSENGERS_PER_YEAR',
  ANNUAL_CARGO_TONNES: 'TONNES_PER_YEAR',
  CONTAINER_CAPACITY: 'TEU_PER_YEAR',
  STORAGE_VOLUME: 'M3',
  MASS: 'KG',
}

const MAX_FACTS_PER_TYPE = 50
const MIN_YEAR = 1850
const MAX_YEAR = 2200
const MINIMUM_PLAYABLE_COST = 1_000_000
const MAXIMUM_PLAYABLE_COST = 1_000_000_000_000

type JsonRecord = Record<string, unknown>

export type BudgetJsonChangeKind = 'added' | 'changed' | 'removed'

export interface BudgetJsonChange {
  kind: BudgetJsonChangeKind
  path: string
  before?: unknown
  after?: unknown
}

export interface BudgetJsonImportResult {
  value: BudgetContentUpdateInput | null
  errors: string[]
}

export function createBudgetCandidateJson(
  candidate: BudgetContentCandidate,
  input: BudgetContentUpdateInput,
) {
  const comments = `/*
CLUETERRA BUDGET PROJECT EDITOR (JSONC)
Context only (not imported): wikidataId=${candidate.wikidataId}, category=${candidate.category}

Return one JSON object with the same shape as below. Comments and unknown properties are ignored.
Do not wrap the result in prose. A markdown \`\`\`json fence is tolerated, but plain JSONC is preferred.

Required project fields:
- name: non-empty, max 300 characters
- description: non-empty, max 5000 characters; keep original wording, or fill descriptionSource
- countryCode: exactly two uppercase ISO letters; countryName: max 120 characters
- regionCode: ${BUDGET_REGIONS.join(' | ')}
- projectYear: integer ${MIN_YEAR}-${MAX_YEAR}, or null
- difficulty: ${BUDGET_DIFFICULTIES.join(' | ')}
- imageFileTitle: non-empty Wikimedia Commons file title, max 500 characters
- descriptionSource: null for original wording, otherwise name/url/licenseLabel/licenseUrl with HTTPS URLs

costClaims: 1-${MAX_FACTS_PER_TYPE} entries. Exactly one entry must have answer=true and primary=true.
- costKind: ${BUDGET_COST_KINDS.join(' | ')}
- all amount/rate values must be positive; each high value must be >= its low value
- playable USD midpoint must be between 1,000,000 and 1,000,000,000,000
- sourceCurrency: 3 uppercase letters; costYear: ${MIN_YEAR}-${MAX_YEAR} or null
- exchangeRateBasis: ${BUDGET_EXCHANGE_RATE_BASES.join(' | ')}
- exchangeRateProvider: ${BUDGET_EXCHANGE_RATE_PROVIDERS.join(' | ')}
- verificationStatus: ${BUDGET_VERIFICATION_STATUSES.join(' | ')}

metrics: 0-${MAX_FACTS_PER_TYPE} entries.
- metricType and required normalizedUnit: ${Object.entries(BUDGET_NORMALIZED_METRIC_UNITS)
    .map(([type, unit]) => `${type}=${unit}`)
    .join(' | ')}
- normalizedValue and sourceValue must be positive

milestones: 0-${MAX_FACTS_PER_TYPE} entries.
- milestoneType: ${BUDGET_MILESTONE_TYPES.join(' | ')}
- datePrecision=DAY requires milestoneDate YYYY-MM-DD and milestoneYear=null
- datePrecision=YEAR requires milestoneYear ${MIN_YEAR}-${MAX_YEAR} and milestoneDate=null

Every cost, metric and milestone requires:
- sourceCode: ${BUDGET_SOURCE_CODES.join(' | ')}
- sourceName, HTTPS sourceUrl, sourceKey, and primary
- referenceUrl may be empty or HTTP(S); evidenceText may be empty
- only one primary entry is allowed for each costKind, metricType or milestoneType

Never invent citations, amounts, exchange rates, dates or measurements. Preserve existing evidence unless a reliable source supports the change.
*/`

  return `${comments}\n${JSON.stringify(input, null, 2)}\n`
}

export function parseBudgetCandidateJson(source: string): BudgetJsonImportResult {
  const parseErrors: ParseError[] = []
  const normalizedSource = unwrapMarkdownFence(source)
  const parsed = parse(normalizedSource, parseErrors, {
    allowTrailingComma: true,
    disallowComments: false,
  }) as unknown

  if (parseErrors.length > 0) {
    return {
      value: null,
      errors: parseErrors.map((error) => formatParseError(normalizedSource, error)),
    }
  }
  if (!isRecord(parsed)) {
    return { value: null, errors: ['Root must be a JSON object.'] }
  }

  const errors: string[] = []
  const descriptionSource = readDescriptionSource(parsed.descriptionSource, errors)
  const costClaims = readArray(parsed, 'costClaims', errors)
    .slice(0, MAX_FACTS_PER_TYPE + 1)
    .map((value, index) => readCostClaim(value, index, errors))
  const metrics = readArray(parsed, 'metrics', errors)
    .slice(0, MAX_FACTS_PER_TYPE + 1)
    .map((value, index) => readMetric(value, index, errors))
  const milestones = readArray(parsed, 'milestones', errors)
    .slice(0, MAX_FACTS_PER_TYPE + 1)
    .map((value, index) => readMilestone(value, index, errors))

  validateFactCounts(costClaims, metrics, milestones, errors)
  validatePrimaryFacts(costClaims, metrics, milestones, errors)
  validatePlayableCost(costClaims, errors)

  const value: BudgetContentUpdateInput = {
    name: readString(parsed, 'name', 'name', errors, { min: 1, max: 300 }),
    description: readString(parsed, 'description', 'description', errors, { min: 1, max: 5000 }),
    countryCode: readPatternString(parsed, 'countryCode', 'countryCode', errors, /^[A-Z]{2}$/, 'two uppercase ISO letters'),
    countryName: readString(parsed, 'countryName', 'countryName', errors, { min: 1, max: 120 }),
    regionCode: readEnum(parsed, 'regionCode', 'regionCode', BUDGET_REGIONS, errors),
    projectYear: readNullableInteger(parsed, 'projectYear', 'projectYear', MIN_YEAR, MAX_YEAR, errors),
    difficulty: readEnum(parsed, 'difficulty', 'difficulty', BUDGET_DIFFICULTIES, errors),
    imageFileTitle: readString(parsed, 'imageFileTitle', 'imageFileTitle', errors, { min: 1, max: 500 }),
    descriptionSource,
    costClaims,
    metrics,
    milestones,
  }

  return { value: errors.length === 0 ? value : null, errors }
}

export function diffBudgetCandidateJson(
  before: BudgetContentUpdateInput,
  after: BudgetContentUpdateInput,
) {
  const changes: BudgetJsonChange[] = []
  const scalarKeys: Array<keyof BudgetContentUpdateInput> = [
    'name',
    'description',
    'countryCode',
    'countryName',
    'regionCode',
    'projectYear',
    'difficulty',
    'imageFileTitle',
  ]
  scalarKeys.forEach((key) => diffValue(String(key), before[key], after[key], changes))
  diffValue('descriptionSource', before.descriptionSource, after.descriptionSource, changes)
  diffCollection(
    'costClaims',
    before.costClaims,
    after.costClaims,
    (claim) => `${claim.costKind} · ${claim.sourceKey}`,
    (claim) => claim.costKind,
    changes,
  )
  diffCollection(
    'metrics',
    before.metrics,
    after.metrics,
    (metric) => `${metric.metricType} · ${metric.sourceKey}`,
    (metric) => metric.metricType,
    changes,
  )
  diffCollection(
    'milestones',
    before.milestones,
    after.milestones,
    (milestone) => `${milestone.milestoneType} · ${milestone.sourceKey}`,
    (milestone) => milestone.milestoneType,
    changes,
  )
  return changes
}

function readDescriptionSource(value: unknown, errors: string[]) {
  if (value === null) return null
  if (!isRecord(value)) {
    errors.push('descriptionSource must be an object or null.')
    return null
  }
  return {
    name: readString(value, 'name', 'descriptionSource.name', errors, { min: 1, max: 200 }),
    url: readUrl(value, 'url', 'descriptionSource.url', errors, true),
    licenseLabel: readString(value, 'licenseLabel', 'descriptionSource.licenseLabel', errors, { min: 1, max: 100 }),
    licenseUrl: readUrl(value, 'licenseUrl', 'descriptionSource.licenseUrl', errors, true),
  } satisfies BudgetContentDescriptionSourceInput
}

function readCostClaim(value: unknown, index: number, errors: string[]) {
  const path = `costClaims[${index}]`
  const object = requireRecord(value, path, errors)
  const sourceAmountLow = readPositiveNumber(object, 'sourceAmountLow', `${path}.sourceAmountLow`, errors)
  const sourceAmountHigh = readPositiveNumber(object, 'sourceAmountHigh', `${path}.sourceAmountHigh`, errors)
  const amountUsdLow = readPositiveNumber(object, 'amountUsdLow', `${path}.amountUsdLow`, errors)
  const amountUsdHigh = readPositiveNumber(object, 'amountUsdHigh', `${path}.amountUsdHigh`, errors)
  if (sourceAmountHigh < sourceAmountLow) errors.push(`${path}.sourceAmountHigh must be >= sourceAmountLow.`)
  if (amountUsdHigh < amountUsdLow) errors.push(`${path}.amountUsdHigh must be >= amountUsdLow.`)

  return {
    costKind: readEnum(object, 'costKind', `${path}.costKind`, BUDGET_COST_KINDS, errors),
    sourceAmountLow,
    sourceAmountHigh,
    sourceCurrency: readPatternString(object, 'sourceCurrency', `${path}.sourceCurrency`, errors, /^[A-Z]{3}$/, 'three uppercase letters'),
    amountUsdLow,
    amountUsdHigh,
    costYear: readNullableInteger(object, 'costYear', `${path}.costYear`, MIN_YEAR, MAX_YEAR, errors),
    unitsPerUsd: readPositiveNumber(object, 'unitsPerUsd', `${path}.unitsPerUsd`, errors),
    exchangeRateDate: readDate(object, 'exchangeRateDate', `${path}.exchangeRateDate`, errors),
    exchangeRateBasis: readEnum(object, 'exchangeRateBasis', `${path}.exchangeRateBasis`, BUDGET_EXCHANGE_RATE_BASES, errors),
    exchangeRateProvider: readEnum(object, 'exchangeRateProvider', `${path}.exchangeRateProvider`, BUDGET_EXCHANGE_RATE_PROVIDERS, errors),
    sourceCode: readEnum(object, 'sourceCode', `${path}.sourceCode`, BUDGET_SOURCE_CODES, errors),
    sourceName: readString(object, 'sourceName', `${path}.sourceName`, errors, { min: 1, max: 200 }),
    sourceUrl: readUrl(object, 'sourceUrl', `${path}.sourceUrl`, errors, true),
    referenceUrl: readOptionalUrl(object, 'referenceUrl', `${path}.referenceUrl`, errors),
    normalizationNote: readString(object, 'normalizationNote', `${path}.normalizationNote`, errors, { min: 1, max: 500 }),
    scopeNote: readOptionalString(object, 'scopeNote', `${path}.scopeNote`, errors, 500),
    sourceKey: readString(object, 'sourceKey', `${path}.sourceKey`, errors, { min: 1, max: 120 }),
    evidenceText: readOptionalString(object, 'evidenceText', `${path}.evidenceText`, errors, 1000),
    verificationStatus: readEnum(object, 'verificationStatus', `${path}.verificationStatus`, BUDGET_VERIFICATION_STATUSES, errors),
    primary: readBoolean(object, 'primary', `${path}.primary`, errors),
    answer: readBoolean(object, 'answer', `${path}.answer`, errors),
  } satisfies BudgetContentCostClaimInput
}

function readMetric(value: unknown, index: number, errors: string[]) {
  const path = `metrics[${index}]`
  const object = requireRecord(value, path, errors)
  const metricType = readEnum(object, 'metricType', `${path}.metricType`, BUDGET_METRIC_TYPES, errors)
  const normalizedUnit = readString(object, 'normalizedUnit', `${path}.normalizedUnit`, errors, { min: 1, max: 40 })
  if (metricType && normalizedUnit !== BUDGET_NORMALIZED_METRIC_UNITS[metricType]) {
    errors.push(`${path}.normalizedUnit must be ${BUDGET_NORMALIZED_METRIC_UNITS[metricType]} for ${metricType}.`)
  }
  return {
    metricType,
    normalizedValue: readPositiveNumber(object, 'normalizedValue', `${path}.normalizedValue`, errors),
    normalizedUnit,
    sourceValue: readPositiveNumber(object, 'sourceValue', `${path}.sourceValue`, errors),
    sourceUnit: readString(object, 'sourceUnit', `${path}.sourceUnit`, errors, { min: 1, max: 40 }),
    sourceCode: readEnum(object, 'sourceCode', `${path}.sourceCode`, BUDGET_SOURCE_CODES, errors),
    sourceName: readString(object, 'sourceName', `${path}.sourceName`, errors, { min: 1, max: 200 }),
    sourceUrl: readUrl(object, 'sourceUrl', `${path}.sourceUrl`, errors, true),
    referenceUrl: readOptionalUrl(object, 'referenceUrl', `${path}.referenceUrl`, errors),
    sourceKey: readString(object, 'sourceKey', `${path}.sourceKey`, errors, { min: 1, max: 120 }),
    evidenceText: readOptionalString(object, 'evidenceText', `${path}.evidenceText`, errors, 1000),
    primary: readBoolean(object, 'primary', `${path}.primary`, errors),
  } satisfies BudgetContentMetricInput
}

function readMilestone(value: unknown, index: number, errors: string[]) {
  const path = `milestones[${index}]`
  const object = requireRecord(value, path, errors)
  const datePrecision = readEnum(object, 'datePrecision', `${path}.datePrecision`, BUDGET_DATE_PRECISIONS, errors)
  const milestoneDate = readNullableDate(object, 'milestoneDate', `${path}.milestoneDate`, errors)
  const milestoneYear = readNullableInteger(object, 'milestoneYear', `${path}.milestoneYear`, MIN_YEAR, MAX_YEAR, errors)
  if (datePrecision === 'DAY' && (milestoneDate === null || milestoneYear !== null)) {
    errors.push(`${path} with DAY precision requires milestoneDate and milestoneYear=null.`)
  }
  if (datePrecision === 'YEAR' && (milestoneYear === null || milestoneDate !== null)) {
    errors.push(`${path} with YEAR precision requires milestoneYear and milestoneDate=null.`)
  }
  return {
    milestoneType: readEnum(object, 'milestoneType', `${path}.milestoneType`, BUDGET_MILESTONE_TYPES, errors),
    milestoneDate,
    milestoneYear,
    datePrecision,
    sourceCode: readEnum(object, 'sourceCode', `${path}.sourceCode`, BUDGET_SOURCE_CODES, errors),
    sourceName: readString(object, 'sourceName', `${path}.sourceName`, errors, { min: 1, max: 200 }),
    sourceUrl: readUrl(object, 'sourceUrl', `${path}.sourceUrl`, errors, true),
    referenceUrl: readOptionalUrl(object, 'referenceUrl', `${path}.referenceUrl`, errors),
    sourceKey: readString(object, 'sourceKey', `${path}.sourceKey`, errors, { min: 1, max: 120 }),
    evidenceText: readOptionalString(object, 'evidenceText', `${path}.evidenceText`, errors, 1000),
    primary: readBoolean(object, 'primary', `${path}.primary`, errors),
  } satisfies BudgetContentMilestoneInput
}

function validateFactCounts(
  costs: BudgetContentCostClaimInput[],
  metrics: BudgetContentMetricInput[],
  milestones: BudgetContentMilestoneInput[],
  errors: string[],
) {
  if (costs.length === 0) errors.push('costClaims must contain at least one entry.')
  if (costs.length > MAX_FACTS_PER_TYPE) errors.push(`costClaims cannot contain more than ${MAX_FACTS_PER_TYPE} entries.`)
  if (metrics.length > MAX_FACTS_PER_TYPE) errors.push(`metrics cannot contain more than ${MAX_FACTS_PER_TYPE} entries.`)
  if (milestones.length > MAX_FACTS_PER_TYPE) errors.push(`milestones cannot contain more than ${MAX_FACTS_PER_TYPE} entries.`)
}

function validatePrimaryFacts(
  costs: BudgetContentCostClaimInput[],
  metrics: BudgetContentMetricInput[],
  milestones: BudgetContentMilestoneInput[],
  errors: string[],
) {
  const duplicates = [
    duplicatePrimary(costs, (cost) => cost.costKind),
    duplicatePrimary(metrics, (metric) => metric.metricType),
    duplicatePrimary(milestones, (milestone) => milestone.milestoneType),
  ].flat()
  duplicates.forEach((key) => errors.push(`Only one primary entry is allowed for ${key}.`))
}

function validatePlayableCost(costs: BudgetContentCostClaimInput[], errors: string[]) {
  const answers = costs.filter((cost) => cost.answer)
  if (answers.length !== 1 || !answers[0]?.primary) {
    errors.push('Exactly one primary cost claim must have answer=true.')
    return
  }
  const midpoint = (answers[0].amountUsdLow + answers[0].amountUsdHigh) / 2
  if (midpoint < MINIMUM_PLAYABLE_COST || midpoint > MAXIMUM_PLAYABLE_COST) {
    errors.push('Playable cost midpoint must be between USD 1 million and USD 1 trillion.')
  }
}

function duplicatePrimary<T>(values: T[], key: (value: T) => string) {
  const counts = new Map<string, number>()
  values.filter((value) => Boolean((value as { primary?: boolean }).primary)).forEach((value) => {
    const currentKey = key(value)
    counts.set(currentKey, (counts.get(currentKey) ?? 0) + 1)
  })
  return [...counts.entries()].filter(([, count]) => count > 1).map(([value]) => value)
}

function readArray(object: JsonRecord, key: string, errors: string[]) {
  const value = object[key]
  if (!Array.isArray(value)) {
    errors.push(`${key} must be an array.`)
    return []
  }
  return value
}

function requireRecord(value: unknown, path: string, errors: string[]) {
  if (isRecord(value)) return value
  errors.push(`${path} must be an object.`)
  return {} as JsonRecord
}

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readString(
  object: JsonRecord,
  key: string,
  path: string,
  errors: string[],
  bounds: { min: number; max: number },
) {
  const value = object[key]
  if (typeof value !== 'string') {
    errors.push(`${path} must be a string.`)
    return ''
  }
  if (value.trim().length < bounds.min) errors.push(`${path} cannot be blank.`)
  if (value.length > bounds.max) errors.push(`${path} cannot exceed ${bounds.max} characters.`)
  return value
}

function readPatternString(
  object: JsonRecord,
  key: string,
  path: string,
  errors: string[],
  pattern: RegExp,
  expectation: string,
) {
  const value = readString(object, key, path, errors, { min: 1, max: 20 })
  if (value && !pattern.test(value)) errors.push(`${path} must contain ${expectation}.`)
  return value
}

function readEnum<T extends string>(
  object: JsonRecord,
  key: string,
  path: string,
  accepted: readonly T[],
  errors: string[],
) {
  const value = object[key]
  if (typeof value !== 'string' || !accepted.includes(value as T)) {
    errors.push(`${path} must be one of: ${accepted.join(', ')}.`)
    return accepted[0]
  }
  return value as T
}

function readPositiveNumber(object: JsonRecord, key: string, path: string, errors: string[]) {
  const value = object[key]
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
    errors.push(`${path} must be a positive number.`)
    return 0
  }
  return value
}

function readBoolean(object: JsonRecord, key: string, path: string, errors: string[]) {
  const value = object[key]
  if (typeof value !== 'boolean') {
    errors.push(`${path} must be true or false.`)
    return false
  }
  return value
}

function readNullableInteger(
  object: JsonRecord,
  key: string,
  path: string,
  minimum: number,
  maximum: number,
  errors: string[],
) {
  const value = object[key]
  if (value === null) return null
  if (!Number.isInteger(value) || (value as number) < minimum || (value as number) > maximum) {
    errors.push(`${path} must be an integer from ${minimum} to ${maximum}, or null.`)
    return null
  }
  return value as number
}

function readDate(object: JsonRecord, key: string, path: string, errors: string[]) {
  const value = object[key]
  if (typeof value !== 'string' || !isIsoDate(value)) {
    errors.push(`${path} must be a valid YYYY-MM-DD date.`)
    return ''
  }
  return value
}

function readNullableDate(object: JsonRecord, key: string, path: string, errors: string[]) {
  if (object[key] === null) return null
  return readDate(object, key, path, errors)
}

function readUrl(object: JsonRecord, key: string, path: string, errors: string[], httpsOnly: boolean) {
  const value = readString(object, key, path, errors, { min: 1, max: 2000 })
  const pattern = httpsOnly ? /^https:\/\/.+/i : /^https?:\/\/.+/i
  if (value && !pattern.test(value)) errors.push(`${path} must be an ${httpsOnly ? 'HTTPS' : 'HTTP(S)'} URL.`)
  return value
}

function readOptionalUrl(object: JsonRecord, key: string, path: string, errors: string[]) {
  const value = readOptionalString(object, key, path, errors, 2000)
  if (value && !/^https?:\/\/.+/i.test(value)) errors.push(`${path} must be empty or an HTTP(S) URL.`)
  return value
}

function readOptionalString(
  object: JsonRecord,
  key: string,
  path: string,
  errors: string[],
  maximum: number,
) {
  const value = object[key]
  if (value === undefined || value === null) return ''
  if (typeof value !== 'string') {
    errors.push(`${path} must be a string when provided.`)
    return ''
  }
  if (value.length > maximum) errors.push(`${path} cannot exceed ${maximum} characters.`)
  return value
}

function isIsoDate(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.valueOf())
    && date.getUTCFullYear() === Number(match[1])
    && date.getUTCMonth() + 1 === Number(match[2])
    && date.getUTCDate() === Number(match[3])
}

function unwrapMarkdownFence(source: string) {
  const trimmed = source.trim()
  const match = /^```(?:jsonc?|javascript|js)?\s*\r?\n([\s\S]*?)\r?\n```$/i.exec(trimmed)
  return match ? match[1] : source
}

function formatParseError(source: string, error: ParseError) {
  const prefix = source.slice(0, error.offset)
  const line = prefix.split(/\r?\n/).length
  const lastNewline = Math.max(prefix.lastIndexOf('\n'), prefix.lastIndexOf('\r'))
  const column = error.offset - lastNewline
  return `${printParseErrorCode(error.error)} at line ${line}, column ${column}.`
}

function diffCollection<T>(
  path: string,
  before: T[],
  after: T[],
  identity: (value: T) => string,
  fallbackIdentity: (value: T) => string,
  changes: BudgetJsonChange[],
) {
  const unmatchedAfter = new Set(after.map((_, index) => index))
  const matches: Array<{ beforeValue: T; afterValue: T | undefined }> = []

  before.forEach((beforeValue) => {
    const exactIndex = after.findIndex((afterValue, index) => (
      unmatchedAfter.has(index) && identity(afterValue) === identity(beforeValue)
    ))
    if (exactIndex >= 0) {
      unmatchedAfter.delete(exactIndex)
      matches.push({ beforeValue, afterValue: after[exactIndex] })
      return
    }
    matches.push({ beforeValue, afterValue: undefined })
  })

  matches.forEach((match) => {
    if (match.afterValue) return
    const fallbackIndex = after.findIndex((afterValue, index) => (
      unmatchedAfter.has(index)
      && fallbackIdentity(afterValue) === fallbackIdentity(match.beforeValue)
    ))
    if (fallbackIndex >= 0) {
      unmatchedAfter.delete(fallbackIndex)
      match.afterValue = after[fallbackIndex]
    }
  })

  const displayCounts = new Map<string, number>()
  const itemPath = (value: T) => {
    const base = fallbackIdentity(value)
    const count = (displayCounts.get(base) ?? 0) + 1
    displayCounts.set(base, count)
    return `${path}[${base}${count === 1 ? '' : ` #${count}`}]`
  }

  matches.forEach(({ beforeValue, afterValue }) => {
    const currentPath = itemPath(beforeValue)
    if (afterValue === undefined) {
      changes.push({ kind: 'removed', path: currentPath, before: beforeValue })
      return
    }
    diffValue(currentPath, beforeValue, afterValue, changes)
  })
  unmatchedAfter.forEach((index) => {
    const afterValue = after[index]
    changes.push({ kind: 'added', path: itemPath(afterValue), after: afterValue })
  })
}

function diffValue(path: string, before: unknown, after: unknown, changes: BudgetJsonChange[]) {
  if (Object.is(before, after)) return
  if (isRecord(before) && isRecord(after)) {
    const keys = new Set([...Object.keys(before), ...Object.keys(after)])
    keys.forEach((key) => diffValue(`${path}.${key}`, before[key], after[key], changes))
    return
  }
  if (before == null && after != null) {
    changes.push({ kind: 'added', path, after })
    return
  }
  if (before != null && after == null) {
    changes.push({ kind: 'removed', path, before })
    return
  }
  changes.push({ kind: 'changed', path, before, after })
}
