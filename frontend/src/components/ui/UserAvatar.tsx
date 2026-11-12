import { cn } from '@/utils/cn'

interface UserAvatarProps {
  avatarUrl?: string | null
  name?: string | null
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const SIZE_MAP = {
  sm: 'w-16 h-16',
  md: 'w-20 h-20',
  lg: 'w-24 h-24',
}

const FONT_MAP = {
  sm: 'text-xl',
  md: 'text-[28px]',
  lg: 'text-[32px]',
}

export function UserAvatar({ avatarUrl, name, size = 'md', className }: UserAvatarProps) {
  const initial = (name || '?')[0].toUpperCase()
  return (
    <div className={cn(
      'rounded-full bg-white/[.08] flex items-center justify-center overflow-hidden',
      SIZE_MAP[size],
      className,
    )}>
      {avatarUrl ? (
        <img src={avatarUrl} alt="" className="w-full h-full object-cover" />
      ) : (
        <span className={cn('font-bold text-white/35', FONT_MAP[size])}>
          {initial}
        </span>
      )}
    </div>
  )
}
