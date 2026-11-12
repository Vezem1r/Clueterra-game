import type { TodayStateGame } from '@/lib/todayStateHint'

interface TodayStateLoadingCardProps {
  placeholderFor: TodayStateGame
}

export function TodayStateLoadingCard({ placeholderFor }: TodayStateLoadingCardProps) {
  return (
    <div data-today-placeholder={placeholderFor} aria-hidden="true">
      <div className="flex w-full max-w-[50rem] min-h-[34rem] flex-col items-center justify-center rounded-[2.4rem] border border-white/[.11] bg-[linear-gradient(165deg,rgba(12,15,26,.97),rgba(7,9,17,.99))] p-8 shadow-[0_40px_140px_rgba(0,0,0,.52),0_0_0_1px_rgba(255,255,255,.035)] md:backdrop-blur-[40px]">
        <div className="mx-auto h-8 w-8 rounded-full border-2 border-white/20 border-t-white/65 animate-spin" />
      </div>
    </div>
  )
}
