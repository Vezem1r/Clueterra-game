import { useMemo } from 'react'
import { motion } from 'framer-motion'
import {
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  Clock3,
  History,
  Loader2,
  RefreshCw,
  SlidersHorizontal,
  Users,
} from 'lucide-react'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import type { AppSettings, SettingsAuditEntry } from '@/types'

const SETTING_AUDIT_LABELS: Record<keyof AppSettings, string> = {
  registrationEnabled: 'Registration',
  countriesPerChallenge: 'Countries',
  hintsPerCountry: 'Hints',
  baseXpEasy: 'XP Easy',
  baseXpMedium: 'XP Medium',
  baseXpHard: 'XP Hard',
  hintsBonusMultiplier: 'Hints bonus',
  attemptsBonusMultiplier: 'Attempts bonus',
  participationMultiplier: 'Participation',
  levelDivisor: 'Divisor',
  maxAttempts: 'Attempts',
  timeBonusMultiplier: 'Time bonus',
  timeBonusThreshold: 'Time threshold',
  autoApproveGeneration: 'Auto-approve generation',
  autoApproveReview: 'Auto-approve review',
  streakMaxBonusPercent: 'Streak bonus max',
  streakMaxDays: 'Streak max days',
  streakCurvePower: 'Streak curve',
  streakFreezeMax: 'Shield max',
  streakFreezeStreakInterval: 'Shield interval',
  streakFreezePerBadge: 'Shields per badge',
  budgetBaseXpPerQuestion: 'Budget base XP',
  budgetFullXpThresholdPercent: 'Budget full XP tolerance',
  budgetXpCurvePower: 'Budget XP curve',
  riverGameBaseXp: 'River base XP',
  riverGameHintPenaltyPercent: 'River hint penalty',
  silhouetteGameBaseXp: 'Silhouette base XP',
  silhouetteGameHintPenaltyPercent: 'Silhouette hint penalty',
  flagGameBaseXp: 'Flag base XP',
  flagGameHintPenaltyPercent: 'Flag hint penalty',
  quizGameAnswerXp: 'World quiz XP per answer',
}

const PERCENTAGE_AUDIT_FIELDS = new Set<keyof AppSettings>([
  'hintsBonusMultiplier',
  'attemptsBonusMultiplier',
  'participationMultiplier',
  'timeBonusMultiplier',
])

const SECOND_AUDIT_FIELDS = new Set<keyof AppSettings>(['timeBonusThreshold'])

const RAW_PERCENTAGE_AUDIT_FIELDS = new Set<keyof AppSettings>([
  'streakMaxBonusPercent',
  'budgetFullXpThresholdPercent',
  'riverGameHintPenaltyPercent',
  'silhouetteGameHintPenaltyPercent',
  'flagGameHintPenaltyPercent',
])

interface SettingsAuditSidebarCardProps {
  preview: SettingsAuditEntry[]
  isLoading: boolean
  isRefreshing: boolean
  onRefresh: () => void
  onOpenHistory: (batchId?: string) => void
}

interface SettingsAuditHistoryModalProps {
  isOpen: boolean
  onClose: () => void
  entries: SettingsAuditEntry[]
  isLoading: boolean
  isRefreshing: boolean
  onRefresh: () => void
  page: number
  pageSize: number
  totalPages: number
  totalElements: number
  selectedBatchId: string | null
  onSelectBatch: (batchId: string | null) => void
  onPageChange: (page: number) => void
}

