import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  Users, Gamepad2, Target, TrendingUp, Activity,
  Globe2, Lightbulb, Calendar, ArrowRight, UserCog,
} from 'lucide-react'
import {
  AreaChart, Area, LineChart, Line,
  XAxis, YAxis, Tooltip, ResponsiveContainer,
} from 'recharts'
import { adminApi } from '@/api/admin'
import { useTranslation } from '@/i18n'
import { Skeleton } from '@/components/ui/Skeleton'
import { format } from 'date-fns'
import { NavLink } from 'react-router-dom'

const CHART_INITIAL_DIMENSION = { width: 320, height: 224 }

export function DashboardPage() {
  const { t } = useTranslation()

  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['admin-dashboard-summary'],
    queryFn: () => adminApi.getDashboardSummary(),
  })

  const { data: geographyChartPoints } = useQuery({
    queryKey: ['admin-dashboard-chart'],
    queryFn: () => adminApi.getDashboardSessionsChart(30),
  })

  const { data: budgetChartPoints } = useQuery({
    queryKey: ['admin-dashboard-budget-chart'],
    queryFn: () => adminApi.getDashboardBudgetSessionsChart(30),
  })

  const { data: countries } = useQuery({
    queryKey: ['admin-countries-count'],
    queryFn: () => adminApi.getCountries({ page: 0, size: 1 }),
  })

  const { data: hints } = useQuery({
    queryKey: ['admin-hints-count'],
    queryFn: () => adminApi.getHints({ page: 0, size: 1 }),
  })

  const { data: challenges } = useQuery({
    queryKey: ['admin-challenges-count'],
    queryFn: () => adminApi.getChallenges({ page: 0, size: 1 }),
  })

  const toChartData = (points = geographyChartPoints) => points?.map(p => ({
    date: format(new Date(p.date), 'MMM d'),
    sessions: p.totalSessions,
    completed: p.completedSessions,
    users: p.uniqueUsers,
  })) ?? []

  const geographyChartData = toChartData(geographyChartPoints)
  const budgetChartData = toChartData(budgetChartPoints)

  const gameSections = [
    { name: 'Clueterra', stats: summary?.geography, color: 'text-violet-300' },
    { name: 'BudgetGuessr', stats: summary?.budget, color: 'text-emerald-300' },
  ].map(section => {
    const completionRate = section.stats && section.stats.totalSessions > 0
      ? Math.round((section.stats.completedSessions / section.stats.totalSessions) * 100)
      : 0
    return {
      ...section,
      cards: [
        { icon: Gamepad2, label: t.admin.totalSessions, value: section.stats?.totalSessions ?? 0, color: 'text-blue-400', bg: 'bg-blue-500/10' },
        { icon: Users, label: t.admin.uniqueUsers, value: section.stats?.uniqueUsers ?? 0, color: 'text-emerald-400', bg: 'bg-emerald-500/10' },
        { icon: TrendingUp, label: t.admin.activeToday, value: section.stats?.activeToday ?? 0, color: 'text-violet-400', bg: 'bg-violet-500/10' },
        { icon: Target, label: t.admin.completionRate, value: `${completionRate}%`, color: 'text-amber-400', bg: 'bg-amber-500/10' },
      ],
    }
  })

  const resourceCards = [
    { icon: Globe2, label: t.admin.countries, value: countries?.totalElements ?? 0, to: '/admin/countries' },
    { icon: Lightbulb, label: t.admin.hints, value: hints?.totalElements ?? 0, to: '/admin/hints' },
    { icon: Calendar, label: t.admin.challenges, value: challenges?.totalElements ?? 0, to: '/admin/challenges' },
    { icon: UserCog, label: t.admin.users, value: summary?.totalRegisteredUsers ?? 0, to: '/admin/users' },
  ]

  const tooltipStyle = {
    backgroundColor: '#0c0c14',
    border: '1px solid rgba(255,255,255,.08)',
    borderRadius: '10px',
    fontSize: '12px',
    color: '#fff',
  }

  return (
    <div className="p-5 sm:p-6 lg:p-8 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.dashboard}</h1>
          <p className="text-white/40 text-sm mt-1">{t.admin.overview}</p>
        </div>
        <div className="flex items-center gap-2 text-xs text-white/30">
          <Activity size={14} className="text-emerald-400" />
          {t.admin.live}
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        {gameSections.map((section, sectionIndex) => (
          <section key={section.name} className="rounded-xl bg-white/[.02] border border-white/[.06] p-4">
            <h2 className={`mb-3 text-sm font-bold ${section.color}`}>{section.name}</h2>
            <div className="grid grid-cols-2 gap-3">
              {section.cards.map((stat, i) => (
                <motion.div
                  key={stat.label}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: (sectionIndex * 4 + i) * 0.05 }}
                  className="rounded-xl bg-white/[.03] border border-white/[.06] p-4"
                >
                  {summaryLoading ? (
                    <Skeleton className="w-full h-12 rounded-lg" />
                  ) : (
                    <div className="flex items-start gap-3">
                      <div className={`p-2.5 rounded-lg ${stat.bg}`}>
                        <stat.icon size={18} className={stat.color} />
                      </div>
                      <div>
                        <p className="text-xl sm:text-2xl font-bold text-white">{stat.value}</p>
                        <p className="text-[11px] text-white/40 mt-0.5">{stat.label}</p>
                      </div>
                    </div>
                  )}
                </motion.div>
              ))}
            </div>
          </section>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-6">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="rounded-xl bg-white/[.03] border border-white/[.06] p-5"
        >
          <h3 className="font-semibold text-white text-sm mb-4">Clueterra · {t.admin.sessionsOverTime}</h3>
          <div className="h-56 sm:h-64 min-w-0">
            <ResponsiveContainer
              width="100%"
              height="100%"
              minWidth={0}
              initialDimension={CHART_INITIAL_DIMENSION}
            >
              <AreaChart data={geographyChartData}>
                <defs>
                  <linearGradient id="gradSessions" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.25} />
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="date" stroke="transparent" tick={{ fill: '#525252', fontSize: 11 }} />
                <YAxis stroke="transparent" tick={{ fill: '#525252', fontSize: 11 }} />
                <Tooltip contentStyle={tooltipStyle} itemStyle={{ color: '#fff' }} labelStyle={{ color: '#fff' }} />
                <Area type="monotone" dataKey="sessions" stroke="#3b82f6" strokeWidth={2} fillOpacity={1} fill="url(#gradSessions)" />
                <Line type="monotone" dataKey="completed" stroke="#22c55e" strokeWidth={2} dot={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.25 }}
          className="rounded-xl bg-white/[.03] border border-white/[.06] p-5"
        >
          <h3 className="font-semibold text-white text-sm mb-4">BudgetGuessr · {t.admin.sessionsOverTime}</h3>
          <div className="h-56 sm:h-64 min-w-0">
            <ResponsiveContainer
              width="100%"
              height="100%"
              minWidth={0}
              initialDimension={CHART_INITIAL_DIMENSION}
            >
              <LineChart data={budgetChartData}>
                <XAxis dataKey="date" stroke="transparent" tick={{ fill: '#525252', fontSize: 11 }} />
                <YAxis stroke="transparent" tick={{ fill: '#525252', fontSize: 11 }} />
                <Tooltip contentStyle={tooltipStyle} itemStyle={{ color: '#fff' }} labelStyle={{ color: '#fff' }} />
                <Line type="monotone" dataKey="sessions" stroke="#3b82f6" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="completed" stroke="#22c55e" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </motion.div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        {resourceCards.map((card, i) => (
          <NavLink key={i} to={card.to}>
            <motion.div
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3 + i * 0.05 }}
              className="rounded-xl bg-white/[.03] border border-white/[.06] hover:border-white/[.12] hover:bg-white/[.04] p-5 transition-all group cursor-pointer"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                  <div className="p-3 rounded-xl bg-white/[.04]">
                    <card.icon size={22} className="text-white/50" />
                  </div>
                  <div>
                    <p className="text-2xl sm:text-3xl font-bold text-white">{card.value}</p>
                    <p className="text-sm text-white/40">{card.label}</p>
                  </div>
                </div>
                <ArrowRight size={16} className="text-white/20 group-hover:text-white/40 transition-colors" />
              </div>
            </motion.div>
          </NavLink>
        ))}
      </div>
    </div>
  )
}
