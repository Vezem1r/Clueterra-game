import { useState, useRef, useEffect, useCallback } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Play, Database, Sparkles, RefreshCw,
  Loader2, Terminal, Heart, Calendar, Lightbulb, Zap, Trash2, Server,
  Globe2, Landmark, ImageIcon, CircleX, Flag,
} from 'lucide-react'

import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { SourceSelect } from '@/components/admin/SourceSelect'
import { Modal } from '@/components/ui/Modal'
import { adminApi, type ConsoleService } from '@/api/admin'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import { useOperationPolling } from '@/hooks/useOperationPolling'
import { cn } from '@/utils/cn'

interface ConsoleEntry {
  time: string
  text: string
  type: 'info' | 'success' | 'error' | 'system'
  timestamp: number
}

const MAX_CONSOLE_ENTRIES = 500

export function OperationsPage() {
  const { t } = useTranslation()
  const toast = useToastStore()
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<'questions' | 'budget' | 'games'>('questions')
  const operationService: ConsoleService = activeTab === 'budget' ? 'budget' : 'questions'
  const { consoleState, operation, isPolling, startPolling, stopPolling } = useOperationPolling(operationService)

  const [runningOpIds, setRunningOpIds] = useState<Record<ConsoleService, string | null>>({
    questions: null,
    budget: null,
  })
  const [selectedSyncSource, setSelectedSyncSource] = useState('')
  const [syncCountryCode, setSyncCountryCode] = useState('')
  const [hintCountryCode, setHintCountryCode] = useState('')
  const [selectedHintSource, setSelectedHintSource] = useState('')
  const [localEntries, setLocalEntries] = useState<Record<ConsoleService, ConsoleEntry[]>>({
    questions: [],
    budget: [],
  })
  const outputRef = useRef<HTMLDivElement>(null)
  const opsColumnRef = useRef<HTMLDivElement>(null)
  const [consoleHeight, setConsoleHeight] = useState<number | null>(null)
  const [sourcesModalOpen, setSourcesModalOpen] = useState(false)
  const [pendingConfirm, setPendingConfirm] = useState<{ message: string; action: () => void } | null>(null)

  const runningOpId = isPolling ? runningOpIds[operationService] : null
  const isRunning = isPolling || operation?.status === 'RUNNING'

  const remoteLogs = consoleState?.logs ?? []
  const remoteEntries: ConsoleEntry[] = remoteLogs.map(entry => ({
    time: new Date(entry.timestamp).toLocaleTimeString(),
    text: entry.text,
    type: (entry.type || 'info') as ConsoleEntry['type'],
    timestamp: entry.timestamp,
  }))

  const allEntries = [...remoteEntries, ...localEntries[operationService]]
    .sort((a, b) => a.timestamp - b.timestamp)
    .slice(-MAX_CONSOLE_ENTRIES)

  const addLog = useCallback((text: string, type: ConsoleEntry['type'] = 'info') => {
    setLocalEntries(previous => ({
      ...previous,
      [operationService]: [...previous[operationService], {
        time: new Date().toLocaleTimeString(),
        text,
        type,
        timestamp: Date.now(),
      }],
    }))
  }, [operationService])

  const { data: sourcesOverview } = useQuery({
    queryKey: ['admin-data-sources'],
    queryFn: adminApi.getDataSources,
    staleTime: 60_000,
  })

  const syncSources = sourcesOverview?.sources?.filter(s => s.syncAvailable) ?? []
  const hintSources = sourcesOverview?.sources?.filter(s => s.hintGenerationAvailable) ?? []
  const syncSource = syncSources.some(source => source.name === selectedSyncSource)
    ? selectedSyncSource
    : syncSources[0]?.name ?? ''
  const hintSource = hintSources.some(source => source.name === selectedHintSource)
    ? selectedHintSource
    : hintSources[0]?.name ?? ''

  const { data: health } = useQuery({
    queryKey: ['admin-health'],
    queryFn: adminApi.checkHealth,
    refetchInterval: 30000,
  })

  const { data: budgetStatus } = useQuery({
    queryKey: ['admin-budget-service-status'],
    queryFn: adminApi.getBudgetServiceStatus,
    refetchInterval: 30000,
    retry: false,
  })

  const questionsUnavailable = health?.status === 'DOWN' || sourcesOverview?.serviceStatus === 'DOWN'
  const budgetUnavailable = budgetStatus?.serviceStatus !== 'UP'

  useEffect(() => {
    const el = opsColumnRef.current
    if (!el) return
    const ro = new ResizeObserver(([entry]) => {
      setConsoleHeight(entry.contentRect.height)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  useEffect(() => {
    if (outputRef.current) {
      outputRef.current.scrollTop = outputRef.current.scrollHeight
    }
  }, [allEntries.length])

  const handleClear = async () => {
    setLocalEntries(previous => ({ ...previous, [operationService]: [] }))
    queryClient.setQueryData(['admin-console-state', operationService], (prev: typeof consoleState) =>
      prev ? { ...prev, logs: [] } : prev
    )
    await adminApi.clearConsoleLogs(operationService).catch(() => {})
  }

  const clearStaleOperationStatus = async () => {
    const service = operationService
    await adminApi.clearOperationStatus(service)
    setRunningOpIds(previous => ({ ...previous, [service]: null }))
    queryClient.setQueryData(['admin-console-state', service], (previous: typeof consoleState) =>
      previous ? { ...previous, operation: null } : previous
    )
    stopPolling()
    addLog('Cleared stale operation status. This did not cancel any live backend work.', 'system')
  }

  const runOperation = async (
    id: string,
    name: string,
    fn: () => Promise<unknown>
  ) => {
    const service = operationService
    setRunningOpIds(previous => ({ ...previous, [service]: id }))
    startPolling()
    addLog(`Starting ${name}...`, 'system')
    try {
      await fn()
      toast.addToast({ type: 'success', message: t.admin.operationStarted })
    } catch (err) {
      addLog(`Error: ${err instanceof Error ? err.message : 'Unknown error'}`, 'error')
      setRunningOpIds(previous => ({ ...previous, [service]: null }))
      stopPolling()
    }
  }

  const operations = [
    {
      id: 'full-sync', name: t.admin.fullSync,
      desc: 'Synchronize all country data from external sources',
      icon: Database, color: 'text-blue-400', bg: 'bg-blue-500/10',
      fn: () => setPendingConfirm({
        message: t.admin.confirmFullSync,
        action: () => runOperation('full-sync', 'Full Sync', adminApi.syncFull),
      }),
    },
    {
      id: 'sync-source', name: t.admin.syncBySource,
      desc: 'Sync from specific data source',
      icon: RefreshCw, color: 'text-cyan-400', bg: 'bg-cyan-500/10',
      fn: () => setPendingConfirm({
        message: t.admin.confirmSyncSource.replace('{source}', syncSource),
        action: () => runOperation('sync-source', `Sync ${syncSource}`, () => adminApi.syncSource(syncSource)),
      }),
      extra: (
        <SourceSelect
          size="sm"
          value={syncSource}
          onChange={setSelectedSyncSource}
          className="mt-2 w-[240px]"
          sources={syncSources}
          placeholder="Select source..."
        />
      ),
    },
    {
      id: 'sync-country', name: t.admin.syncByCountry,
      desc: 'Sync data for a specific country code',
      icon: RefreshCw, color: 'text-teal-400', bg: 'bg-teal-500/10',
      fn: () => {
        if (!syncCountryCode) return
        setPendingConfirm({
          message: t.admin.confirmSyncCountry.replace('{code}', syncCountryCode),
          action: () => runOperation('sync-country', `Sync ${syncCountryCode}`, () => adminApi.syncCountry(syncCountryCode)),
        })
      },
      extra: (
        <Input
          placeholder="e.g. US"
          value={syncCountryCode}
          onChange={e => setSyncCountryCode(e.target.value.toUpperCase())}
          className="max-w-[100px] h-8 text-xs mt-2"
        />
      ),
    },
    {
      id: 'enrich', name: t.admin.enrichStats,
      desc: 'Calculate ranks, percentiles, and derived statistics',
      icon: Sparkles, color: 'text-violet-400', bg: 'bg-violet-500/10',
      fn: () => runOperation('enrich', 'Enrich Statistics', adminApi.enrichStatistics),
    },
    {
      id: 'gen-hints', name: t.admin.generateAllHints,
      desc: 'Generate hints for all countries (batch)',
      icon: Lightbulb, color: 'text-amber-400', bg: 'bg-amber-500/10',
      fn: () => runOperation('gen-hints', 'Generate All Hints', adminApi.generateAllHints),
    },
    {
      id: 'gen-combo-hints', name: 'Generate Combo Hints',
      desc: 'Generate combinatorics hints for all countries from combo indicator configs',
      icon: Sparkles, color: 'text-fuchsia-400', bg: 'bg-fuchsia-500/10',
      fn: () => runOperation('gen-combo-hints', 'Generate Combo Hints', adminApi.generateAllComboHints),
    },
    {
      id: 'image-integrity', name: 'Audit & repair images',
      desc: 'Restore missing hint images and verify current Wikimedia attribution and licenses',
      icon: ImageIcon, color: 'text-rose-400', bg: 'bg-rose-500/10',
      fn: () => setPendingConfirm({
        message: 'Scan all current question images, restore missing objects, and validate source attribution now?',
        action: () => runOperation('image-integrity', 'Image integrity audit', adminApi.auditImageIntegrity),
      }),
    },
    {
      id: 'gen-hints-country', name: 'Generate Hints (Country)',
      desc: 'Generate hints for a specific country code',
      icon: Lightbulb, color: 'text-yellow-400', bg: 'bg-yellow-500/10',
      fn: () => hintCountryCode && runOperation('gen-hints-country', `Generate Hints ${hintCountryCode}`, () => adminApi.generateHintsForCountry(hintCountryCode)),
      extra: (
        <Input
          placeholder="e.g. US"
          value={hintCountryCode}
          onChange={e => setHintCountryCode(e.target.value.toUpperCase())}
          className="max-w-[100px] h-8 text-xs mt-2"
        />
      ),
    },
    {
      id: 'gen-hints-source', name: 'Generate Hints (Source)',
      desc: 'Regenerate hints for a specific data source across all countries',
      icon: Lightbulb, color: 'text-lime-400', bg: 'bg-lime-500/10',
      fn: () => hintSource && runOperation('gen-hints-source', `Generate Hints ${hintSource}`, () => adminApi.generateHintsForSource(hintSource)),
      extra: (
        <SourceSelect
          size="sm"
          value={hintSource}
          onChange={setSelectedHintSource}
          className="mt-2 w-[240px]"
          sources={hintSources}
          placeholder="Select source..."
        />
      ),
    },
    {
      id: 'gen-today', name: t.admin.generateToday,
      desc: "Generate today's daily challenge",
      icon: Zap, color: 'text-emerald-400', bg: 'bg-emerald-500/10',
      fn: () => runOperation('gen-today', 'Generate Today', adminApi.generateTodayChallenge),
    },
    {
      id: 'gen-upcoming', name: t.admin.generateUpcoming,
      desc: 'Generate challenges for the next 7 days',
      icon: Calendar, color: 'text-orange-400', bg: 'bg-orange-500/10',
      fn: () => runOperation('gen-upcoming', 'Generate Upcoming', () => adminApi.generateUpcoming(7)),
    },
    {
      id: 'game-rivers', name: 'Generate river questions',
      desc: 'Import source-backed river maps and three clues per question. Existing questions are updated only when facts or maps change.',
      icon: Globe2, color: 'text-cyan-300', bg: 'bg-cyan-400/10',
      fn: () => setPendingConfirm({
        message: 'Import the bundled river catalog and upload its maps to image storage?',
        action: () => runOperation('game-rivers', 'Generate River Questions', adminApi.generateRiverQuestions),
      }),
    },
    {
      id: 'game-river-photos', name: 'Sync river photos',
      desc: 'Find a licensed Wikimedia Commons photo and a Wikipedia link for every active river. Photos join the image integrity audit.',
      icon: ImageIcon, color: 'text-cyan-300', bg: 'bg-cyan-400/10',
      fn: () => setPendingConfirm({
        message: 'Check Wikidata and Wikimedia Commons for river photos and store the chosen ones in image storage?',
        action: () => runOperation('game-river-photos', 'Sync River Photos', adminApi.syncRiverPhotos),
      }),
    },
    {
      id: 'game-silhouettes', name: 'Generate silhouette questions',
      desc: 'Build the 197 country silhouette questions and their clues from the bundled catalog, keeping only facts our statistics and Wikidata agree on. Run it after syncing country photos so the landmark clue is included.',
      icon: Globe2, color: 'text-violet-300', bg: 'bg-violet-400/10',
      fn: () => setPendingConfirm({
        message: 'Regenerate the country silhouette questions and clues from the bundled catalog and current statistics?',
        action: () => runOperation('game-silhouettes', 'Generate Silhouette Questions', adminApi.generateSilhouetteQuestions),
      }),
    },
    {
      id: 'game-flags', name: 'Generate flag questions',
      desc: 'Build the 197 Guess the flag questions from the bundled catalog with region, first-letter and capital clues; the capital is kept only when our statistics and Wikidata agree on it.',
      icon: Flag, color: 'text-rose-300', bg: 'bg-rose-400/10',
      fn: () => setPendingConfirm({
        message: 'Regenerate the flag questions and clues from the bundled catalog and current statistics?',
        action: () => runOperation('game-flags', 'Generate Flag Questions', adminApi.generateFlagQuestions),
      }),
    },
    {
      id: 'game-quiz', name: 'Generate world quiz questions',
      desc: 'Build the Around the World Quiz: every country of the pool becomes a stop with up to ten multiple-choice questions (capital, flag, currency, neighbours, outline and more). Answers are kept only when two sources agree, and wrong options come from the same part of the world.',
      icon: Globe2, color: 'text-orange-200', bg: 'bg-orange-300/10',
      fn: () => setPendingConfirm({
        message: 'Regenerate the world quiz stops and questions from the bundled catalog and current statistics?',
        action: () => runOperation('game-quiz', 'Generate World Quiz Questions', adminApi.generateQuizQuestions),
      }),
    },
    {
      id: 'game-country-photos', name: 'Sync country photos',
      desc: 'Rank the landmark photos of every country by how well known the depicted landmark is and store the best licensed one. Photos join the image integrity audit.',
      icon: ImageIcon, color: 'text-cyan-300', bg: 'bg-cyan-400/10',
      fn: () => setPendingConfirm({
        message: 'Check Wikimedia Commons and Wikidata for country landmark photos and store the chosen ones in image storage?',
        action: () => runOperation('game-country-photos', 'Sync Country Photos', adminApi.syncCountryPhotos),
      }),
    },
    {
      id: 'budget-full-sync', name: 'Full content sync',
      desc: 'Fetch priced Wikidata projects and store licensed images for editorial review',
      icon: Database, color: 'text-emerald-400', bg: 'bg-emerald-500/10',
      fn: () => setPendingConfirm({
        message: 'Run project discovery and licensed-image enrichment? This can take a long time.',
        action: () => runOperation('budget-full-sync', 'Full content sync', adminApi.syncBudgetFull),
      }),
    },
    {
      id: 'budget-wikidata', name: 'Wikidata discovery',
      desc: 'Discovery only: fetch every eligible candidate without starting enrichment',
      icon: Globe2, color: 'text-sky-400', bg: 'bg-sky-500/10',
      fn: () => setPendingConfirm({
        message: 'Fetch project candidates from Wikidata now?',
        action: () => runOperation('budget-wikidata', 'Wikidata discovery', adminApi.syncBudgetWikidata),
      }),
    },
    {
      id: 'budget-images', name: 'Retry images',
      desc: 'Retry failed licensed images for priced Wikidata candidates',
      icon: ImageIcon, color: 'text-fuchsia-400', bg: 'bg-fuchsia-500/10',
      fn: () => setPendingConfirm({
        message: 'Retry failed licensed images for project candidates?',
        action: () => runOperation('budget-images', 'Image enrichment', adminApi.enrichBudgetImages),
      }),
    },
    {
      id: 'budget-questions', name: 'Generate questions',
      desc: 'Rebuild all playable question variants from published project snapshots',
      icon: Lightbulb, color: 'text-amber-400', bg: 'bg-amber-500/10',
      fn: () => runOperation(
        'budget-questions',
        'Question generation',
        adminApi.generateBudgetQuestions,
      ),
    },
    {
      id: 'budget-challenges', name: 'Generate challenges now',
      desc: 'Create missing five-question daily challenges from today through seven days ahead',
      icon: Calendar, color: 'text-orange-400', bg: 'bg-orange-500/10',
      fn: () => runOperation(
        'budget-challenges',
        'Daily challenge generation',
        adminApi.generateBudgetChallenges,
      ),
    },
  ]

  const visibleOperations = operations.filter(op => activeTab === 'budget'
    ? op.id.startsWith('budget-')
    : activeTab === 'games' ? op.id.startsWith('game-')
    : !op.id.startsWith('budget-') && !op.id.startsWith('game-'))

  return (
    <div className="p-5 sm:p-6 lg:p-8 space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.operations}</h1>
          <p className="text-white/40 text-sm mt-1">Run administrative operations and monitor progress</p>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-3">
          <div className="flex items-center gap-2">
            <Heart size={14} className={health?.status === 'UP' ? 'text-emerald-400' : 'text-red-400'} />
            <span className="text-xs text-white/35">Questions</span>
            <Badge variant={health?.status === 'UP' ? 'success' : 'error'}>
              {health?.status || 'Unknown'}
            </Badge>
          </div>
          <div className="flex items-center gap-2">
            <Landmark size={14} className={budgetStatus?.serviceStatus === 'UP' ? 'text-emerald-400' : 'text-red-400'} />
            <span className="text-xs text-white/35">Budget</span>
            <Badge variant={budgetStatus?.serviceStatus === 'UP' ? 'success' : 'error'}>
              {budgetStatus?.serviceStatus || 'Unknown'}
            </Badge>
          </div>
        </div>
      </div>

      {questionsUnavailable && (
        <div className="rounded-xl border border-red-500/20 bg-red-500/8 px-4 py-3">
          <div className="flex items-start gap-3">
            <Server size={16} className="mt-0.5 shrink-0 text-red-300" />
            <div className="min-w-0">
              <p className="text-sm font-medium text-red-200">
                <code className="rounded bg-black/20 px-1.5 py-0.5 text-[12px]">clueterra-questions</code> is unavailable
              </p>
              <p className="mt-1 text-xs text-red-100/70">
                Operations are temporarily disabled until the questions service is back online.
              </p>
              {sourcesOverview?.serviceDetails && (
                <p className="mt-2 text-[11px] text-red-100/55 font-mono break-all">
                  {sourcesOverview.serviceDetails}
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      {budgetUnavailable && (
        <div className="rounded-xl border border-red-500/20 bg-red-500/8 px-4 py-3">
          <div className="flex items-start gap-3">
            <Landmark size={16} className="mt-0.5 shrink-0 text-red-300" />
            <div className="min-w-0">
              <p className="text-sm font-medium text-red-200">
                <code className="rounded bg-black/20 px-1.5 py-0.5 text-[12px]">clueterra-budget</code> is unavailable
              </p>
              <p className="mt-1 text-xs text-red-100/70">
                Only BudgetGuessr operations are disabled. Questions operations remain available.
              </p>
              {budgetStatus?.error && (
                <p className="mt-2 text-[11px] text-red-100/55 font-mono break-all">
                  {budgetStatus.error}
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <div ref={opsColumnRef} className="space-y-3">
          <div className="flex rounded-xl border border-white/[.06] bg-white/[.02] p-1">
            <Button
              variant={activeTab === 'questions' ? 'secondary' : 'ghost'}
              size="sm"
              className="flex-1"
              onClick={() => setActiveTab('questions')}
            >
              <Lightbulb size={14} /> Questions
            </Button>
            <Button
              variant={activeTab === 'budget' ? 'secondary' : 'ghost'}
              size="sm"
              className="flex-1"
              onClick={() => setActiveTab('budget')}
            >
              <Landmark size={14} /> BudgetGuessr
            </Button>
            <Button
              variant={activeTab === 'games' ? 'secondary' : 'ghost'}
              size="sm"
              className="flex-1"
              onClick={() => setActiveTab('games')}
            >
              <Globe2 size={14} /> Games
            </Button>
          </div>
          {visibleOperations.map(op => {
            const isActive = runningOpId === op.id
            const operationUnavailable = op.id.startsWith('budget-')
              ? budgetUnavailable
              : questionsUnavailable
            return (
              <div
                key={op.id}
                className={cn(
                  'rounded-xl bg-white/[.03] border border-white/[.06] p-4 transition-all',
                  isActive && 'ring-1 ring-white/15'
                )}
              >
                <div className="flex items-start gap-3">
                  <div className={cn('p-2.5 rounded-lg shrink-0', op.bg)}>
                    {isActive
                      ? <Loader2 size={18} className="text-blue-400 animate-spin" />
                      : <op.icon size={18} className={op.color} />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <h3 className="text-sm font-semibold text-white">{op.name}</h3>
                    <p className="text-xs text-white/35 mt-0.5">{op.desc}</p>
                    {op.extra}
                  </div>
                  <Button
                    variant="outline" size="sm"
                    onClick={op.fn}
                    disabled={isRunning || operationUnavailable}
                  >
                    <Play size={13} /> {t.admin.startOperation}
                  </Button>
                </div>
              </div>
            )
          })}
        </div>

        <div
          className="rounded-xl bg-white/[.03] border border-white/[.06] flex flex-col"
          style={{ height: consoleHeight ?? 400 }}
        >
          <div className="px-4 py-3 border-b border-white/[.06] flex items-center justify-between shrink-0">
            <div className="flex items-center gap-2 text-sm font-medium text-white/70">
              <Terminal size={15} />
              Output Console
            </div>
            <div className="flex items-center gap-2">
              {isRunning && (
                <>
                  <span className="flex items-center gap-2 text-xs text-amber-400">
                    <Loader2 size={12} className="animate-spin" />
                    Running...
                  </span>
                  {operation?.status === 'RUNNING' && (
                    <button
                      onClick={() => setPendingConfirm({
                        message: 'Clear this stale operation status and unlock the UI? This does not stop live backend work; use it only after a service restart or confirmed crash.',
                        action: () => void clearStaleOperationStatus(),
                      })}
                      className="flex items-center gap-1 text-[11px] text-red-300/45 transition-colors hover:text-red-300/75"
                      title="Clear stale operation status"
                    >
                      <CircleX size={13} />
                      Clear stale status
                    </button>
                  )}
                </>
              )}
              {allEntries.length > 0 && !isRunning && (
                <button
                  onClick={handleClear}
                  className="text-white/25 hover:text-white/50 transition-colors"
                  title="Clear console"
                >
                  <Trash2 size={14} />
                </button>
              )}
            </div>
          </div>
          <div ref={outputRef} className="flex-1 overflow-auto p-4 font-mono text-xs leading-relaxed">
            {allEntries.length === 0 ? (
              <p className="text-white/20">Output will appear here...</p>
            ) : (
              allEntries.map((entry, i) => (
                <div
                  key={`${entry.timestamp}-${i}`}
                  className={cn(
                    'py-0.5',
                    entry.type === 'success' && 'text-emerald-400',
                    entry.type === 'error' && 'text-red-400',
                    entry.type === 'system' && 'text-white/30',
                    entry.type === 'info' && 'text-white/55'
                  )}
                >
                  <span className="text-white/15 mr-2">[{entry.time}]</span>
                  {entry.text}
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {sourcesOverview && (
        <>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4 text-xs text-white/30">
              <span>
                <span className="text-emerald-400 font-medium">{sourcesOverview.healthySources}</span>
                /{sourcesOverview.enabledSources} healthy
              </span>
              <span>
                <span className="text-white/50 font-medium">{sourcesOverview.enabledSources}</span>
                /{sourcesOverview.totalSources} enabled
              </span>
            </div>
            <Button
              variant="outline" size="sm"
              onClick={() => setSourcesModalOpen(true)}
            >
              <Server size={14} /> Data Sources
            </Button>
          </div>

          <Modal
            isOpen={sourcesModalOpen}
            onClose={() => setSourcesModalOpen(false)}
            title="Data Sources"
            className="max-w-3xl"
          >
            {sourcesOverview.sources.length === 0 ? (
              <div className="rounded-xl border border-white/[.06] bg-white/[.02] px-4 py-8 text-center">
                <p className="text-sm text-white/65">No data sources available.</p>
                {sourcesOverview.serviceError && (
                  <p className="mt-2 text-xs text-white/35">{sourcesOverview.serviceError}</p>
                )}
              </div>
            ) : (
              <div className="rounded-xl border border-white/[.06] overflow-hidden -mx-2 max-h-[60vh] overflow-y-auto">
                <table className="w-full">
                  <tbody>
                    {sourcesOverview.sources.map(src => (
                      <tr
                        key={src.name}
                        className={cn(
                          'border-b border-white/[.04] last:border-0 transition-colors',
                          src.enabled ? 'hover:bg-white/[.02]' : 'opacity-40'
                        )}
                      >
                        <td className="px-4 py-2.5 text-sm text-white/80 font-medium w-48">
                          {src.displayName}
                        </td>
                        <td className="px-4 py-2.5 w-28">
                          <Badge
                            dot
                            variant={
                              src.status === 'AVAILABLE' ? 'success'
                              : src.status === 'UNHEALTHY' ? 'error'
                              : 'neutral'
                            }
                          >
                            {src.status}
                          </Badge>
                        </td>
                        <td className="px-4 py-2.5 text-xs text-white/30 font-mono truncate max-w-[220px]">
                          {src.baseUrl ?? '—'}
                        </td>
                        <td className="px-4 py-2.5 text-xs text-white/25 text-right">
                          {src.message}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Modal>
        </>
      )}

      <ConfirmDialog
        isOpen={!!pendingConfirm}
        onClose={() => setPendingConfirm(null)}
        onConfirm={() => {
          pendingConfirm?.action()
          setPendingConfirm(null)
        }}
        title={t.admin.confirmSyncTitle}
        message={pendingConfirm?.message}
        confirmLabel={t.admin.confirmRun}
        variant="default"
      />
    </div>
  )
}
