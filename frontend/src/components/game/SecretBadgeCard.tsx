import { motion } from 'framer-motion'
import { EyeOff } from 'lucide-react'

import { BadgeCard } from '@/components/game/BadgeCard'
import { useTranslation } from '@/i18n'
import type { ProfileBadgeDto } from '@/types'

export function SecretBadgeCard({ badge, revealed, onReveal }: { badge: ProfileBadgeDto | null; revealed: boolean; onReveal: () => void }) {
  const { t } = useTranslation()

  if (revealed && badge) {
    return (
      <motion.div
        initial={{ opacity: 0, scale: 0.8, rotateY: 90 }}
        animate={{ opacity: 1, scale: 1, rotateY: 0 }}
        transition={{ type: 'spring', stiffness: 300, damping: 24 }}
      >
        <BadgeCard badge={{ ...badge, unlocked: true }} />
      </motion.div>
    )
  }

  return (
    <button
      type="button"
      onClick={onReveal}
      className="group relative isolate flex h-[9.2rem] w-full flex-col items-center justify-center overflow-hidden rounded-[1.25rem] border border-dashed border-white/[.1] bg-[linear-gradient(180deg,rgba(255,255,255,.02),rgba(5,10,20,.94))] p-2.5 text-left transition-all duration-500 hover:border-white/[.16] hover:bg-white/[.03] sm:h-[11.2rem] sm:rounded-[1.45rem] sm:p-4"
    >
      <div className="pointer-events-none absolute inset-x-[18%] top-2 h-16 rounded-full bg-[radial-gradient(circle,rgba(125,211,252,.05),transparent_70%)] blur-2xl transition-opacity duration-500 group-hover:opacity-80" />
      <div className="flex flex-col items-center gap-2">
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-white/[.06] bg-white/[.04] sm:h-16 sm:w-16">
          <EyeOff size={20} className="text-white/25 sm:[&]:h-6 sm:[&]:w-6" />
        </div>
        <p className="text-[10px] font-medium text-white/30">???</p>
        <p className="text-[9px] text-white/20">{t.guide.step4SecretTap}</p>
      </div>
    </button>
  )
}
