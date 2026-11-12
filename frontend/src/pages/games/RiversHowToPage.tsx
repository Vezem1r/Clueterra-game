import { ArrowLeft, ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Header } from '@/components/layout/Header'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { RiverGuide } from './rivers/RiverGuide'
import { RIVER_CANONICAL_PATH, RIVER_EXAMPLE_MAP, RIVER_GUIDE_PATH, RIVER_SHARE_IMAGE } from './rivers/riverGameConstants'
import { useLocalizedPath } from '@/hooks/useLocalizedPath'

export function RiversHowToPage() {
  const { t } = useTranslation()
  const copy = t.riverGame
  const localize = useLocalizedPath()

  useSeoMeta({
    title: `${copy.guide.howTitle} | Clueterra`,
    description: copy.leadDetail,
    canonicalPath: RIVER_GUIDE_PATH,
    image: RIVER_SHARE_IMAGE,
    imageAlt: copy.exampleAlt,
  })

  return <div className="relative isolate min-h-[100dvh] overflow-x-hidden bg-river-night text-white">
    <Header />
    <main className="mx-auto max-w-[1200px] px-4 pb-28 pt-8 sm:px-8 sm:pt-12 lg:px-12">
      <div className="relative isolate overflow-hidden rounded-[2rem] border border-river-glacier/15 bg-river-channel/45 px-6 pb-8 pt-7 sm:px-10 sm:pb-12 sm:pt-10 lg:px-14">
        <img src={RIVER_EXAMPLE_MAP} alt="" aria-hidden="true" className="absolute inset-y-0 right-0 -z-10 h-full w-full object-cover opacity-35 [mask-image:linear-gradient(to_right,transparent_10%,black_100%)] md:w-[70%]" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-gradient-to-r from-river-night/85 via-river-night/55 to-river-night/15" />
        <Link to={localize(RIVER_CANONICAL_PATH)} className="inline-flex items-center gap-2 text-sm font-medium text-river-mist/70 hover:text-river-glacier-strong">
          <ArrowLeft size={15} aria-hidden="true" /> {copy.title}
        </Link>
        <h1 className="mt-8 max-w-[40rem] font-display text-4xl font-semibold tracking-[-.045em] text-white sm:text-5xl">{copy.guide.howTitle}</h1>
        <p className="mt-4 max-w-[37rem] text-[15px] leading-7 text-river-mist/75 sm:text-base">{copy.leadDetail}</p>
        <Link to={localize(RIVER_CANONICAL_PATH)} className="mt-7 inline-flex items-center gap-2 rounded-2xl bg-river-glacier px-5 py-3 text-sm font-semibold text-river-night transition-colors hover:bg-river-glacier-strong">
          {copy.start} <ArrowRight size={16} aria-hidden="true" />
        </Link>
      </div>
      <div className="mt-11 sm:mt-14"><RiverGuide copy={copy.guide} /></div>
    </main>
    <HomeLegalStrip guideMode="river" />
  </div>
}
