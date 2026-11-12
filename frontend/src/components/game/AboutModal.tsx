import { BookOpen, BookOpenCheck, Compass, Database, Sparkles } from 'lucide-react'
import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { Modal } from '@/components/ui/Modal'
import { useTranslation } from '@/i18n'

interface AboutModalProps {
  isOpen: boolean
  onClose: () => void
}

export function AboutModal({ isOpen, onClose }: AboutModalProps) {
  const { t } = useTranslation()
  const highlights = [
    {
      icon: Compass,
      title: t.aboutPage.missionTitle,
      body: t.aboutPage.missionBody,
      accent: 'text-sky-300',
      glow: 'bg-sky-300/10',
    },
    {
      icon: BookOpenCheck,
      title: t.aboutPage.sourcesTitle,
      body: t.aboutPage.sourcesBody,
      accent: 'text-emerald-300',
      glow: 'bg-emerald-300/10',
    },
    {
      icon: Sparkles,
      title: t.aboutPage.progressTitle,
      body: t.aboutPage.progressBody,
      accent: 'text-violet-300',
      glow: 'bg-violet-300/10',
    },
  ]

  return (
    <Modal
      ariaLabel={t.footer.aboutLink}
      isOpen={isOpen}
      onClose={onClose}
      title={t.footer.aboutLink}
      className="max-w-3xl"
    >
      <div className="flex flex-col gap-4 sm:gap-5">
        <section className="relative isolate overflow-hidden rounded-[1.75rem] border border-white/[.08] bg-[linear-gradient(145deg,rgba(22,29,48,.9),rgba(8,11,19,.96))] px-5 py-7 sm:px-7 sm:py-8">
          <div className="pointer-events-none absolute -right-16 -top-20 h-56 w-56 rounded-full border border-sky-300/[.08]" />
          <div className="pointer-events-none absolute -right-8 -top-12 h-40 w-40 rounded-full border border-violet-300/[.1]" />
          <div className="pointer-events-none absolute right-8 top-8 h-24 w-24 rounded-full bg-[radial-gradient(circle,rgba(125,211,252,.13),transparent_67%)] blur-xl" />

          <div className="relative max-w-2xl">
            <motion.div
              initial={{ opacity: 0, scale: 0.92 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.35 }}
              className="mb-5 flex h-11 w-11 items-center justify-center rounded-2xl border border-sky-300/15 bg-sky-300/[.08] text-sky-300 shadow-[0_0_35px_rgba(125,211,252,.08)]"
            >
              <Compass size={21} />
            </motion.div>
            <p className="text-[10px] font-bold uppercase tracking-[.24em] text-emerald-300/65">
              {t.aboutPage.eyebrow}
            </p>
            <h2 className="mt-3 max-w-xl text-2xl font-black tracking-[-.035em] text-white sm:text-3xl">
              {t.aboutPage.title}
            </h2>
            <p className="mt-4 max-w-2xl text-sm leading-6 text-white/52 sm:text-[15px] sm:leading-7">
              {t.aboutPage.intro}
            </p>
          </div>
        </section>

        <div className="grid gap-2.5 md:grid-cols-3">
          {highlights.map(({ accent, body, glow, icon: Icon, title }, index) => (
            <motion.article
              key={title}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.08 + index * 0.06, duration: 0.3 }}
              className="rounded-2xl border border-white/[.07] bg-white/[.025] p-4 sm:p-5"
            >
              <span className={`flex h-9 w-9 items-center justify-center rounded-xl ${glow} ${accent}`}>
                <Icon size={17} />
              </span>
              <h3 className="mt-4 text-sm font-semibold leading-5 text-white/82">{title}</h3>
              <p className="mt-2 text-xs leading-5 text-white/40">{body}</p>
            </motion.article>
          ))}
        </div>

        <div className="flex flex-col gap-2.5 border-t border-white/[.06] pt-4 sm:flex-row sm:items-center">
          <Link
            to="/"
            onClick={onClose}
            className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-2xl bg-white px-5 text-sm font-semibold text-black transition-colors hover:bg-white/90"
          >
            <Compass size={16} />
            {t.aboutPage.playCta}
          </Link>
          <Link
            to="/about"
            onClick={onClose}
            className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-2xl border border-white/[.08] bg-white/[.04] px-4 text-sm font-medium text-white/65 transition-colors hover:bg-white/[.08] hover:text-white"
          >
            <BookOpen size={16} />
            {t.aboutPage.fullPageCta}
          </Link>
          <Link
            to="/data-sources"
            onClick={onClose}
            className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-2xl px-3 text-sm text-white/38 transition-colors hover:bg-white/[.04] hover:text-white/65 sm:ml-auto"
          >
            <Database size={15} />
            {t.aboutPage.sourcesCta}
          </Link>
        </div>
      </div>
    </Modal>
  )
}
