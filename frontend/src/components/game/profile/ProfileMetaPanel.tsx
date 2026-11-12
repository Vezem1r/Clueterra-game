import { Crown, Mountain, Sparkles, type LucideIcon } from 'lucide-react'
import { useTranslation } from '@/i18n'
import type { ProfileDto } from '@/types'
import { cn } from '@/utils/cn'

interface ProfileMetaPanelProps {
  profile: ProfileDto
  className?: string
}

interface MetaItem {
  icon: LucideIcon
  label: string
  value: string
  toneClassName: string
  iconClassName: string
}

function getDifficultyLabel(difficulty: ProfileDto['favoriteDifficulty'], t: ReturnType<typeof useTranslation>['t']) {
  if (difficulty === 'EASY') return t.home.easy
  if (difficulty === 'MEDIUM') return t.home.normal
  if (difficulty === 'HARD') return t.home.hard
  return '-'
}

function MetaCard({ item }: { item: MetaItem }) {
  return (
    <div className="min-w-0 border-white/[.06] px-2.5 py-3 sm:px-3.5 sm:py-4 lg:border-l first:lg:border-l-0">
      <div className="flex min-h-[30px] flex-col items-center justify-start gap-1 sm:min-h-0 sm:flex-row sm:gap-1.5 lg:justify-start">
        <item.icon size={15} className={cn('shrink-0 sm:size-[17px]', item.iconClassName)} />
        <p className="min-w-0 text-center text-[7.5px] font-bold uppercase leading-tight tracking-[.08em] text-white/46 sm:text-left sm:text-[11px] sm:font-semibold sm:tracking-normal">
          {item.label}
        </p>
      </div>
      <p className={cn('mt-2 max-w-full break-words text-center text-[1.42rem] font-black leading-tight sm:text-[1.72rem] lg:text-left 2xl:text-[1.92rem]', item.toneClassName)}>
        {item.value}
      </p>
    </div>
  )
}

export function ProfileMetaPanel({ profile, className }: ProfileMetaPanelProps) {
  const { t } = useTranslation()
  const metaItems: MetaItem[] = [
    {
      icon: Sparkles,
      label: t.profile.level,
      value: String(profile.level),
      toneClassName: 'text-violet-200',
      iconClassName: 'text-violet-300',
    },
    {
      icon: Crown,
      label: t.profile.rank,
      value: profile.leaderboardRank ? `#${profile.leaderboardRank}` : '-',
      toneClassName: 'text-amber-200',
      iconClassName: 'text-amber-300',
    },
    {
      icon: Mountain,
      label: t.profile.favoriteDifficulty,
      value: getDifficultyLabel(profile.favoriteDifficulty, t),
      toneClassName: 'text-emerald-200',
      iconClassName: 'text-emerald-300',
    },
  ]

  return (
    <div className={cn('overflow-hidden rounded-2xl border border-white/[.07] bg-black/22', className)}>
      <div className="grid grid-cols-3 divide-x divide-white/[.06]">
        {metaItems.map(item => <MetaCard key={item.label} item={item} />)}
      </div>
    </div>
  )
}
