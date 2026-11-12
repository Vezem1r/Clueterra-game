import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowLeft, BookOpen, MapPin } from 'lucide-react'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { Header } from '@/components/layout/Header'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'

export function NotFoundPage() {
  const { t } = useTranslation()

  useSeoMeta({
    title: '404 | Clueterra',
    description: t.notFoundPage.description,
    canonicalPath: null,
    robots: 'noindex,nofollow',
  })

  return (
    <div className="flex min-h-[100dvh] flex-col overflow-x-hidden bg-background text-white">
      <GameAuroraBackdrop variant="play" />
      <Header />

      <main className="relative z-10 flex flex-1 items-center justify-center px-4 pb-24 pt-14 sm:px-6 md:pb-28">
        <motion.section
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="w-full max-w-3xl rounded-[2.5rem] border border-white/[.09] bg-[linear-gradient(155deg,rgba(14,18,30,.94),rgba(7,9,16,.98))] px-6 py-12 text-center shadow-[0_40px_120px_rgba(0,0,0,.4)] sm:px-10 sm:py-16"
        >
          <motion.div
            initial={{ scale: 0.8, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: 'spring', delay: 0.08, stiffness: 180 }}
            className="mx-auto flex h-20 w-20 items-center justify-center rounded-[1.6rem] border border-violet-300/15 bg-violet-400/[.08] shadow-[0_20px_60px_rgba(124,92,252,.12)]"
          >
            <MapPin size={32} className="text-violet-300/65" />
          </motion.div>

          <p className="mt-7 font-display text-sm font-bold tracking-[.32em] text-white/20">404</p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight text-white sm:text-4xl">
            {t.notFoundPage.title}
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-sm leading-7 text-white/45 sm:text-base">
            {t.notFoundPage.description}
          </p>

          <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
            <Link
              to="/"
              className="inline-flex items-center justify-center gap-2 rounded-2xl bg-white px-5 py-3 text-sm font-semibold text-black transition-colors hover:bg-white/90"
            >
              <ArrowLeft size={15} />
              {t.notFoundPage.homeCta}
            </Link>
            <Link
              to="/how-to"
              className="inline-flex items-center justify-center gap-2 rounded-2xl border border-white/[.1] bg-white/[.04] px-5 py-3 text-sm font-medium text-white/70 transition-colors hover:bg-white/[.08] hover:text-white"
            >
              <BookOpen size={15} />
              {t.notFoundPage.guideCta}
            </Link>
          </div>
        </motion.section>
      </main>

      <HomeLegalStrip />
    </div>
  )
}
