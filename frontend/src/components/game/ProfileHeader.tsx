import { motion } from 'framer-motion'
import { Pencil } from 'lucide-react'
import { ProfileHeroBackground } from '@/components/game/ProfileHeroBackground'
import { GuestProfileSavePrompt } from '@/components/game/profile/GuestProfileSavePrompt'
import { ProfileMetaPanel } from '@/components/game/profile/ProfileMetaPanel'
import { UserAvatar } from '@/components/ui/UserAvatar'
import { useTranslation } from '@/i18n'
import type { ProfileDto } from '@/types'
import { cn } from '@/utils/cn'

interface ProfileHeaderProps {
  profile: ProfileDto
  guest?: boolean
  email?: string
  showSettings?: boolean
  onSettingsClick?: () => void
  className?: string
}

function getDisplayName(profile: ProfileDto, email?: string) {
  const rawDisplayName = profile.displayName?.trim()
  if (rawDisplayName) return rawDisplayName
  if (profile.username) return profile.username
  if (email) return email.split('@')[0]
  return 'Anonymous'
}

function AvatarFrame({ avatarUrl, level, avatarName }: { avatarUrl: string | null; level: number; avatarName: string }) {
  return (
    <div className="relative mx-auto h-[154px] w-[154px] shrink-0 sm:h-[198px] sm:w-[198px]">
      <div className="absolute inset-0 rounded-full border border-sky-200/12 bg-[linear-gradient(145deg,rgba(125,211,252,.08),rgba(124,92,252,.04)_48%,rgba(2,6,23,.18))] shadow-[0_22px_70px_rgba(0,0,0,.45)]" />
      <div className="absolute inset-[6px] rounded-full border border-violet-300/70 shadow-[0_0_0_1px_rgba(125,211,252,.22),0_0_32px_rgba(124,92,252,.24)]" />
      <div className="absolute inset-[13px] rounded-full border border-white/[.12]" />
      <div className="absolute inset-[18px] rounded-full bg-black/20 shadow-[inset_0_0_28px_rgba(0,0,0,.44)]" />

      <div className="absolute inset-0 flex items-center justify-center">
        <UserAvatar
          avatarUrl={avatarUrl}
          name={avatarName}
          size="lg"
          className="h-[116px] w-[116px] border border-white/[.1] bg-white/[.06] shadow-[0_20px_56px_rgba(0,0,0,.6)] sm:h-[150px] sm:w-[150px]"
        />
      </div>

      <div className="absolute -bottom-1 left-1/2 flex h-[52px] min-w-[52px] -translate-x-1/2 items-center justify-center sm:-bottom-2 sm:h-[58px] sm:min-w-[58px]">
        <div className="absolute inset-0 [clip-path:polygon(50%_0,88%_18%,88%_66%,50%_100%,12%_66%,12%_18%)] bg-[linear-gradient(180deg,#9a8cff,#5b4ef2)] shadow-[0_14px_34px_rgba(124,92,252,.38)]" />
        <div className="absolute inset-[3px] [clip-path:polygon(50%_0,88%_18%,88%_66%,50%_100%,12%_66%,12%_18%)] bg-[linear-gradient(180deg,#171c4c,#0b1030)]" />
        <span className="relative text-lg font-black text-white drop-shadow-[0_1px_6px_rgba(0,0,0,.55)] sm:text-xl">{level}</span>
      </div>
    </div>
  )
}

export function ProfileHeader({
  profile,
  guest = false,
  email,
  showSettings,
  onSettingsClick,
  className,
}: ProfileHeaderProps) {
  const { t } = useTranslation()
  const displayName = guest ? t.guestProfile.title : getDisplayName(profile, email)

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.1 }}
      className={cn('w-full self-start', className)}
    >
      <ProfileHeroBackground className="rounded-[1.55rem] shadow-[0_30px_90px_rgba(0,0,0,.38)]">
        {showSettings && onSettingsClick ? (
          <button
            onClick={onSettingsClick}
            aria-label={t.profile.editProfile}
            className="absolute right-4 top-4 z-10 inline-flex h-10 w-10 items-center justify-center rounded-full border border-white/[.09] bg-white/[.035] text-white/42 transition-colors hover:bg-white/[.07] hover:text-white/74 sm:right-6 sm:top-6"
          >
            <Pencil size={16} />
          </button>
        ) : null}

        <div className="relative z-[2] px-5 pb-5 pt-7 sm:px-8 sm:pb-7 sm:pt-10 lg:px-10">
          <div className="grid gap-6 lg:grid-cols-[220px_minmax(0,1fr)] lg:items-center">
            <AvatarFrame avatarUrl={profile.avatarUrl} level={profile.level} avatarName={guest ? t.guestProfile.avatarName : displayName} />

            <div className="min-w-0">
              <h1 className="text-center text-[2.25rem] font-black leading-tight text-white drop-shadow-[0_2px_10px_rgba(0,0,0,.45)] sm:text-6xl lg:text-left">
                {displayName}
              </h1>

              <div className="mt-2.5 flex flex-col items-center gap-0.5 text-center lg:items-start lg:text-left">
                {guest ? (
                  <>
                    <span className="text-base font-semibold text-emerald-200/70 sm:text-lg">{t.guestProfile.privateBadge}</span>
                    <span className="text-sm text-white/40 sm:text-base">{t.guestProfile.temporaryBadge}</span>
                  </>
                ) : (
                  <>
                    {profile.username ? <span className="text-base font-semibold text-white/54 sm:text-lg">@{profile.username}</span> : null}
                    {email ? <span className="break-all text-sm text-white/36 sm:text-base sm:text-white/38">{email}</span> : null}
                  </>
                )}
              </div>
              {guest ? <GuestProfileSavePrompt /> : <ProfileMetaPanel profile={profile} className="mt-5 sm:mt-7" />}
            </div>
          </div>
        </div>
      </ProfileHeroBackground>
    </motion.div>
  )
}