export function SettingsAuditSidebarCard({
  preview,
  isLoading,
  isRefreshing,
  onRefresh,
  onOpenHistory,
}: SettingsAuditSidebarCardProps) {
  const { t } = useTranslation()

  return (
    <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5 overflow-hidden">
      <div className="flex items-start justify-between gap-3 mb-4">
        <div className="flex items-start gap-3 min-w-0">
          <div className="w-8 h-8 rounded-lg bg-white/[.04] border border-white/[.06] flex items-center justify-center shrink-0">
            <History size={15} className="text-white/45" />
          </div>
          <div className="min-w-0">
            <h2 className="text-sm font-semibold">{t.admin.settingsHistory}</h2>
            <p className="text-[11px] text-white/25 mt-0.5 truncate">{t.admin.settingsHistorySubtitle}</p>
          </div>
        </div>
        <button
          onClick={onRefresh}
          className="shrink-0 inline-flex items-center justify-center w-8 h-8 rounded-lg bg-white/[.04] border border-white/[.08] text-white/45 hover:bg-white/[.07] hover:text-white/75 transition-colors cursor-pointer"
          aria-label={t.common.refresh}
        >
          {isRefreshing ? <Loader2 size={13} className="animate-spin" /> : <RefreshCw size={13} />}
        </button>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center h-36">
          <div className="w-5 h-5 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
        </div>
      ) : preview.length === 0 ? (
        <div className="rounded-xl border border-white/[.06] bg-white/[.02] px-4 py-8 text-center">
          <p className="text-sm text-white/30">{t.admin.settingsHistoryEmpty}</p>
        </div>
      ) : (
        <>
          <div className="space-y-3 max-h-[320px] overflow-y-auto pr-1">
            {preview.map((entry) => (
              <SettingsAuditPreview key={entry.batchId} entry={entry} onOpen={onOpenHistory} />
            ))}
          </div>

          <button
            onClick={() => onOpenHistory()}
            className="mt-4 w-full h-9 rounded-lg bg-white/[.04] border border-white/[.08] text-xs font-medium text-white/60 hover:bg-white/[.08] hover:text-white/80 transition-colors cursor-pointer"
          >
            View Full History
          </button>
        </>
      )}
    </div>
  )
}

