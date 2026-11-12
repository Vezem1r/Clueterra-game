import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { Settings, History } from 'lucide-react'
import { adminApi } from '@/api/admin'
import { gameApi } from '@/api/game'
import {
  SettingsAuditHistoryModal,
} from '@/components/admin/settings/SettingsAuditHistory'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Tabs } from '@/components/ui/Tabs'
import { useTranslation } from '@/i18n'
import { toast } from '@/store/toastStore'
import { GlobalSettingsTab } from './settings/GlobalSettingsTab'
import { BudgetSettingsTab } from './settings/BudgetSettingsTab'
import { GameSettingsTab } from './settings/GameSettingsTab'
import { SettingsSidebar } from './settings/SettingsSidebar'
import type { AppSettings } from '@/types'

const AUDIT_PREVIEW_SIZE = 3
const AUDIT_PAGE_SIZE = 8

const TABS = [
  { id: 'global', label: 'Global Settings', shortLabel: 'Global' },
  { id: 'game', label: 'Game Settings', shortLabel: 'Game' },
  { id: 'budget', label: 'Budget Settings', shortLabel: 'Budget' },
]

type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export function SettingsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [draft, setDraft] = useState<AppSettings | null>(null)
  const [activeTab, setActiveTab] = useState('global')
  const [showConfirm, setShowConfirm] = useState(false)
  const [showCacheClearConfirm, setShowCacheClearConfirm] = useState(false)
  const [showAuditModal, setShowAuditModal] = useState(false)
  const [selectedAuditBatchId, setSelectedAuditBatchId] = useState<string | null>(null)
  const [auditPage, setAuditPage] = useState(0)

  const [calcDifficulty, setCalcDifficulty] = useState<Difficulty>('MEDIUM')
  const [calcHintsRevealed, setCalcHintsRevealed] = useState(1)
  const [calcAttemptsUsed, setCalcAttemptsUsed] = useState(1)
  const [calcTimeSpent, setCalcTimeSpent] = useState(15)

  const { data: settings, isLoading } = useQuery({
    queryKey: ['admin-settings'],
    queryFn: adminApi.getSettings,
  })

  const { data: todayChallenges } = useQuery({
    queryKey: ['admin-today-challenges'],
    queryFn: gameApi.getTodaysChallenges,
    staleTime: 60_000,
  })

  const {
    data: auditPreviewPage,
    isLoading: isAuditPreviewLoading,
    isFetching: isAuditPreviewFetching,
    refetch: refetchAuditPreview,
  } = useQuery({
    queryKey: ['admin-settings-audit', 'preview'],
    queryFn: () => adminApi.getSettingsAudit({ page: 0, size: AUDIT_PREVIEW_SIZE }),
    staleTime: 60_000,
  })

  const {
    data: settingsAuditPage,
    isLoading: isAuditLoading,
    isFetching: isAuditFetching,
    refetch: refetchSettingsAudit,
  } = useQuery({
    queryKey: ['admin-settings-audit', auditPage, AUDIT_PAGE_SIZE],
    queryFn: () => adminApi.getSettingsAudit({ page: auditPage, size: AUDIT_PAGE_SIZE }),
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  })

  if (settings && !draft) {
    setDraft(settings)
  }

  if (draft) {
    if (calcHintsRevealed > draft.hintsPerCountry) setCalcHintsRevealed(draft.hintsPerCountry)
    if (calcAttemptsUsed > draft.maxAttempts) setCalcAttemptsUsed(draft.maxAttempts)
    if (calcTimeSpent > draft.timeBonusThreshold) setCalcTimeSpent(draft.timeBonusThreshold)
  }

  const mutation = useMutation({
    mutationFn: adminApi.updateSettings,
    onSuccess: (updated) => {
      queryClient.setQueryData(['admin-settings'], updated)
      queryClient.invalidateQueries({ queryKey: ['admin-settings-audit'] })
      setAuditPage(0)
      setSelectedAuditBatchId(null)
      setDraft(updated)
      toast.success(t.admin.settingsSaved)
    },
    onError: () => {
      toast.error(t.common.error)
    },
  })

  const cacheClearMutation = useMutation({
    mutationFn: adminApi.invalidateTodayChallengeCache,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-today-challenges'] })
      toast.success('Today\'s challenge cache cleared')
    },
    onError: () => {
      toast.error(t.common.error)
    },
  })

  const hasChanges = !!(draft && settings && (
    draft.registrationEnabled !== settings.registrationEnabled ||
    draft.countriesPerChallenge !== settings.countriesPerChallenge ||
    draft.hintsPerCountry !== settings.hintsPerCountry ||
    draft.baseXpEasy !== settings.baseXpEasy ||
    draft.baseXpMedium !== settings.baseXpMedium ||
    draft.baseXpHard !== settings.baseXpHard ||
    draft.hintsBonusMultiplier !== settings.hintsBonusMultiplier ||
    draft.attemptsBonusMultiplier !== settings.attemptsBonusMultiplier ||
    draft.participationMultiplier !== settings.participationMultiplier ||
    draft.levelDivisor !== settings.levelDivisor ||
    draft.maxAttempts !== settings.maxAttempts ||
    draft.timeBonusMultiplier !== settings.timeBonusMultiplier ||
    draft.timeBonusThreshold !== settings.timeBonusThreshold ||
    draft.autoApproveGeneration !== settings.autoApproveGeneration ||
    draft.autoApproveReview !== settings.autoApproveReview ||
    draft.streakMaxBonusPercent !== settings.streakMaxBonusPercent ||
    draft.streakMaxDays !== settings.streakMaxDays ||
    draft.streakCurvePower !== settings.streakCurvePower ||
    draft.streakFreezeMax !== settings.streakFreezeMax ||
    draft.streakFreezeStreakInterval !== settings.streakFreezeStreakInterval ||
    draft.streakFreezePerBadge !== settings.streakFreezePerBadge ||
    draft.budgetBaseXpPerQuestion !== settings.budgetBaseXpPerQuestion ||
    draft.budgetFullXpThresholdPercent !== settings.budgetFullXpThresholdPercent ||
    draft.budgetXpCurvePower !== settings.budgetXpCurvePower ||
    draft.riverGameBaseXp !== settings.riverGameBaseXp ||
    draft.riverGameHintPenaltyPercent !== settings.riverGameHintPenaltyPercent ||
    draft.silhouetteGameBaseXp !== settings.silhouetteGameBaseXp ||
    draft.silhouetteGameHintPenaltyPercent !== settings.silhouetteGameHintPenaltyPercent ||
    draft.flagGameBaseXp !== settings.flagGameBaseXp ||
    draft.flagGameHintPenaltyPercent !== settings.flagGameHintPenaltyPercent ||
    draft.quizGameAnswerXp !== settings.quizGameAnswerXp
  ))

  const auditPreview = auditPreviewPage?.content ?? []
  const settingsAudit = settingsAuditPage?.content ?? []
  const auditTotalPages = settingsAuditPage?.totalPages ?? 0
  const auditTotalElements = settingsAuditPage?.totalElements ?? 0
  const isAuditRefreshing = isAuditPreviewFetching || isAuditFetching

  const handleOpenAuditModal = (batchId?: string) => {
    setAuditPage(0)
    setSelectedAuditBatchId(batchId ?? null)
    setShowAuditModal(true)
  }

  const handleRefreshAudit = () => {
    void refetchAuditPreview()
    void refetchSettingsAudit()
  }

  const handleSave = () => {
    if (!draft) return
    setShowConfirm(true)
  }

  const handleConfirm = () => {
    if (!draft) return
    setShowConfirm(false)
    mutation.mutate(draft)
  }

  const handleDraftChange = (patch: Partial<AppSettings>) => {
    if (!draft) return
    setDraft({ ...draft, ...patch })
  }

  if (isLoading || !draft || !settings) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-5 h-5 border-2 border-white/20 border-t-white/60 rounded-full animate-spin" />
      </div>
    )
  }

  return (
    <div className="px-4 sm:px-6 py-5 sm:py-6 h-full flex flex-col">
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
        className="w-full space-y-5 flex-1 flex flex-col"
      >
        {/* Header */}
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-white/[.06] border border-white/[.08] flex items-center justify-center">
            <Settings size={20} className="text-white/50" />
          </div>
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-bold">{t.admin.settingsTitle}</h1>
            <p className="text-xs text-white/30 mt-0.5">Configure game parameters and access control</p>
          </div>
          <button
            onClick={() => handleOpenAuditModal()}
            className="hidden sm:inline-flex items-center gap-2 px-3.5 h-10 rounded-xl bg-white/[.04] border border-white/[.08] text-sm font-medium text-white/60 hover:bg-white/[.07] hover:text-white/80 transition-colors cursor-pointer"
          >
            <History size={15} />
            {t.admin.settingsHistory}
          </button>
        </div>

        {/* Tabs */}
        <Tabs tabs={TABS} activeTab={activeTab} onChange={setActiveTab} className="max-w-md" />

        {/* 4-column responsive grid: 3 content + 1 sidebar */}
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-5 flex-1">
          {activeTab === 'global' && (
            <GlobalSettingsTab draft={draft} onChange={handleDraftChange} />
          )}
          {activeTab === 'game' && (
            <GameSettingsTab
              draft={draft}
              onChange={handleDraftChange}
              todayChallenges={todayChallenges}
              calcDifficulty={calcDifficulty}
              setCalcDifficulty={setCalcDifficulty}
              calcHintsRevealed={calcHintsRevealed}
              setCalcHintsRevealed={setCalcHintsRevealed}
              calcAttemptsUsed={calcAttemptsUsed}
              setCalcAttemptsUsed={setCalcAttemptsUsed}
              calcTimeSpent={calcTimeSpent}
              setCalcTimeSpent={setCalcTimeSpent}
            />
          )}
          {activeTab === 'budget' && (
            <BudgetSettingsTab draft={draft} onChange={handleDraftChange} />
          )}

          <SettingsSidebar
            draft={draft}
            settings={settings}
            hasChanges={hasChanges}
            onSave={handleSave}
            isSaving={mutation.isPending}
            showCacheClearConfirm={() => setShowCacheClearConfirm(true)}
            isCacheClearing={cacheClearMutation.isPending}
            auditPreview={auditPreview}
            isAuditPreviewLoading={isAuditPreviewLoading}
            isAuditRefreshing={isAuditRefreshing}
            onRefreshAudit={handleRefreshAudit}
            onOpenAuditModal={handleOpenAuditModal}
          />
        </div>
      </motion.div>

      <ConfirmDialog isOpen={showConfirm} onClose={() => setShowConfirm(false)}
        onConfirm={handleConfirm} title={t.admin.settings} message={t.admin.confirmSettingsChange} />
      <ConfirmDialog isOpen={showCacheClearConfirm} onClose={() => setShowCacheClearConfirm(false)}
        onConfirm={() => { setShowCacheClearConfirm(false); cacheClearMutation.mutate() }}
        title="Clear Cache" message="Are you sure you want to clear today's challenge cache? The next request will fetch fresh data from the database." />

      <SettingsAuditHistoryModal
        isOpen={showAuditModal}
        onClose={() => {
          setShowAuditModal(false)
          setAuditPage(0)
          setSelectedAuditBatchId(null)
        }}
        entries={settingsAudit}
        isLoading={isAuditLoading}
        isRefreshing={isAuditRefreshing}
        onRefresh={handleRefreshAudit}
        page={auditPage}
        pageSize={AUDIT_PAGE_SIZE}
        totalPages={auditTotalPages}
        totalElements={auditTotalElements}
        selectedBatchId={selectedAuditBatchId}
        onSelectBatch={setSelectedAuditBatchId}
        onPageChange={setAuditPage}
      />
    </div>
  )
}
