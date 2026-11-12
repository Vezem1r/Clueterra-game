import type { ReactNode } from 'react'
import { ArrowRight, ArrowUpRight, Crown, Target } from 'lucide-react'
import { useTranslation } from '@/i18n'
import type { LeaderboardEntry } from '@/types'
import { cn } from '@/utils/cn'
import { LeaderboardAvatar } from './LeaderboardAvatar'

export interface LeaderboardSelf {
  isAuthenticated: boolean
  isLoading: boolean
  username?: string
  displayName: string
  avatarUrl?: string | null
  rank: number | null
  totalXp: number | null
  level: number | null
  onLogin: () => void
}

interface LeaderboardChaseProps {
  entries: LeaderboardEntry[]
  self: LeaderboardSelf
  onEntryClick: (entry: LeaderboardEntry) => void
}

const score = (value: number) => value.toLocaleString()
const playerName = (entry: LeaderboardEntry) => entry.displayName || entry.username || 'Anonymous'
const LANE_SIZE = 25

function PlayerLink({ entry, onEntryClick, children, className }: {
  entry: LeaderboardEntry
  onEntryClick: (entry: LeaderboardEntry) => void
  children: ReactNode
  className?: string
}) {
  const { t } = useTranslation()
  return <button
    type="button"
    disabled={!entry.username}
    onClick={() => onEntryClick(entry)}
    className={cn('min-w-0 text-left transition-[background-color,border-color,transform] duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-300', className)}
  ><span className="sr-only">{t.leaderboard.rank} {entry.rank}: </span>{children}</button>
}

