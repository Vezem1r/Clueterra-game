import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { TrendingUp, TrendingDown, Globe2, Clock, Target, Users, BarChart3, Activity, RefreshCw, Check, X } from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, AreaChart, Area,
} from 'recharts'
import { DatePicker } from '@/components/ui/DatePicker'
import { Skeleton } from '@/components/ui/Skeleton'
import { adminApi } from '@/api/admin'
import { useTranslation } from '@/i18n'
import { format, subDays, subMonths, subYears } from 'date-fns'
import { AnimatePresence, motion } from 'framer-motion'

const COLORS = ['#3b82f6', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6']
const CHART_INITIAL_DIMENSION = { width: 320, height: 224 }

const tooltipStyle = {
  backgroundColor: '#0c0c14',
  border: '1px solid rgba(255,255,255,.08)',
  borderRadius: '10px',
  fontSize: '12px',
  color: '#fff',
}

const tooltipItemStyle = { color: '#fff' }

export function AnalyticsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [dateRange, setDateRange] = useState({
    start: format(subDays(new Date(), 30), 'yyyy-MM-dd'),
    end: format(new Date(), 'yyyy-MM-dd'),
  })
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(() => setToast(null), 4000)
    return () => clearTimeout(timer)
  }, [toast])

  const aggregateMutation = useMutation({
    mutationFn: () => adminApi.aggregateAnalytics(dateRange.start, dateRange.end),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['admin-analytics-v2-summary'] })
      queryClient.invalidateQueries({ queryKey: ['admin-analytics-v2-level'] })
      queryClient.invalidateQueries({ queryKey: ['admin-analytics-v2-top'] })
      queryClient.invalidateQueries({ queryKey: ['admin-analytics-v2-hardest'] })
      queryClient.invalidateQueries({ queryKey: ['admin-analytics-v2-daily'] })
      setToast({ type: 'success', message: `Aggregated ${data.aggregatedDays} days` })
    },
    onError: () => setToast({ type: 'error', message: 'Aggregation failed' }),
  })

  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['admin-analytics-v2-summary', dateRange],
    queryFn: () => adminApi.getAnalyticsSummary(dateRange.start, dateRange.end),
  })

  const { data: byLevel } = useQuery({
    queryKey: ['admin-analytics-v2-level', dateRange],
    queryFn: () => adminApi.getAnalyticsByLevel(dateRange.start, dateRange.end),
  })

  const { data: topCountries } = useQuery({
    queryKey: ['admin-analytics-v2-top', dateRange],
    queryFn: () => adminApi.getAnalyticsTopCountries(dateRange.start, dateRange.end, 10),
  })

  const { data: hardestCountries } = useQuery({
    queryKey: ['admin-analytics-v2-hardest', dateRange],
    queryFn: () => adminApi.getAnalyticsHardestCountries(dateRange.start, dateRange.end, 10),
  })

  const { data: dailySessions } = useQuery({
    queryKey: ['admin-analytics-v2-daily', dateRange],
    queryFn: () => adminApi.getAnalyticsDailySessions(dateRange.start, dateRange.end),
  })

  const completionRate = summary && summary.totalSessions > 0
    ? Math.round((summary.completedSessions / summary.totalSessions) * 100)
    : 0

  const guessAccuracy = summary && summary.totalGuesses > 0
    ? Math.round((summary.correctGuesses / summary.totalGuesses) * 100)
    : 0

  const avgTimeMin = summary ? Math.round(summary.avgTimePerSessionMs / 60000) : 0

  const levelData = byLevel?.map(l => ({ name: l.level, value: l.total, completed: l.completed })) ?? []

  const topCountriesChart = topCountries?.map(c => ({
    name: c.countryName,
    featured: c.timesFeatured,
    correct: c.timesGuessedCorrect,
  })) ?? []

  const dailyChart = dailySessions?.map(d => ({
    date: format(new Date(d.date), 'MMM dd'),
    sessions: d.totalSessions,
    completed: d.completedSessions,
    users: d.uniqueUsers,
  })) ?? []

  return (
    <div className="p-5 sm:p-6 lg:p-8 space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.analytics}</h1>
          <p className="text-white/40 text-sm mt-1">Game analytics from aggregated session data</p>
        </div>
        <button
          onClick={() => aggregateMutation.mutate()}
          disabled={aggregateMutation.isPending}
          className="flex items-center gap-2 px-4 py-2 text-sm rounded-lg bg-white/[.06] border border-white/[.08] text-white/70 hover:text-white hover:bg-white/[.1] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <RefreshCw size={14} className={aggregateMutation.isPending ? 'animate-spin' : ''} />
          {aggregateMutation.isPending ? 'Aggregating...' : 'Aggregate'}
        </button>
      </div>

      <AnimatePresence>
        {toast && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl border text-sm ${
              toast.type === 'success'
                ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
                : 'bg-red-500/10 border-red-500/20 text-red-400'
            }`}
          >
            {toast.type === 'success' ? <Check size={14} /> : <X size={14} />}
            {toast.message}
          </motion.div>
        )}
      </AnimatePresence>

      <div className="flex items-center gap-3 flex-wrap rounded-xl bg-white/[.03] border border-white/[.06] p-3 sm:p-4">
        <span className="text-white/45 text-sm">Date Range:</span>
        <DatePicker
          value={dateRange.start}
          onChange={v => setDateRange(prev => ({ ...prev, start: v }))}
          placeholder="Start date"
          size="sm"
          clearable
        />
        <span className="text-white/25">to</span>
        <DatePicker
          value={dateRange.end}
          onChange={v => setDateRange(prev => ({ ...prev, end: v }))}
          placeholder="End date"
          size="sm"
          clearable
        />
        <div className="flex items-center gap-1.5 ml-auto">
          {[
            { label: 'Today', start: new Date(), end: new Date() },
            { label: '7d', start: subDays(new Date(), 7), end: new Date() },
            { label: '1m', start: subMonths(new Date(), 1), end: new Date() },
            { label: '3m', start: subMonths(new Date(), 3), end: new Date() },
            { label: '1y', start: subYears(new Date(), 1), end: new Date() },
          ].map(p => (
            <button
              key={p.label}
              onClick={() => setDateRange({ start: format(p.start, 'yyyy-MM-dd'), end: format(p.end, 'yyyy-MM-dd') })}
              className="px-2.5 py-1 text-xs rounded-md bg-white/[.04] text-white/50 hover:text-white hover:bg-white/[.08] transition-colors"
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {summaryLoading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="rounded-xl bg-white/[.03] border border-white/[.06] p-4">
              <Skeleton className="w-20 h-7 mb-1" />
              <Skeleton className="w-16 h-3" />
            </div>
          ))}
        </div>
      ) : summary && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {[
            { label: t.admin.totalSessions, value: summary.totalSessions, icon: BarChart3, color: 'text-blue-400' },
            { label: 'Completed', value: summary.completedSessions, icon: Target, color: 'text-emerald-400' },
            { label: t.admin.uniqueUsers, value: summary.uniqueUsers, icon: Users, color: 'text-violet-400' },
            { label: t.admin.completionRate, value: `${completionRate}%`, icon: Activity, color: 'text-amber-400' },
            { label: 'Guess Accuracy', value: `${guessAccuracy}%`, icon: Target, color: 'text-cyan-400' },
            { label: 'Avg Time', value: `${avgTimeMin}m`, icon: Clock, color: 'text-rose-400' },
          ].map((s, i) => (
            <div key={i} className="rounded-xl bg-white/[.03] border border-white/[.06] px-4 py-3">
              <div className="flex items-center gap-2 mb-1">
                <s.icon size={14} className={s.color} />
                <p className="text-xl font-bold text-white">{s.value}</p>
              </div>
              <p className="text-[11px] text-white/35">{s.label}</p>
            </div>
          ))}
        </div>
      )}

      {dailyChart.length > 0 && (
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <h3 className="font-semibold text-white text-sm mb-4">Daily Sessions</h3>
          <div className="h-56 min-w-0">
            <ResponsiveContainer
              width="100%"
              height="100%"
              minWidth={0}
              initialDimension={CHART_INITIAL_DIMENSION}
            >
              <AreaChart data={dailyChart}>
                <defs>
                  <linearGradient id="colorSessions" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="colorCompleted" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#22c55e" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#22c55e" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="date" stroke="transparent" tick={{ fill: '#525252', fontSize: 10 }} />
                <YAxis stroke="transparent" tick={{ fill: '#525252', fontSize: 11 }} />
                <Tooltip contentStyle={tooltipStyle} itemStyle={tooltipItemStyle} labelStyle={tooltipItemStyle} />
                <Area type="monotone" dataKey="sessions" stroke="#3b82f6" fill="url(#colorSessions)" strokeWidth={2} />
                <Area type="monotone" dataKey="completed" stroke="#22c55e" fill="url(#colorCompleted)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <div className="flex justify-center gap-5 mt-3">
            <div className="flex items-center gap-1.5">
              <div className="w-2.5 h-2.5 rounded-full bg-blue-500" />
              <span className="text-xs text-white/50">Total Sessions</span>
            </div>
            <div className="flex items-center gap-1.5">
              <div className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
              <span className="text-xs text-white/50">Completed</span>
            </div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <h3 className="font-semibold text-white text-sm mb-4">Sessions by Difficulty</h3>
          <div className="h-56 min-w-0">
            <ResponsiveContainer
              width="100%"
              height="100%"
              minWidth={0}
              initialDimension={CHART_INITIAL_DIMENSION}
            >
              <PieChart>
                <Pie data={levelData} cx="50%" cy="50%" innerRadius={50} outerRadius={75} paddingAngle={4} dataKey="value">
                  {levelData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip contentStyle={tooltipStyle} itemStyle={tooltipItemStyle} labelStyle={tooltipItemStyle} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="flex justify-center gap-4 mt-3 flex-wrap">
            {levelData.map((item, i) => (
              <div key={item.name} className="flex items-center gap-1.5">
                <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
                <span className="text-xs text-white/50">{item.name} ({item.value})</span>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5 lg:col-span-2">
          <h3 className="font-semibold text-white text-sm mb-4">Top Featured Countries</h3>
          <div className="h-56 min-w-0">
            <ResponsiveContainer
              width="100%"
              height="100%"
              minWidth={0}
              initialDimension={CHART_INITIAL_DIMENSION}
            >
              <BarChart data={topCountriesChart} layout="horizontal">
                <XAxis dataKey="name" stroke="transparent" tick={{ fill: '#525252', fontSize: 10 }} angle={-30} textAnchor="end" height={50} />
                <YAxis stroke="transparent" tick={{ fill: '#525252', fontSize: 11 }} />
                <Tooltip contentStyle={tooltipStyle} itemStyle={tooltipItemStyle} labelStyle={tooltipItemStyle} cursor={{ fill: 'rgba(255,255,255,0.03)' }} />
                <Bar dataKey="featured" fill="#3b82f6" radius={[4, 4, 0, 0]} name="Featured" />
                <Bar dataKey="correct" fill="#22c55e" radius={[4, 4, 0, 0]} name="Guessed" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <h3 className="font-semibold text-white text-sm flex items-center gap-2 mb-4">
            <TrendingUp className="text-red-400" size={16} />
            Hardest Countries
          </h3>
          <div className="space-y-3">
            {hardestCountries?.slice(0, 5).map((c, i) => {
              const successRate = c.timesFeatured > 0
                ? Math.round((c.timesGuessedCorrect / c.timesFeatured) * 100)
                : 0
              return (
                <div key={c.countryId} className="flex items-center gap-3">
                  <span className="w-5 text-white/30 text-sm font-mono">{i + 1}</span>
                  <Globe2 size={14} className="text-white/25" />
                  <span className="text-sm text-white/70 flex-1">{c.countryName}</span>
                  <span className="text-[11px] text-white/30 mr-2">{successRate}% success</span>
                  <span className="text-xs font-medium text-red-400/80 bg-red-400/10 px-2 py-0.5 rounded-md">
                    {c.avgAttempts.toFixed(1)} avg
                  </span>
                </div>
              )
            })}
            {!hardestCountries?.length && (
              <p className="text-sm text-white/25">No data yet</p>
            )}
          </div>
        </div>

        <div className="rounded-xl bg-white/[.03] border border-white/[.06] p-5">
          <h3 className="font-semibold text-white text-sm flex items-center gap-2 mb-4">
            <TrendingDown className="text-emerald-400" size={16} />
            Most Featured
          </h3>
          <div className="space-y-3">
            {topCountries?.slice(0, 5).map((c, i) => (
              <div key={c.countryId} className="flex items-center gap-3">
                <span className="w-5 text-white/30 text-sm font-mono">{i + 1}</span>
                <Globe2 size={14} className="text-white/25" />
                <span className="text-sm text-white/70 flex-1">{c.countryName}</span>
                <span className="text-xs font-medium text-emerald-400/80 bg-emerald-400/10 px-2 py-0.5 rounded-md">
                  {c.timesFeatured} times
                </span>
              </div>
            ))}
            {!topCountries?.length && (
              <p className="text-sm text-white/25">No data yet</p>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
