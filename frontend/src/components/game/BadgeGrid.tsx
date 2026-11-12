import type { Ref } from 'react'
import { BadgeCard } from '@/components/game/BadgeCard'
import type { ProfileBadgeDto } from '@/types'

export type AchievementGridItem =
  | { type: 'badge'; badge: ProfileBadgeDto }

interface BadgeGridProps {
  items: AchievementGridItem[]
  containerRef?: Ref<HTMLDivElement>
}

export function BadgeGrid({ items, containerRef }: BadgeGridProps) {
  if (items.length === 0) return null

  return (
    <div
      ref={containerRef}
      className="grid auto-rows-fr grid-cols-2 gap-2.5 sm:gap-3 lg:grid-cols-3"
    >
      {items.map((item, index) => (
        <div key={item.badge.id} className="min-w-0">
          <BadgeCard badge={item.badge} index={index} />
        </div>
      ))}
    </div>
  )
}
