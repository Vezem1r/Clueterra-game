import { cn } from '@/utils/cn'

const sizeClasses = {
  sm: 'h-16 w-16 text-xl',
  md: 'h-20 w-20 text-[28px]',
  lg: 'h-24 w-24 text-[32px]',
}

const swatches = [
  ['#7b67d9', '#32245f'],
  ['#e58a68', '#713c50'],
  ['#48bcae', '#1c536a'],
  ['#cba061', '#715044'],
  ['#628fdb', '#273c7d'],
  ['#c37cae', '#613d73'],
  ['#9bbd69', '#3d6250'],
  ['#d67e68', '#743b39'],
] as const

function avatarSwatch(identity: string): readonly [string, string] {
  const hashed = [...identity].reduce((value, character) => Math.imul(value ^ character.charCodeAt(0), 16777619), 2166136261)
  let mixed = hashed ^ (hashed >>> 16)
  mixed = Math.imul(mixed, 0x7feb352d)
  mixed ^= mixed >>> 15
  return swatches[(mixed >>> 0) % swatches.length]
}

function leaderboardAvatarUrl(avatarUrl: string): string {
  try {
    const path = new URL(avatarUrl, 'https://clueterra.com').pathname
    const match = path.match(/^\/(?:storage\/)?avatars\/([1-9]\d*)\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(?:png|jpe?g))$/)
    return match ? `/api/assets/avatars/${match[1]}/${match[2]}/128.jpg` : avatarUrl
  } catch {
    return avatarUrl
  }
}

export function LeaderboardAvatar({ name, identity, avatarUrl, size = 'md', className }: {
  name: string
  identity: string
  avatarUrl?: string | null
  size?: 'sm' | 'md' | 'lg'
  className?: string
}) {
  const [start, end] = avatarSwatch(identity)
  const prominent = size !== 'sm'
  return (
    <span
      aria-hidden="true"
      className={cn('relative flex shrink-0 items-center justify-center overflow-hidden rounded-full border border-white/20 font-black text-white shadow-[inset_0_1px_0_rgba(255,255,255,.25),0_10px_30px_rgba(0,0,0,.25)]', sizeClasses[size], className)}
      style={{ background: `linear-gradient(145deg, ${start}, ${end})` }}
    >
      {avatarUrl ? <img
        src={leaderboardAvatarUrl(avatarUrl)}
        alt=""
        width={128}
        height={128}
        loading={prominent ? 'eager' : 'lazy'}
        fetchPriority={prominent ? 'high' : 'auto'}
        decoding="async"
        className="h-full w-full object-cover"
      /> : <span className="relative drop-shadow-[0_2px_3px_rgba(0,0,0,.3)]">{(name || '?')[0].toUpperCase()}</span>}
    </span>
  )
}