export function LeaderboardChase({ entries, self, onEntryClick }: LeaderboardChaseProps) {
  const { t } = useTranslation()
  const leader = entries[0]
  const leaderXp = leader?.totalXp ?? 0
  const rank = self.rank ?? 0
  const isLeader = self.isAuthenticated && rank === 1 && entries.length > 1
  const challenger = isLeader ? entries[1] : null
  const nextTarget = self.isAuthenticated && !isLeader
    ? rank > 1 && rank <= entries.length
      ? entries.filter(entry => entry.rank < rank).at(-1)
      : entries.at(-1)
    : null
  const gapToLeader = self.totalXp == null ? null : Math.max(0, leaderXp - self.totalXp)
  const gapToTarget = nextTarget && self.totalXp != null
    ? Math.max(0, nextTarget.totalXp - self.totalXp)
    : null

  return <div className="space-y-5">
    <section className="leaderboard-enter overflow-hidden rounded-[1.6rem] border border-white/10 bg-[linear-gradient(115deg,#10211f,#111922_55%,#191926)] p-3 sm:p-5">
      <div className="mb-3 flex items-center justify-between gap-3 px-1">
        <h2 className="text-xs font-black uppercase tracking-[.2em] text-emerald-200/70">{t.leaderboard.currentLeader} <span className="mx-1.5 text-white/25">/</span> {challenger ? t.leaderboard.closestChallenger : t.leaderboard.yourPosition}</h2>
        <span className="font-mono text-[10px] text-white/65">01—{String(entries.length).padStart(2, '0')}</span>
      </div>
      <div className="grid items-center gap-2 md:grid-cols-[minmax(0,1fr)_34px_minmax(0,1fr)]">
        <PlayerLink entry={leader} onEntryClick={onEntryClick} className="group flex min-h-[124px] w-full items-center gap-3 rounded-2xl border border-emerald-200/25 bg-emerald-200/[.055] p-3 hover:border-emerald-200/50 sm:gap-4 sm:p-4">
          <LeaderboardAvatar identity={leader.username} avatarUrl={leader.avatarUrl} name={playerName(leader)} size="md" className="h-11 w-11 sm:h-14 sm:w-14" />
          <span className="min-w-0 flex-1"><span className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[.15em] text-emerald-200/70"><Crown size={12} />{isLeader ? t.leaderboard.youLead : t.leaderboard.currentLeader}</span><span className="mt-1 block truncate text-base font-black text-white sm:text-lg">{playerName(leader)}</span><span className="mt-0.5 block text-[10px] text-white/65">{t.leaderboard.lvl} {leader.level} <span className="mx-1.5 text-white/20">·</span> {t.leaderboard.wins} {leader.challengesWon}</span></span>
          <span className="shrink-0 text-right"><span className="block text-sm font-black tabular-nums text-white sm:text-lg">{score(leaderXp)}</span><span className="text-[10px] font-bold tracking-[.14em] text-emerald-200/75">XP</span></span>
        </PlayerLink>

        <span aria-hidden="true" className="mx-auto hidden h-8 w-8 items-center justify-center rounded-full border border-white/15 bg-[#172021] text-[9px] font-black text-white/65 md:flex">VS</span>

        {challenger ? <PlayerLink entry={challenger} onEntryClick={onEntryClick} className="group flex min-h-[124px] w-full items-center gap-3 rounded-2xl border border-violet-200/20 bg-violet-200/[.045] p-3 hover:border-violet-200/45 sm:gap-4 sm:p-4">
          <LeaderboardAvatar identity={challenger.username} avatarUrl={challenger.avatarUrl} name={playerName(challenger)} size="md" className="h-11 w-11 sm:h-14 sm:w-14" />
          <span className="min-w-0 flex-1"><span className="block text-[10px] font-bold uppercase tracking-[.15em] text-violet-200/65">{t.leaderboard.closestChallenger} · #{challenger.rank}</span><span className="mt-1 block truncate text-base font-black text-white sm:text-lg">{playerName(challenger)}</span><span className="mt-0.5 block text-[10px] tabular-nums text-violet-200/75">{t.leaderboard.leadOverTarget}: {score(Math.max(0, leaderXp - challenger.totalXp))} XP</span></span>
          <ArrowUpRight size={16} className="shrink-0 text-violet-200/70 transition group-hover:text-violet-200" />
        </PlayerLink> : self.isAuthenticated ? <div className="flex min-h-[124px] items-center gap-3 rounded-2xl border border-violet-200/20 bg-violet-200/[.045] p-3 sm:gap-4 sm:p-4">
          <LeaderboardAvatar identity={self.username || self.displayName} avatarUrl={self.avatarUrl} name={self.displayName} size="md" className="h-11 w-11 sm:h-14 sm:w-14" />
          <div className="min-w-0 flex-1"><p className="text-[10px] font-bold uppercase tracking-[.15em] text-violet-200/65">{t.leaderboard.yourPosition} · {self.isLoading ? '…' : self.rank ? `#${self.rank}` : '—'}</p><p className="mt-1 truncate text-base font-black text-white sm:text-lg">{self.displayName}</p><p className="mt-0.5 text-[10px] text-violet-200/75">{self.rank ? self.rank <= entries.length ? t.leaderboard.insideTop.replace('{n}', String(entries.length)) : t.leaderboard.outsideTop.replace('{n}', String(entries.length)) : t.leaderboard.noRankYet}</p></div>
          <span className="shrink-0 text-right"><span className="block text-sm font-black tabular-nums text-white sm:text-lg">{self.totalXp == null ? '—' : score(self.totalXp)}</span><span className="text-[10px] font-bold tracking-[.14em] text-violet-200/75">XP</span></span>
        </div> : <div className="flex min-h-[124px] items-center gap-3 rounded-2xl border border-violet-200/20 bg-violet-200/[.045] p-3 sm:gap-4 sm:p-4">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-dashed border-violet-200/30 text-lg font-black text-violet-200/75 sm:h-14 sm:w-14">?</span>
          <span className="min-w-0 flex-1"><span className="block text-[10px] font-bold uppercase tracking-[.15em] text-violet-200/65">{t.leaderboard.guestTitle}</span><span className="mt-1 block text-sm font-bold leading-snug text-white sm:text-base">{t.leaderboard.guestHeadline}</span></span>
          <button type="button" onClick={self.onLogin} className="inline-flex min-h-9 shrink-0 items-center gap-1 rounded-lg border border-violet-200/30 px-2.5 text-[11px] font-bold text-white transition hover:border-violet-200/70 hover:bg-violet-200/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-300">{t.leaderboard.signInButton}<ArrowUpRight size={12} /></button>
        </div>}
      </div>

      {self.isAuthenticated && !isLeader && <div className="mt-3 grid gap-3 border-t border-white/[.08] px-1 pt-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] md:items-center">
        <div className="flex min-h-11 items-center justify-between gap-3 rounded-lg border border-violet-200/[.09] bg-violet-200/[.035] px-3"><span className="text-[11px] font-semibold text-white/70">{t.leaderboard.nextMilestone}</span><strong className="shrink-0 text-sm font-black tabular-nums text-violet-100">{gapToLeader == null ? '—' : `${score(gapToLeader)} XP`}</strong></div>
        {nextTarget && <PlayerLink entry={nextTarget} onEntryClick={onEntryClick} className="flex min-h-11 w-full items-center gap-2 rounded-lg border border-white/[.08] bg-white/[.025] px-2.5 hover:border-sky-200/30"><Target size={15} className="shrink-0 text-sky-200/60" /><span className="min-w-0 flex-1 truncate text-[11px] text-white/65">{self.rank && self.rank <= entries.length ? t.leaderboard.nextToPass : t.leaderboard.top50Threshold} <strong className="ml-1 text-white">#{nextTarget.rank} {playerName(nextTarget)}</strong></span><span className="shrink-0 text-[11px] font-bold tabular-nums text-sky-100/75">{gapToTarget == null ? '—' : `${score(gapToTarget)} XP`}</span><ArrowRight size={13} className="hidden shrink-0 text-white/30 sm:block" /></PlayerLink>}
      </div>}
    </section>

    <section aria-labelledby="leaderboard-board-title" className="space-y-2">
      <div className="flex items-center justify-between gap-3 border-b border-white/10 pb-2">
        <h2 id="leaderboard-board-title" className="text-sm font-black uppercase tracking-[.15em] text-white/75">{t.leaderboard.tableTitle}</h2>
        <span className="font-mono text-[11px] tabular-nums text-white/65">{entries.length} / 50</span>
      </div>
      <div className="grid items-start gap-3 xl:grid-cols-2">
        {[entries.slice(0, LANE_SIZE), entries.slice(LANE_SIZE)].filter(lane => lane.length > 0).map(lane => <div key={lane[0].rank} className="overflow-hidden rounded-2xl border border-white/[.08] bg-[#101820]/85 px-2 sm:px-3">
          <div className="flex items-center justify-between border-b border-white/[.08] px-2 py-2 text-[10px] font-bold uppercase tracking-[.14em] text-white/65"><span>#{String(lane[0].rank).padStart(2, '0')} — #{String(lane.at(-1)!.rank).padStart(2, '0')}</span><span>XP</span></div>
          {lane.map(entry => {
            const isCurrent = Boolean(self.username && entry.username.toLowerCase() === self.username.toLowerCase())
            return <PlayerLink key={entry.rank} entry={entry} onEntryClick={onEntryClick} className={cn('grid min-h-[66px] w-full grid-cols-[2rem_minmax(0,1fr)_auto] items-center gap-2 border-b border-white/[.065] px-2 py-1.5 last:border-0 hover:bg-white/[.045] sm:grid-cols-[2.5rem_minmax(0,1fr)_auto]', entry.rank === 1 && 'bg-emerald-200/[.055]', entry.rank === 2 && 'bg-sky-200/[.025]', entry.rank === 3 && 'bg-amber-200/[.025]', isCurrent && 'bg-violet-200/[.08] ring-1 ring-inset ring-violet-200/25')}>
              <span className={cn('font-mono text-xs font-black tabular-nums text-white/65', entry.rank === 1 && 'text-emerald-200', isCurrent && 'text-violet-200')}>{String(entry.rank).padStart(2, '0')}</span>
              <span className="flex min-w-0 items-center gap-2.5"><LeaderboardAvatar identity={entry.username} avatarUrl={entry.avatarUrl} name={playerName(entry)} size="sm" className="h-8 w-8" /><span className="min-w-0"><span className="block truncate text-xs font-bold text-white sm:text-sm">{playerName(entry)}</span><span className="block truncate text-[10px] text-white/65">@{entry.username} <span className="mx-1 text-white/20">·</span> {t.leaderboard.lvl} {entry.level} <span className="mx-1 text-white/20">·</span> {t.leaderboard.wins} {entry.challengesWon}</span></span></span>
              <span className="shrink-0 text-right text-sm font-black tabular-nums text-white sm:text-base">{score(entry.totalXp)} <span className="text-[10px] font-bold text-white/65">XP</span></span>
            </PlayerLink>
          })}
        </div>)}
      </div>
    </section>
  </div>
}
