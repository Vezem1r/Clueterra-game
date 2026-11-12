import {
  Check,
  CheckCircle2,
  CircleDollarSign,
  Eye,
  ExternalLink,
  Flag,
  X,
} from 'lucide-react'
import type {
  BudgetContentCandidate,
  BudgetContentReviewStatus,
} from '@/api/budgetContentAdmin'
import { isBudgetContentApproved } from '@/components/admin/content/budgetContentReviewStatus'
import { cn } from '@/utils/cn'

interface BudgetCandidateCardProps {
  candidate: BudgetContentCandidate
  selected: boolean
  busy: boolean
  onToggle: () => void
  onInfo: () => void
  onReview: (status: BudgetContentReviewStatus) => void
}

const statusStyles: Record<string, string> = {
  DISCOVERED: 'border-sky-400/20 bg-sky-400/10 text-sky-300',
  NEEDS_REVIEW: 'border-amber-400/20 bg-amber-400/10 text-amber-300',
  NEEDS_IMAGE: 'border-orange-400/20 bg-orange-400/10 text-orange-300',
  EDITORIAL_APPROVED: 'border-emerald-400/20 bg-emerald-400/10 text-emerald-300',
  APPROVED: 'border-emerald-400/25 bg-emerald-400/15 text-emerald-200',
  REJECTED: 'border-red-400/20 bg-red-400/10 text-red-300',
}
const compactUsd = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 2,
})
const compactNumber = new Intl.NumberFormat('en-US', { notation: 'compact' })

function formatCost(amount?: number | null) {
  if (!amount) return 'Needs cost evidence'
  return compactUsd.format(amount)
}

