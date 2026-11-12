import { useEffect, useState } from 'react'
import { motion, useReducedMotion } from 'framer-motion'
import { MapPin } from 'lucide-react'
import { silhouettesApi, type SilhouetteLocale, type SilhouetteReveal } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { cn } from '@/utils/cn'
import { interpolate } from '@/utils/format'
import { factNotes, factStats } from './silhouetteFactItems'
import { flagUrl } from './silhouetteGameConstants'
import { flagTones } from './silhouetteGlobeStyle'

interface SilhouettePassportProps {
  copy: TranslationKeys['silhouetteGame']
  iso: string
  language: SilhouetteLocale
}

const cache = new Map<string, Promise<SilhouetteReveal>>()
const ENTER = { opacity: 0, y: 28, scale: 0.82, rotateX: 32 }
const SHOWN = { opacity: 1, y: 0, scale: 1, rotateX: 0 }
const SPRING = { type: 'spring', stiffness: 280, damping: 24 } as const
const GLOW_ALPHA = '66'
const HALO_ALPHA = '33'

function loadCountry(iso: string) {
  const cached = cache.get(iso)
  if (cached) return cached
  const request = silhouettesApi.country(iso).catch(error => {
    cache.delete(iso)
    throw error
  })
  cache.set(iso, request)
  return request
}

export function SilhouettePassport({ copy, iso, language }: SilhouettePassportProps) {
  const [country, setCountry] = useState<SilhouetteReveal | null>(null)
  const reducedMotion = useReducedMotion() ?? false

  useEffect(() => {
    let cancelled = false
    loadCountry(iso).then(loaded => {
      if (!cancelled) setCountry(loaded)
    }).catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [iso])

  const current = country?.countryCode === iso ? country : null
  const tones = current ? flagTones(current.flagColors) : null
  const name = current?.names[language]
  const photo = current?.photo
  const credit = photo?.licenseLabel
    ? photo.authorName
      ? interpolate(copy.photoCredit, { author: photo.authorName, license: photo.licenseLabel })
      : interpolate(copy.photoCreditUnknown, { license: photo.licenseLabel })
    : null
  const stats = current ? factStats(copy.facts, current.facts, language) : []
  const notes = current ? factNotes(copy.facts, current.facts) : []

  return <motion.article initial={reducedMotion ? false : ENTER} animate={SHOWN} transition={SPRING}
    data-testid="silhouette-passport"
    style={tones ? { boxShadow: `0 34px 80px rgba(0,0,0,.6), 0 0 0 1px ${tones.fill}${GLOW_ALPHA}, 0 0 56px ${tones.fill}${HALO_ALPHA}` } : undefined}
    className="origin-bottom overflow-hidden rounded-[1.4rem] border border-white/[.12] bg-[#070b14] [backface-visibility:hidden]">
    <div className="relative -mb-px aspect-[16/10] overflow-hidden"
      style={tones ? { background: `linear-gradient(135deg, ${tones.fill}, ${tones.edge})` } : undefined}>
      {photo && <img src={photo.url} alt={interpolate(copy.photoAlt, { name: name ?? iso })} className="absolute inset-0 h-full w-full object-cover" />}
      <div aria-hidden="true" className="absolute inset-0 bg-[linear-gradient(to_top,rgba(7,11,20,1)_0%,rgba(7,11,20,.55)_38%,transparent_70%)]" />
      <img src={flagUrl(iso)} alt="" aria-hidden="true"
        className="absolute left-3 top-3 h-7 w-[2.6rem] rounded-[5px] object-cover shadow-[0_6px_18px_rgba(0,0,0,.5)] ring-1 ring-white/40" />
      {current && <p className="absolute right-3 top-3 rounded-full bg-black/45 px-2.5 py-1 text-[11px] font-medium text-white/80 backdrop-blur-md">
        {copy.regions[current.subregion] ?? current.subregion}
      </p>}
      <div className="absolute inset-x-0 bottom-0 px-4 pb-3">
        <h3 className="text-[1.65rem] font-black leading-none tracking-[-.03em] text-white [text-shadow:0_2px_18px_rgba(0,0,0,.6)]">{name ?? copy.globe.cardLoading}</h3>
        {current?.facts.capital && <p className="mt-1 text-sm text-white/70">{copy.facts.capital}: {current.facts.capital[language]}</p>}
        {photo?.landmarkNames && <p className="mt-1.5 flex items-center gap-1 text-xs font-medium text-cyan-100/80">
          <MapPin size={12} aria-hidden="true" /> {photo.landmarkNames[language]}
        </p>}
      </div>
    </div>
    {current && <div className="px-3 pb-3 pt-3">
      <dl className="grid grid-cols-2 gap-2">
        {stats.filter(stat => stat.key !== 'capital').map(stat => <div key={stat.key}
          className={cn('rounded-xl bg-white/[.04] px-3 py-2 ring-1 ring-white/[.06]', stat.key === 'peak' && 'col-span-2')}>
          <dt className="flex items-center gap-1.5 text-[11px] text-white/45"><stat.icon size={12} aria-hidden="true" /> {stat.label}</dt>
          <dd className="mt-0.5 truncate text-[15px] font-bold tabular-nums text-white">{stat.value}</dd>
        </div>)}
      </dl>
      {notes.length > 0 && <ul className="mt-2 grid gap-1.5">
        {notes.map(note => <li key={note.key} className={cn('flex items-center gap-2 rounded-xl px-3 py-1.5 text-xs',
          note.notable ? 'bg-amber-300/10 text-amber-100' : 'bg-white/[.03] text-white/70')}>
          <note.icon size={13} aria-hidden="true" className="shrink-0" /> {note.text}
        </li>)}
      </ul>}
      {credit && <p className="mt-2.5 truncate px-1 text-[10px] text-white/40">{credit}</p>}
    </div>}
  </motion.article>
}