export function SettingsAuditHistoryModal({
  isOpen,
  onClose,
  entries,
  isLoading,
  isRefreshing,
  onRefresh,
  page,
  pageSize,
  totalPages,
  totalElements,
  selectedBatchId,
  onSelectBatch,
  onPageChange,
}: SettingsAuditHistoryModalProps) {
  const { t } = useTranslation()

  const selectedEntry = useMemo(
    () => entries.find((entry) => entry.batchId === selectedBatchId) ?? entries[0] ?? null,
    [entries, selectedBatchId]
  )

  const effectiveSelectedBatchId = selectedEntry?.batchId ?? null
  const rangeStart = entries.length === 0 ? 0 : page * pageSize + 1
  const rangeEnd = entries.length === 0 ? 0 : Math.min(totalElements, page * pageSize + entries.length)

  const stats = useMemo(() => ({
    totalChanges: entries.reduce((total, entry) => total + entry.changes.length, 0),
    uniqueAdmins: new Set(entries.map(getAuditActorIdentity)).size,
    latestChangedAt: entries[0]?.changedAt ?? null,
  }), [entries])

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={t.admin.settingsHistory}
      className="max-w-6xl h-[min(88vh,860px)] flex flex-col"
    >
      <div className="space-y-5">
        <div className="rounded-[28px] border border-white/[.08] bg-[radial-gradient(circle_at_top_left,rgba(255,255,255,0.07),transparent_50%),linear-gradient(180deg,rgba(255,255,255,0.03),rgba(255,255,255,0.01))] px-5 py-5 sm:px-6">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div className="max-w-2xl">
              <p className="text-[11px] uppercase tracking-[0.28em] text-white/35">Settings Audit</p>
              <p className="text-sm text-white/72 mt-2">{t.admin.settingsHistorySubtitle}</p>
              <p className="text-xs text-white/32 mt-1.5">
                Browse recent save batches, inspect exact before/after values, and move through older config changes without a giant endless list.
              </p>
            </div>
            <button
              onClick={onRefresh}
              className="self-start inline-flex items-center gap-2 px-3.5 h-10 rounded-xl bg-white/[.05] border border-white/[.08] text-sm font-medium text-white/60 hover:bg-white/[.08] hover:text-white/78 transition-colors cursor-pointer"
            >
              {isRefreshing ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
              {t.common.refresh}
            </button>
          </div>

          {!isLoading && entries.length > 0 && (
            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              <SettingsAuditStatCard
                icon={History}
                label="Visible Batches"
                value={rangeStart === 0 ? '0' : `${rangeStart}-${rangeEnd}`}
                hint={`of ${totalElements} total saves`}
              />
              <SettingsAuditStatCard
                icon={SlidersHorizontal}
                label="Field Changes"
                value={String(stats.totalChanges)}
                hint="changes on this page"
              />
              <SettingsAuditStatCard
                icon={stats.uniqueAdmins > 1 ? Users : Clock3}
                label={stats.uniqueAdmins > 1 ? 'Admins Involved' : 'Latest Save'}
                value={stats.uniqueAdmins > 1 ? String(stats.uniqueAdmins) : formatAuditRelativeTimestamp(stats.latestChangedAt)}
                hint={stats.uniqueAdmins > 1 ? 'unique actors on this page' : formatAuditTimestamp(stats.latestChangedAt)}
              />
            </div>
          )}
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-5 h-5 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
          </div>
        ) : entries.length === 0 ? (
          <div className="rounded-[28px] border border-white/[.06] bg-white/[.02] px-4 py-12 text-center">
            <p className="text-sm text-white/30">{t.admin.settingsHistoryEmpty}</p>
          </div>
        ) : (
          <div className="grid gap-4 xl:grid-cols-[320px_minmax(0,1fr)]">
            <div className="rounded-[28px] border border-white/[.08] bg-white/[.02] overflow-hidden">
              <div className="flex items-center justify-between gap-3 px-4 py-4 border-b border-white/[.06] bg-white/[.03]">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.2em] text-white/35">Save Batches</p>
                  <p className="text-[11px] text-white/28 mt-1">Pick a save to inspect exact field diffs</p>
                </div>
                <Badge variant="neutral" className="text-[10px]">
                  {totalElements}
                </Badge>
              </div>

              <div className="max-h-[360px] xl:max-h-[520px] overflow-y-auto p-3 space-y-2.5">
                {entries.map((entry) => (
                  <SettingsAuditListItem
                    key={entry.batchId}
                    entry={entry}
                    selected={entry.batchId === effectiveSelectedBatchId}
                    onSelect={() => onSelectBatch(entry.batchId)}
                  />
                ))}
              </div>
            </div>

            {selectedEntry && (
              <div className="rounded-[28px] border border-white/[.08] bg-white/[.02] overflow-hidden">
                <div className="border-b border-white/[.06] bg-white/[.03] px-5 py-5">
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div className="flex items-start gap-4 min-w-0">
                      <div className="w-12 h-12 rounded-2xl border border-white/[.08] bg-white/[.05] flex items-center justify-center shrink-0">
                        <span className="text-sm font-semibold text-white/72">
                          {getAuditActorInitials(selectedEntry)}
                        </span>
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="text-base font-semibold text-white/86 truncate">{getAuditActorLabel(selectedEntry)}</p>
                          <Badge variant="neutral" className="text-[10px]">
                            {selectedEntry.changes.length} changes
                          </Badge>
                        </div>
                        {getAuditActorMeta(selectedEntry) && (
                          <p className="text-xs text-white/32 mt-1 truncate">{getAuditActorMeta(selectedEntry)}</p>
                        )}
                        <p className="text-xs text-white/36 mt-2">
                          {formatAuditTimestamp(selectedEntry.changedAt)} · {formatAuditRelativeTimestamp(selectedEntry.changedAt)}
                        </p>
                      </div>
                    </div>

                    <div className="rounded-2xl border border-white/[.08] bg-black/20 px-3.5 py-3 shrink-0">
                      <p className="text-[10px] uppercase tracking-[0.24em] text-white/28">Batch</p>
                      <p className="text-xs font-mono text-white/68 mt-1">{formatAuditBatchLabel(selectedEntry.batchId)}</p>
                    </div>
                  </div>
                </div>

                <div className="p-5 space-y-5">
                  <div className="grid gap-3 sm:grid-cols-3">
                    <SettingsAuditStatCard
                      icon={SlidersHorizontal}
                      label="Changed Fields"
                      value={String(selectedEntry.changes.length)}
                      hint="within this save batch"
                    />
                    <SettingsAuditStatCard
                      icon={Users}
                      label="Actor"
                      value={getAuditActorLabel(selectedEntry)}
                      hint={getAuditActorMeta(selectedEntry) || 'admin action'}
                    />
                    <SettingsAuditStatCard
                      icon={Clock3}
                      label="Saved"
                      value={formatAuditRelativeTimestamp(selectedEntry.changedAt)}
                      hint={formatAuditTimestamp(selectedEntry.changedAt)}
                    />
                  </div>

                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.2em] text-white/35">Touched Settings</p>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {selectedEntry.changes.map((change) => (
                        <span
                          key={`${selectedEntry.batchId}-${change.fieldName}-tag`}
                          className="inline-flex items-center rounded-full border border-white/[.08] bg-white/[.04] px-3 py-1.5 text-[11px] text-white/58"
                        >
                          {getAuditSettingLabel(change.fieldName)}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="grid gap-3 lg:grid-cols-2">
                    {selectedEntry.changes.map((change) => (
                      <SettingsAuditChangeCard
                        key={`${selectedEntry.batchId}-${change.fieldName}`}
                        change={change}
                      />
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {totalElements > 0 && (
          <div className="flex flex-col gap-3 rounded-[24px] border border-white/[.08] bg-white/[.02] px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-sm text-white/72">
                Showing {rangeStart}-{rangeEnd} of {totalElements} save batches
              </p>
              <p className="text-xs text-white/32 mt-1">
                Page {Math.min(page + 1, Math.max(totalPages, 1))} of {Math.max(totalPages, 1)}
              </p>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={() => onPageChange(Math.max(0, page - 1))}
                disabled={page === 0 || isRefreshing}
                className="inline-flex items-center gap-2 px-3 h-10 rounded-xl bg-white/[.04] border border-white/[.08] text-sm font-medium text-white/55 hover:bg-white/[.07] hover:text-white/78 transition-colors disabled:opacity-35 disabled:cursor-not-allowed cursor-pointer"
              >
                <ChevronLeft size={15} />
                Previous
              </button>
              <button
                onClick={() => onPageChange(Math.min(Math.max(totalPages - 1, 0), page + 1))}
                disabled={page >= totalPages - 1 || isRefreshing}
                className="inline-flex items-center gap-2 px-3 h-10 rounded-xl bg-white/[.04] border border-white/[.08] text-sm font-medium text-white/55 hover:bg-white/[.07] hover:text-white/78 transition-colors disabled:opacity-35 disabled:cursor-not-allowed cursor-pointer"
              >
                Next
                <ChevronRight size={15} />
              </button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  )
}

function SettingsAuditPreview({ entry, onOpen }: { entry: SettingsAuditEntry; onOpen: (batchId?: string) => void }) {
  const topChanges = entry.changes.slice(0, 2)

  return (
    <button
      type="button"
      onClick={() => onOpen(entry.batchId)}
      className="w-full text-left rounded-2xl border border-white/[.06] bg-white/[.02] p-3.5 hover:bg-white/[.04] hover:border-white/[.1] transition-colors cursor-pointer"
    >
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 rounded-2xl border border-white/[.08] bg-white/[.04] flex items-center justify-center shrink-0">
          <span className="text-xs font-semibold text-white/65">{getAuditActorInitials(entry)}</span>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-sm font-semibold text-white/78 truncate">{getAuditActorLabel(entry)}</p>
              <p className="text-[11px] text-white/30 mt-1">{formatAuditTimestamp(entry.changedAt)}</p>
            </div>
            <Badge variant="neutral" className="text-[10px] shrink-0">
              {entry.changes.length} changes
            </Badge>
          </div>

          <div className="mt-3 flex flex-wrap gap-1.5">
            {topChanges.map((change) => (
              <span
                key={`${entry.batchId}-${change.fieldName}`}
                className="inline-flex items-center rounded-full border border-white/[.08] bg-white/[.04] px-2.5 py-1 text-[10px] text-white/55"
              >
                {getAuditSettingLabel(change.fieldName)}
              </span>
            ))}
            {entry.changes.length > topChanges.length && (
              <span className="inline-flex items-center rounded-full border border-white/[.08] bg-white/[.03] px-2.5 py-1 text-[10px] text-white/35">
                +{entry.changes.length - topChanges.length}
              </span>
            )}
          </div>
        </div>
      </div>
    </button>
  )
}

function SettingsAuditStatCard({ icon: Icon, label, value, hint }: {
  icon: typeof History
  label: string
  value: string
  hint: string
}) {
  return (
    <div className="rounded-2xl border border-white/[.08] bg-black/20 px-4 py-3.5">
      <div className="flex items-center gap-2 text-white/34">
        <Icon size={14} />
        <p className="text-[11px] uppercase tracking-[0.18em]">{label}</p>
      </div>
      <p className="text-lg font-semibold text-white/86 mt-3 truncate">{value}</p>
      <p className="text-xs text-white/30 mt-1 truncate">{hint}</p>
    </div>
  )
}

function SettingsAuditListItem({ entry, selected, onSelect }: {
  entry: SettingsAuditEntry
  selected: boolean
  onSelect: () => void
}) {
  const visibleChanges = entry.changes.slice(0, 3)

  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        'w-full rounded-2xl border p-3.5 text-left transition-colors cursor-pointer',
        selected
          ? 'border-accent/28 bg-accent/[.08]'
          : 'border-white/[.06] bg-white/[.02] hover:bg-white/[.04] hover:border-white/[.1]'
      )}
    >
      <div className="flex items-start gap-3">
        <div className={cn(
          'w-10 h-10 rounded-2xl border flex items-center justify-center shrink-0',
          selected ? 'border-accent/24 bg-accent/[.12]' : 'border-white/[.08] bg-white/[.04]'
        )}>
          <span className={cn('text-xs font-semibold', selected ? 'text-white/82' : 'text-white/62')}>
            {getAuditActorInitials(entry)}
          </span>
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className={cn('text-sm font-semibold truncate', selected ? 'text-white' : 'text-white/82')}>
                {getAuditActorLabel(entry)}
              </p>
              <p className="text-[11px] text-white/30 mt-1 truncate">{formatAuditTimestamp(entry.changedAt)}</p>
            </div>
            <div className="text-right shrink-0">
              <p className="text-[11px] text-white/42">{formatAuditRelativeTimestamp(entry.changedAt)}</p>
              <p className="text-[10px] text-white/24 mt-1">{entry.changes.length} fields</p>
            </div>
          </div>

          <div className="mt-3 flex flex-wrap gap-1.5">
            {visibleChanges.map((change) => (
              <span
                key={`${entry.batchId}-${change.fieldName}`}
                className={cn(
                  'inline-flex items-center rounded-full px-2.5 py-1 text-[10px] border',
                  selected
                    ? 'border-accent/22 bg-accent/[.1] text-white/68'
                    : 'border-white/[.08] bg-white/[.04] text-white/52'
                )}
              >
                {getAuditSettingLabel(change.fieldName)}
              </span>
            ))}
            {entry.changes.length > visibleChanges.length && (
              <span className="inline-flex items-center rounded-full border border-white/[.08] bg-white/[.03] px-2.5 py-1 text-[10px] text-white/35">
                +{entry.changes.length - visibleChanges.length}
              </span>
            )}
          </div>
        </div>

        <ArrowRight size={15} className={cn('shrink-0 mt-1', selected ? 'text-white/62' : 'text-white/18')} />
      </div>
    </button>
  )
}

function SettingsAuditChangeCard({ change }: { change: SettingsAuditEntry['changes'][number] }) {
  const formattedOldValue = formatAuditSettingValue(change.fieldName, change.oldValue)
  const formattedNewValue = formatAuditSettingValue(change.fieldName, change.newValue)

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="rounded-2xl border border-white/[.06] bg-white/[.03] px-4 py-4"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-white/82 truncate">{getAuditSettingLabel(change.fieldName)}</p>
          <p className="text-[11px] text-white/24 mt-1 font-mono truncate">{change.fieldName}</p>
        </div>
        <Badge variant="neutral" className="text-[10px] shrink-0">
          updated
        </Badge>
      </div>

      <div className="grid gap-2.5 sm:grid-cols-2">
        <AuditValueBlock label="Before" value={formattedOldValue} tone="old" />
        <AuditValueBlock label="After" value={formattedNewValue} tone="new" />
      </div>
    </motion.div>
  )
}

function AuditValueBlock({ label, value, tone }: { label: string; value: string; tone: 'old' | 'new' }) {
  return (
    <div
      className={cn(
        'min-w-0 rounded-xl border px-3 py-2.5',
        tone === 'old'
          ? 'border-white/[.06] bg-white/[.03]'
          : 'border-accent/16 bg-accent/[.08]'
      )}
    >
      <p className="text-[10px] uppercase tracking-[0.18em] text-white/30">{label}</p>
      <p className={cn(
        'mt-2 text-sm font-mono break-words',
        tone === 'old' ? 'text-white/46' : 'text-white/86'
      )}>
        {value}
      </p>
    </div>
  )
}

function getAuditSettingLabel(fieldName: string) {
  return SETTING_AUDIT_LABELS[fieldName as keyof AppSettings] ?? fieldName
}

function getAuditActorLabel(entry: SettingsAuditEntry) {
  return entry.changedByUsername
    ? `@${entry.changedByUsername}`
    : entry.changedByEmail || 'Unknown admin'
}

function getAuditActorMeta(entry: SettingsAuditEntry) {
  return entry.changedByUsername && entry.changedByEmail
    ? entry.changedByEmail
    : null
}

function getAuditActorIdentity(entry: SettingsAuditEntry) {
  return entry.changedByUserId
    || entry.changedByUsername
    || entry.changedByEmail
    || entry.batchId
}

function getAuditActorInitials(entry: SettingsAuditEntry) {
  const baseLabel = getAuditActorLabel(entry).replace(/^@/, '')
  const parts = baseLabel.split(/[\s._-]+/).filter(Boolean)
  const initials = parts.slice(0, 2).map((part) => part[0]?.toUpperCase() ?? '').join('')
  return initials || 'AD'
}

function formatAuditSettingValue(fieldName: string, value: string | null | undefined) {
  if (value == null || value === '') return '—'

  const typedField = fieldName as keyof AppSettings
  if (typedField === 'registrationEnabled' || typedField === 'autoApproveGeneration' || typedField === 'autoApproveReview') {
    return value === 'true' ? 'On' : 'Off'
  }
  if (PERCENTAGE_AUDIT_FIELDS.has(typedField)) {
    const numeric = Number(value)
    return Number.isFinite(numeric) ? `${Math.round(numeric * 100)}%` : value
  }
  if (RAW_PERCENTAGE_AUDIT_FIELDS.has(typedField)) {
    const numeric = Number(value)
    return Number.isFinite(numeric) ? `${Math.round(numeric)}%` : value
  }
  if (SECOND_AUDIT_FIELDS.has(typedField)) {
    return `${value}s`
  }
  return value
}

function formatAuditTimestamp(value: string | null | undefined) {
  if (!value) return 'No data'

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return 'No data'

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(parsed)
}

function formatAuditRelativeTimestamp(value: string | null | undefined) {
  if (!value) return 'No data'

  const target = new Date(value).getTime()
  if (Number.isNaN(target)) return 'No data'

  const diffMs = target - Date.now()
  const diffMinutes = Math.round(diffMs / 60_000)
  const diffHours = Math.round(diffMs / 3_600_000)
  const diffDays = Math.round(diffMs / 86_400_000)
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })

  if (Math.abs(diffMinutes) < 60) {
    return formatter.format(diffMinutes, 'minute')
  }
  if (Math.abs(diffHours) < 24) {
    return formatter.format(diffHours, 'hour')
  }
  return formatter.format(diffDays, 'day')
}

function formatAuditBatchLabel(batchId: string) {
  return batchId.split('-')[0]?.toUpperCase() || batchId
}
