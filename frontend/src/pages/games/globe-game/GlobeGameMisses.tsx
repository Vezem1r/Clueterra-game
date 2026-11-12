import type { Ref } from 'react'
import { X } from 'lucide-react'

export interface GlobeGameMiss {
  key: string
  label: string
  title?: string
}

export interface GlobeGameMissesProps {
  misses: GlobeGameMiss[]
  announcement: string | null
  notice?: string | null
  attempts?: string | null
  listRef?: Ref<HTMLUListElement>
}

export function GlobeGameMisses({ misses, announcement, notice, attempts, listRef }: GlobeGameMissesProps) {
  return <div className="mb-4 empty:mb-0" aria-live="polite">
    {notice && <p className="text-xs text-amber-200/80">{notice}</p>}
    {announcement && <p role="status" className="sr-only">{announcement}</p>}
    {misses.length > 0
      ? <ul ref={listRef} className="flex flex-wrap gap-1.5">
        {misses.map(miss => <li key={miss.key} title={miss.title}
          className="inline-flex items-center gap-1 rounded-full border border-rose-300/20 bg-rose-400/[.08] px-2.5 py-1 text-xs text-rose-100/80">
          <X size={12} aria-hidden="true" /> <span className="line-through decoration-rose-300/60">{miss.label}</span>
        </li>)}
      </ul>
      : attempts && <p className="text-xs text-white/40">{attempts}</p>}
  </div>
}
