import { motion } from 'framer-motion'
import { Info, Loader2, Database } from 'lucide-react'
import {
  SettingsAuditSidebarCard,
} from '@/components/admin/settings/SettingsAuditHistory'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import type { AppSettings, SettingsAuditEntry } from '@/types'

interface SettingsSidebarProps {
  draft: AppSettings
  settings: AppSettings
  hasChanges: boolean
  onSave: () => void
  isSaving: boolean
  showCacheClearConfirm: () => void
  isCacheClearing: boolean
  auditPreview: SettingsAuditEntry[]
  isAuditPreviewLoading: boolean
  isAuditRefreshing: boolean
  onRefreshAudit: () => void
  onOpenAuditModal: (batchId?: string) => void
}

export function SettingsSidebar({
  draft, settings, hasChanges, onSave, isSaving,
  showCacheClearConfirm, isCacheClearing,
  auditPreview, isAuditPreviewLoading, isAuditRefreshing,
  onRefreshAudit, onOpenAuditModal,
}: SettingsSidebarProps) {
  const { t } = useTranslation()

  return (
    <div className="space-y-5 xl:self-start xl:sticky xl:top-5">
      {hasChanges ? (
        <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}
          className="rounded-xl bg-accent/[.04] border border-accent/15 p-5">
          <h3 className="text-xs font-semibold text-accent/70 uppercase tracking-widest mb-4">Pending Changes</h3>
          <div className="space-y-2 mb-5">
            {draft.registrationEnabled !== settings.registrationEnabled && (
              <ChangeRow label="Registration" oldVal={settings.registrationEnabled ? 'On' : 'Off'} newVal={draft.registrationEnabled ? 'On' : 'Off'} highlight={draft.registrationEnabled} />
            )}
            {draft.autoApproveGeneration !== settings.autoApproveGeneration && (
              <ChangeRow label="Auto-gen" oldVal={settings.autoApproveGeneration ? 'On' : 'Off'} newVal={draft.autoApproveGeneration ? 'On' : 'Off'} highlight={draft.autoApproveGeneration} />
            )}
            {draft.autoApproveReview !== settings.autoApproveReview && (
              <ChangeRow label="Auto-review" oldVal={settings.autoApproveReview ? 'On' : 'Off'} newVal={draft.autoApproveReview ? 'On' : 'Off'} highlight={draft.autoApproveReview} />
            )}
            {draft.streakMaxBonusPercent !== settings.streakMaxBonusPercent && (
              <ChangeRow label="Streak bonus" oldVal={`${settings.streakMaxBonusPercent}%`} newVal={`${draft.streakMaxBonusPercent}%`} />
            )}
            {draft.streakMaxDays !== settings.streakMaxDays && (
              <ChangeRow label="Streak days" oldVal={settings.streakMaxDays} newVal={draft.streakMaxDays} />
            )}
            {draft.streakCurvePower !== settings.streakCurvePower && (
              <ChangeRow label="Curve power" oldVal={settings.streakCurvePower.toFixed(1)} newVal={draft.streakCurvePower.toFixed(1)} />
            )}
            {draft.streakFreezeMax !== settings.streakFreezeMax && (
              <ChangeRow label="Shield max" oldVal={settings.streakFreezeMax} newVal={draft.streakFreezeMax} />
            )}
            {draft.streakFreezeStreakInterval !== settings.streakFreezeStreakInterval && (
              <ChangeRow label="Shield interval" oldVal={settings.streakFreezeStreakInterval} newVal={draft.streakFreezeStreakInterval} />
            )}
            {draft.streakFreezePerBadge !== settings.streakFreezePerBadge && (
              <ChangeRow label="Shields/badge" oldVal={settings.streakFreezePerBadge} newVal={draft.streakFreezePerBadge} />
            )}
            {draft.countriesPerChallenge !== settings.countriesPerChallenge && (
              <ChangeRow label="Countries" oldVal={settings.countriesPerChallenge} newVal={draft.countriesPerChallenge} />
            )}
            {draft.hintsPerCountry !== settings.hintsPerCountry && (
              <ChangeRow label="Hints" oldVal={settings.hintsPerCountry} newVal={draft.hintsPerCountry} />
            )}
            {draft.baseXpEasy !== settings.baseXpEasy && (
              <ChangeRow label="XP Easy" oldVal={settings.baseXpEasy} newVal={draft.baseXpEasy} />
            )}
            {draft.baseXpMedium !== settings.baseXpMedium && (
              <ChangeRow label="XP Medium" oldVal={settings.baseXpMedium} newVal={draft.baseXpMedium} />
            )}
            {draft.baseXpHard !== settings.baseXpHard && (
              <ChangeRow label="XP Hard" oldVal={settings.baseXpHard} newVal={draft.baseXpHard} />
            )}
            {draft.hintsBonusMultiplier !== settings.hintsBonusMultiplier && (
              <ChangeRow label="Hints bonus" oldVal={`${Math.round(settings.hintsBonusMultiplier * 100)}%`} newVal={`${Math.round(draft.hintsBonusMultiplier * 100)}%`} />
            )}
            {draft.attemptsBonusMultiplier !== settings.attemptsBonusMultiplier && (
              <ChangeRow label="Att. bonus" oldVal={`${Math.round(settings.attemptsBonusMultiplier * 100)}%`} newVal={`${Math.round(draft.attemptsBonusMultiplier * 100)}%`} />
            )}
            {draft.timeBonusMultiplier !== settings.timeBonusMultiplier && (
              <ChangeRow label="Time bonus" oldVal={`${Math.round(settings.timeBonusMultiplier * 100)}%`} newVal={`${Math.round(draft.timeBonusMultiplier * 100)}%`} />
            )}
            {draft.timeBonusThreshold !== settings.timeBonusThreshold && (
              <ChangeRow label="Time threshold" oldVal={`${settings.timeBonusThreshold}s`} newVal={`${draft.timeBonusThreshold}s`} />
            )}
            {draft.participationMultiplier !== settings.participationMultiplier && (
              <ChangeRow label="Participation" oldVal={`${Math.round(settings.participationMultiplier * 100)}%`} newVal={`${Math.round(draft.participationMultiplier * 100)}%`} />
            )}
            {draft.levelDivisor !== settings.levelDivisor && (
              <ChangeRow label="Divisor" oldVal={settings.levelDivisor} newVal={draft.levelDivisor} />
            )}
            {draft.maxAttempts !== settings.maxAttempts && (
              <ChangeRow label="Attempts" oldVal={settings.maxAttempts} newVal={draft.maxAttempts} />
            )}
            {draft.budgetBaseXpPerQuestion !== settings.budgetBaseXpPerQuestion && (
              <ChangeRow label="Budget base XP" oldVal={settings.budgetBaseXpPerQuestion} newVal={draft.budgetBaseXpPerQuestion} />
            )}
            {draft.budgetFullXpThresholdPercent !== settings.budgetFullXpThresholdPercent && (
              <ChangeRow label="Budget tolerance" oldVal={`±${settings.budgetFullXpThresholdPercent}%`} newVal={`±${draft.budgetFullXpThresholdPercent}%`} />
            )}
            {draft.budgetXpCurvePower !== settings.budgetXpCurvePower && (
              <ChangeRow label="Budget curve" oldVal={settings.budgetXpCurvePower.toFixed(2)} newVal={draft.budgetXpCurvePower.toFixed(2)} />
            )}
            {draft.riverGameBaseXp !== settings.riverGameBaseXp && (
              <ChangeRow label="River base XP" oldVal={settings.riverGameBaseXp} newVal={draft.riverGameBaseXp} />
            )}
            {draft.riverGameHintPenaltyPercent !== settings.riverGameHintPenaltyPercent && (
              <ChangeRow label="River hint penalty" oldVal={`${settings.riverGameHintPenaltyPercent}%`} newVal={`${draft.riverGameHintPenaltyPercent}%`} />
            )}
            {draft.silhouetteGameBaseXp !== settings.silhouetteGameBaseXp && (
              <ChangeRow label="Silhouette base XP" oldVal={settings.silhouetteGameBaseXp} newVal={draft.silhouetteGameBaseXp} />
            )}
            {draft.silhouetteGameHintPenaltyPercent !== settings.silhouetteGameHintPenaltyPercent && (
              <ChangeRow label="Silhouette hint penalty" oldVal={`${settings.silhouetteGameHintPenaltyPercent}%`} newVal={`${draft.silhouetteGameHintPenaltyPercent}%`} />
            )}
            {draft.flagGameBaseXp !== settings.flagGameBaseXp && (
              <ChangeRow label="Flag base XP" oldVal={settings.flagGameBaseXp} newVal={draft.flagGameBaseXp} />
            )}
            {draft.flagGameHintPenaltyPercent !== settings.flagGameHintPenaltyPercent && (
              <ChangeRow label="Flag hint penalty" oldVal={`${settings.flagGameHintPenaltyPercent}%`} newVal={`${draft.flagGameHintPenaltyPercent}%`} />
            )}
            {draft.quizGameAnswerXp !== settings.quizGameAnswerXp && (
              <ChangeRow label="World quiz XP per answer" oldVal={settings.quizGameAnswerXp} newVal={draft.quizGameAnswerXp} />
            )}
          </div>
          <button onClick={onSave} disabled={isSaving}
            className="w-full flex items-center justify-center gap-2 h-10 rounded-lg bg-white text-black text-sm font-semibold hover:bg-white/90 transition-colors cursor-pointer">
            {isSaving ? <Loader2 size={14} className="animate-spin" /> : t.common.save}
          </button>
        </motion.div>
      ) : (
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <p className="text-sm text-white/20 text-center">No unsaved changes</p>
        </div>
      )}

      <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
        <div className="flex items-start gap-3">
          <Info size={15} className="text-white/20 shrink-0 mt-0.5" />
          <div className="space-y-2.5">
            <p className="text-xs text-white/35 leading-relaxed">{t.admin.settingsNote}</p>
            <p className="text-xs text-white/25 leading-relaxed">
              XP and leveling changes apply to future game sessions only. Existing player XP and levels are not retroactively recalculated.
            </p>
          </div>
        </div>
      </div>

      <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-8 h-8 rounded-lg bg-rose-500/10 flex items-center justify-center shrink-0">
            <Database size={15} className="text-rose-400/70" />
          </div>
          <h2 className="text-sm font-semibold">Cache Management</h2>
        </div>
        <p className="text-xs text-white/30 mb-4">Clear the cached challenge for today so fresh data is served on next request.</p>
        <button
          onClick={showCacheClearConfirm}
          disabled={isCacheClearing}
          className="w-full flex items-center justify-center gap-2 h-9 rounded-lg bg-white/[.06] border border-white/[.08] text-xs font-medium text-white/60 hover:bg-white/[.1] hover:text-white/80 transition-colors cursor-pointer"
        >
          {isCacheClearing ? <Loader2 size={13} className="animate-spin" /> : <Database size={13} />}
          Clear Today's Challenge Cache
        </button>
      </div>

      <SettingsAuditSidebarCard
        preview={auditPreview}
        isLoading={isAuditPreviewLoading}
        isRefreshing={isAuditRefreshing}
        onRefresh={onRefreshAudit}
        onOpenHistory={onOpenAuditModal}
      />
    </div>
  )
}

function ChangeRow({ label, oldVal, newVal, highlight }: {
  label: string; oldVal: string | number; newVal: string | number; highlight?: boolean
}) {
  return (
    <div className="flex items-center justify-between text-sm">
      <span className="text-white/40">{label}</span>
      <div className="flex items-center gap-2">
        <span className="text-white/25">{oldVal}</span>
        <span className="text-white/15">&rarr;</span>
        <span className={cn('font-mono font-bold', highlight ? 'text-success' : 'text-white/80')}>{newVal}</span>
      </div>
    </div>
  )
}
