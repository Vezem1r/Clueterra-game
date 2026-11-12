import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ExternalLink, FileText, Globe2, ShieldCheck } from 'lucide-react'
import { GameAuroraBackdrop } from '@/components/game/GameAuroraBackdrop'
import { Header } from '@/components/layout/Header'
import { HomeLegalStrip } from '@/pages/game/home/HomeLegalStrip'
import { legalApi } from '@/api/legal'
import { useSeoMeta } from '@/hooks/useSeoMeta'
import { useTranslation } from '@/i18n'
import type { PublicDataSourceItem } from '@/types'

function SourceLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-1.5 text-sm text-accent hover:underline"
    >
      {children}
      <ExternalLink size={14} />
    </a>
  )
}

function SourceCard({ source }: { source: PublicDataSourceItem }) {
  return (
    <section
      key={source.slug}
      className="rounded-3xl border border-white/[.08] bg-white/[.03] p-5 sm:p-6 shadow-[0_20px_60px_rgba(0,0,0,0.18)]"
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="inline-flex items-center gap-2 text-white">
            <Globe2 size={16} className="text-sky-300/80" />
            <h2 className="text-lg font-semibold">{source.name}</h2>
          </div>
          <p className="mt-2 text-sm text-white/55">{source.summary}</p>
        </div>

        {source.licenseLabel && (
          <span className="inline-flex w-fit items-center rounded-full border border-white/[.08] bg-white/[.04] px-3 py-1 text-[11px] uppercase tracking-[.16em] text-white/42">
            {source.licenseLabel}
          </span>
        )}
      </div>

      <div className="mt-4 rounded-2xl border border-white/[.06] bg-black/15 px-4 py-3">
        <p className="text-sm text-white/74">{source.notice}</p>
        {source.note && (
          <p className="mt-2 text-xs leading-relaxed text-white/40">{source.note}</p>
        )}
      </div>

      {(source.sourceUrl || source.licenseUrl) && (
        <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2">
          {source.sourceUrl && source.sourceLabel && (
            <SourceLink href={source.sourceUrl}>{source.sourceLabel}</SourceLink>
          )}
          {source.licenseUrl && source.licenseLabel && (
            <SourceLink href={source.licenseUrl}>{source.licenseLabel}</SourceLink>
          )}
        </div>
      )}
    </section>
  )
}

export function DataSourcesPage() {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useQuery({
    queryKey: ['legal-data-sources'],
    queryFn: legalApi.getDataSources,
    staleTime: 1000 * 60 * 30,
  })

  useSeoMeta({
    title: t.dataSourcesPage.seoTitle,
    description: t.dataSourcesPage.seoDescription,
    canonicalPath: '/data-sources',
    keywords: 'Clueterra data sources, Clueterra licenses, source attribution, provider registry',
  })

  return (
    <div className="min-h-screen bg-background text-white/80">
      <GameAuroraBackdrop variant="light" />
      <Header />

      <main className="relative z-10 px-4 pb-24 pt-6 sm:px-6 md:pb-28 lg:px-10">
        <div className="mx-auto max-w-6xl">
          <section className="rounded-[2.25rem] border border-white/[.08] bg-[linear-gradient(180deg,rgba(255,255,255,.045),rgba(9,12,20,.86))] px-5 py-6 shadow-[0_24px_80px_rgba(0,0,0,.22)] backdrop-blur-2xl sm:px-7 sm:py-8 lg:px-8">
            <div className="max-w-3xl">
              <div className="inline-flex items-center gap-2 rounded-full border border-white/[.08] bg-white/[.04] px-3 py-1.5 text-[11px] uppercase tracking-[.18em] text-white/38">
                <ShieldCheck size={13} />
                {t.dataSourcesPage.badge}
              </div>

              <h1 className="mt-5 text-3xl font-bold tracking-tight text-white sm:text-4xl lg:text-5xl">
                {t.dataSourcesPage.title}
              </h1>

              <p className="mt-4 text-sm leading-relaxed text-white/55 sm:text-base">
                {t.dataSourcesPage.description}
              </p>
            </div>

            <div className="mt-6 grid gap-3 sm:grid-cols-3">
              <InfoTile label={t.dataSourcesPage.providersLabel} value={data ? String(data.sources.length) : '—'} />
              <InfoTile label={t.dataSourcesPage.purposeLabel} value={t.dataSourcesPage.purposeValue} />
              <InfoTile label={t.dataSourcesPage.visibilityLabel} value={t.dataSourcesPage.visibilityValue} />
            </div>
          </section>

          <section className="mt-6 rounded-[2rem] border border-white/[.08] bg-white/[.02] p-5 shadow-[0_20px_60px_rgba(0,0,0,.16)] backdrop-blur-xl sm:p-6">
            <h2 className="text-lg font-semibold text-white">{t.dataSourcesPage.noteTitle}</h2>
            <p className="mt-3 text-sm leading-relaxed text-white/55">
              {t.dataSourcesPage.noteBody}
            </p>
          </section>
          <div className="mt-6 grid gap-4">
            <div className="max-w-3xl">
              <div className="inline-flex items-center gap-2 rounded-full border border-white/[.08] bg-white/[.04] px-3 py-1.5 text-[11px] uppercase tracking-[.18em] text-white/38">
                <FileText size={13} />
                {t.dataSourcesPage.registryLabel}
              </div>
            </div>

            {isLoading && (
              <div className="rounded-[2rem] border border-white/[.08] bg-white/[.03] px-5 py-10 text-center shadow-[0_20px_60px_rgba(0,0,0,.18)] backdrop-blur-xl">
                <div className="mx-auto h-5 w-5 rounded-full border-2 border-white/20 border-t-white/60 animate-spin" />
                <p className="mt-4 text-sm text-white/45">{t.dataSourcesPage.loading}</p>
              </div>
            )}

            {isError && (
              <div className="rounded-[2rem] border border-white/[.08] bg-white/[.03] px-5 py-10 text-center shadow-[0_20px_60px_rgba(0,0,0,.18)] backdrop-blur-xl">
                <p className="text-sm text-white/55">
                  {t.dataSourcesPage.error}
                </p>
              </div>
            )}

            {data?.sources.map((source) => (
              <SourceCard key={source.slug} source={source} />
            ))}
          </div>

        </div>
      </main>

      <HomeLegalStrip />
    </div>
  )
}

function InfoTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[1.5rem] border border-white/[.08] bg-black/18 px-4 py-4">
      <p className="text-[11px] uppercase tracking-[.18em] text-white/25">{label}</p>
      <p className="mt-2 text-lg font-semibold text-white">{value}</p>
    </div>
  )
}