export function BudgetCandidateCard({
  candidate,
  selected,
  busy,
  onToggle,
  onInfo,
  onReview,
}: BudgetCandidateCardProps) {
  const approved = isBudgetContentApproved(candidate.reviewStatus)
  const stop = (callback: () => void) => (event: React.MouseEvent) => {
    event.stopPropagation()
    callback()
  }

  return (
    <article
      role="checkbox"
      aria-checked={selected}
      tabIndex={0}
      onClick={onToggle}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onToggle()
        }
      }}
      className={cn(
        'group relative cursor-pointer overflow-hidden rounded-2xl border bg-[#0d0f16] outline-none transition-all',
        '[content-visibility:auto] [contain-intrinsic-size:420px]',
        selected
          ? 'border-emerald-400/55 ring-2 ring-emerald-400/15'
          : 'border-white/[.08] hover:border-white/[.16] hover:-translate-y-0.5',
      )}
    >
      <div className="relative isolate aspect-[16/10] overflow-hidden bg-[#0d0f16]">
        {candidate.image.url ? (
          <img
            src={`${candidate.image.url}?thumbnail=true`}
            alt={candidate.image.description || candidate.name}
            loading="lazy"
            decoding="async"
            className="absolute -inset-px block h-[calc(100%+2px)] w-[calc(100%+2px)] max-w-none transform-gpu object-cover transition-transform duration-500 [backface-visibility:hidden] will-change-transform group-hover:scale-[1.025]"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-white/20">
            <CircleDollarSign size={30} />
          </div>
        )}
        <div className="absolute -inset-x-px -bottom-px h-[calc(6rem+1px)] bg-gradient-to-t from-[#0d0f16] to-transparent" />

        <div
          className={cn(
            'absolute left-3 top-3 flex h-7 w-7 items-center justify-center rounded-lg border backdrop-blur-md transition-colors',
            selected
              ? 'border-emerald-300/50 bg-emerald-400 text-black'
              : 'border-white/20 bg-black/45 text-transparent group-hover:text-white/35',
          )}
        >
          <Check size={15} strokeWidth={3} />
        </div>

        <div className="absolute right-3 top-3 flex items-center gap-1.5">
          {candidate.issues.length > 0 && (
            <span className="flex h-7 items-center gap-1 rounded-lg border border-amber-400/20 bg-black/55 px-2 text-[10px] font-semibold text-amber-300 backdrop-blur-md">
              <Flag size={11} /> {candidate.issues.length}
            </span>
          )}
          <span
            className={cn(
              'rounded-lg border px-2 py-1 text-[9px] font-bold uppercase tracking-wider backdrop-blur-md',
              statusStyles[candidate.reviewStatus] || 'border-white/15 bg-black/50 text-white/60',
            )}
          >
            {candidate.reviewStatus.replaceAll('_', ' ')}
          </span>
        </div>
      </div>

      <div className="relative z-10 -mt-px space-y-3 bg-[#0d0f16] p-4 pt-3">
        <div>
          <div className="mb-1 flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[.14em] text-white/32">
            <span>{candidate.category.replaceAll('_', ' ')}</span>
            <span className="h-1 w-1 rounded-full bg-white/20" />
            <span>{candidate.difficulty}</span>
          </div>
          <h3 className="line-clamp-2 min-h-12 text-[16px] font-semibold leading-6 tracking-[-.01em] text-white/90">
            {candidate.name}
          </h3>
        </div>

        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0">
            <p className="truncate text-xs text-white/48">
              {candidate.countryName} · {candidate.projectYear || 'Year unknown'}
            </p>
            <p className={cn('mt-1 text-sm font-semibold', candidate.cost ? 'text-emerald-300' : 'text-amber-300/80')}>
              {formatCost(candidate.cost?.answerCostUsd)}
            </p>
          </div>
          <div className="shrink-0 text-right">
            <p className="text-sm font-semibold text-white/75">{candidate.qualityScore}</p>
            <p className="text-[9px] uppercase tracking-wider text-white/25">quality</p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-1.5 text-center">
          <div className="rounded-lg bg-white/[.025] px-2 py-1.5">
            <p className="text-[11px] font-medium text-white/60">{candidate.sitelinkCount}</p>
            <p className="text-[8px] uppercase tracking-wider text-white/22">sitelinks</p>
          </div>
          <div className="rounded-lg bg-white/[.025] px-2 py-1.5">
            <p className="text-[11px] font-medium text-white/60">
              {compactNumber.format(candidate.monthlyPageviews)}
            </p>
            <p className="text-[8px] uppercase tracking-wider text-white/22">views/mo</p>
          </div>
          <div className="rounded-lg bg-white/[.025] px-2 py-1.5">
            <p className="text-[11px] font-medium text-white/60">{candidate.image.qualityScore}</p>
            <p className="text-[8px] uppercase tracking-wider text-white/22">image</p>
          </div>
        </div>

        <div className={cn(
          'grid gap-1.5 border-t border-white/[.06] pt-3',
          approved ? 'grid-cols-2' : 'grid-cols-3',
        )}>
          <button
            type="button"
            disabled={busy}
            onClick={stop(onInfo)}
            className="flex h-8 items-center justify-center gap-1 rounded-lg bg-white/[.05] text-[11px] text-white/55 transition-colors hover:bg-white/[.09] hover:text-white disabled:opacity-40"
          >
            <Eye size={13} /> Info
          </button>
          {!approved && (
            <button
              type="button"
              disabled={busy}
              onClick={stop(() => onReview('EDITORIAL_APPROVED'))}
              className="flex h-8 items-center justify-center gap-1 rounded-lg bg-emerald-400/10 text-[11px] text-emerald-300 transition-colors hover:bg-emerald-400/20 disabled:opacity-40"
            >
              <CheckCircle2 size={13} /> Approve
            </button>
          )}
          <button
            type="button"
            disabled={busy}
            onClick={stop(() => onReview('REJECTED'))}
            className="flex h-8 items-center justify-center gap-1 rounded-lg bg-red-400/[.08] text-[11px] text-red-300/80 transition-colors hover:bg-red-400/15 hover:text-red-300 disabled:opacity-40"
          >
            <X size={13} /> Reject
          </button>
        </div>

        <a
          href={candidate.sourceUrl}
          target="_blank"
          rel="noreferrer"
          onClick={(event) => event.stopPropagation()}
          className="flex items-center gap-1 text-[10px] text-white/25 transition-colors hover:text-white/55"
        >
          {candidate.wikidataId} <ExternalLink size={10} />
        </a>
      </div>
    </article>
  )
}
