import type { ComponentType, ReactNode } from 'react'
import { ArrowRight, BookOpenCheck, Check, Gift, Globe2, Landmark, Layers3 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { Header } from '@/components/layout/Header'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { useTranslation } from '@/i18n'
import { cn } from '@/utils/cn'
import { CLUETERRA_SHARE_IMAGE } from '@/constants/site'

interface GameHalfProps {
  accent: 'clueterra' | 'budget'
  brand: string
  icon: ComponentType<{ size?: number; strokeWidth?: number; className?: string }>
  title: string
  body: string
  facts: string[]
  playTo: string
  playLabel: string
  guideTo: string
  guideLabel: string
}

const ACCENTS = {
  clueterra: {
    panel: 'bg-[radial-gradient(circle_at_18%_12%,rgba(124,92,252,.22),transparent_42%),linear-gradient(160deg,rgba(17,16,34,.96),rgba(7,8,16,.98))]',
    chip: 'border-violet-300/20 bg-violet-400/10 text-violet-200',
    check: 'text-violet-300',
    play: 'bg-white text-black hover:bg-white/90',
  },
  budget: {
    panel: 'bg-[radial-gradient(circle_at_82%_12%,rgba(52,211,153,.18),transparent_42%),linear-gradient(200deg,rgba(13,26,25,.96),rgba(7,9,14,.98))]',
    chip: 'border-amber-200/20 bg-amber-300/10 text-amber-100',
    check: 'text-emerald-300',
    play: 'bg-gradient-to-r from-emerald-300 to-teal-300 text-[#06100d] hover:brightness-105',
  },
} as const

export function AboutPage() {
  const { t } = useTranslation()

  useSeoMeta({
    title: t.aboutPage.seoTitle,
    description: t.aboutPage.seoDescription,
    canonicalPath: '/about',
    image: CLUETERRA_SHARE_IMAGE,
    keywords: 'about Clueterra, geography games, country guessing game, BudgetGuessr, megaproject cost game',
  })

  return (
    <div className="min-h-[100dvh] overflow-x-clip bg-background text-white">
      <GameAuroraBackdrop variant="hero" />
      <Header />

      <main className="relative z-10 px-4 pb-24 pt-6 sm:px-6 md:pb-16 lg:px-10">
        <div className="mx-auto max-w-6xl">
          <div className="mx-auto max-w-5xl text-center">
            <p className="text-xs font-semibold uppercase tracking-[.24em] text-emerald-300/70">
              {t.aboutPage.pageEyebrow}
            </p>
            <h1 className="mx-auto mt-3 max-w-3xl text-balance text-4xl font-bold tracking-[-.035em] text-white sm:text-5xl">
              {t.aboutPage.pageTitle}
            </h1>
            <p className="mt-4 text-pretty text-base leading-8 text-white/56 sm:text-lg">{t.aboutPage.pageIntro}</p>
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            <GameHalf
              accent="clueterra"
              brand="Clueterra"
              icon={Globe2}
              title={t.aboutPage.clueterraTitle}
              body={t.aboutPage.clueterraBody}
              facts={[t.aboutPage.clueterraFact1, t.aboutPage.clueterraFact2, t.aboutPage.clueterraFact3]}
              playTo="/"
              playLabel={t.aboutPage.clueterraPlayCta}
              guideTo="/how-to"
              guideLabel={t.aboutPage.howToCta}
            />
            <GameHalf
              accent="budget"
              brand={t.nav.budget}
              icon={Landmark}
              title={t.aboutPage.budgetTitle}
              body={t.aboutPage.budgetBody}
              facts={[t.aboutPage.budgetFact1, t.aboutPage.budgetFact2, t.aboutPage.budgetFact3]}
              playTo="/budget"
              playLabel={t.aboutPage.budgetPlayCta}
              guideTo="/budget/how-to"
              guideLabel={t.aboutPage.howToCta}
            />
          </div>

          <section className="mt-3 rounded-[2rem] border border-white/[.08] bg-[#0b1119]/75 px-5 py-4 shadow-[0_24px_70px_rgba(0,0,0,.18)] sm:px-7" aria-labelledby="more-ways-to-play-heading">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="max-w-2xl">
                <h2 id="more-ways-to-play-heading" className="text-lg font-semibold text-white">{t.aboutPage.moreWaysTitle}</h2>
                <p className="mt-1 text-sm leading-6 text-white/60">{t.aboutPage.moreWaysBody}</p>
              </div>
              <Link to="/games" className="inline-flex min-h-10 shrink-0 items-center gap-2 self-start rounded-xl border border-emerald-200/25 bg-emerald-200/[.08] px-4 text-sm font-semibold text-emerald-100 transition-colors hover:bg-emerald-200/[.14] sm:self-auto">
                {t.aboutPage.exploreGamesCta}<ArrowRight size={15} />
              </Link>
            </div>
          </section>

          <div className="mt-2 grid gap-4 md:grid-cols-3">
            <SharedFact icon={<Gift size={18} />} title={t.aboutPage.freeTitle} body={t.aboutPage.freeBody} />
            <SharedFact
              icon={<BookOpenCheck size={18} />}
              title={t.aboutPage.realTitle}
              body={t.aboutPage.realBody}
              link={{ to: '/data-sources', label: t.aboutPage.sourcesCta }}
            />
            <SharedFact icon={<Layers3 size={18} />} title={t.aboutPage.waysTitle} body={t.aboutPage.waysBody} />
          </div>
        </div>
      </main>

      <HomeLegalStrip />
    </div>
  )
}

function GameHalf({ accent, brand, icon: Icon, title, body, facts, playTo, playLabel, guideTo, guideLabel }: GameHalfProps) {
  const styles = ACCENTS[accent]

  return (
    <section
      className={cn(
        'relative isolate flex flex-col overflow-hidden rounded-[2.5rem] border border-white/[.09] px-6 py-7 shadow-[0_40px_120px_rgba(0,0,0,.38)] sm:px-10',
        styles.panel,
      )}
    >
      <Icon
        size={260}
        strokeWidth={0.8}
        className="pointer-events-none absolute -bottom-14 -right-12 -z-10 text-white/[.045]"
      />

      <span className={cn('inline-flex w-fit items-center gap-2 rounded-full border px-3 py-1.5 text-[11px] font-bold uppercase tracking-[.16em]', styles.chip)}>
        <Icon size={13} />
        {brand}
      </span>

      <h2 className="mt-5 text-3xl font-bold tracking-[-.03em] text-white sm:text-4xl">{title}</h2>
      <p className="mt-4 text-base leading-7 text-white/58">{body}</p>

      <ul className="mt-5 grid gap-2.5">
        {facts.map((fact) => (
          <li key={fact} className="flex items-start gap-2.5 text-sm leading-6 text-white/70">
            <Check size={16} className={cn('mt-1 shrink-0', styles.check)} />
            {fact}
          </li>
        ))}
      </ul>

      <div className="mt-auto flex flex-col gap-3 pt-6 sm:flex-row">
        <Link
          to={playTo}
          className={cn('inline-flex items-center justify-center gap-2 rounded-2xl px-5 py-3 text-sm font-semibold transition', styles.play)}
        >
          {playLabel}
          <ArrowRight size={15} />
        </Link>
        <Link
          to={guideTo}
          className="inline-flex items-center justify-center rounded-2xl border border-white/[.1] bg-white/[.04] px-5 py-3 text-sm font-medium text-white/70 transition-colors hover:bg-white/[.08] hover:text-white"
        >
          {guideLabel}
        </Link>
      </div>
    </section>
  )
}

function SharedFact({
  icon,
  title,
  body,
  link,
}: {
  icon: ReactNode
  title: string
  body: string
  link?: { to: string; label: string }
}) {
  return (
    <article className="rounded-[2rem] border border-white/[.08] bg-white/[.03] p-4 shadow-[0_24px_70px_rgba(0,0,0,.2)] backdrop-blur-xl">
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-white/[.08] bg-white/[.05] text-white/70">
          {icon}
        </div>
        <h3 className="text-base font-semibold text-white">{title}</h3>
      </div>
      <p className="mt-3 text-sm leading-6 text-white/48">{body}</p>
      {link && (
        <Link
          to={link.to}
          className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-white/55 transition-colors hover:text-white"
        >
          {link.label}
          <ArrowRight size={12} />
        </Link>
      )}
    </article>
  )
}
