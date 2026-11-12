import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CheckCircle2,
  ExternalLink,
  Flag,
  Image as ImageIcon,
  Loader2,
  Pencil,
  Save,
  SearchCheck,
  XCircle,
} from 'lucide-react'
import {
  budgetContentAdminApi,
  type BudgetCandidateKey,
  type BudgetContentReviewStatus,
  type BudgetContentUpdateInput,
} from '@/api/budgetContentAdmin'
import {
  BUDGET_CANDIDATE_EDIT_FORM_ID,
  BudgetCandidateEditForm,
} from '@/components/admin/content/BudgetCandidateEditForm'
import { isBudgetContentApproved } from '@/components/admin/content/budgetContentReviewStatus'
import { HintImageModal } from '@/components/game/HintImageModal'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { toast } from '@/store/toastStore'
import { extractApiErrorMessage } from '@/utils/errors'

interface BudgetCandidateInfoModalProps {
  candidate: BudgetCandidateKey | null
  onClose: () => void
  onReview: (status: BudgetContentReviewStatus) => void
  busy: boolean
}

const number = new Intl.NumberFormat('en-US')
const usd = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
})

function Value({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-white/[.07] bg-white/[.025] p-3">
      <p className="mb-1 text-[9px] font-semibold uppercase tracking-[.14em] text-white/25">{label}</p>
      <div className="break-words text-sm text-white/75">{value || '—'}</div>
    </div>
  )
}

export function BudgetCandidateInfoModal({
  candidate,
  onClose,
  onReview,
  busy,
}: BudgetCandidateInfoModalProps) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [editorMode, setEditorMode] = useState<'form' | 'json'>('form')
  const [previewImage, setPreviewImage] = useState<string | null>(null)
  const detailQuery = useQuery({
    queryKey: ['admin', 'budget-content', 'detail', candidate?.wikidataId, candidate?.category],
    queryFn: () => budgetContentAdminApi.getCandidate(candidate!),
    enabled: Boolean(candidate),
    staleTime: 30_000,
  })
  const detail = detailQuery.data
  const approved = detail ? isBudgetContentApproved(detail.reviewStatus) : false
  const close = () => {
    setEditing(false)
    setEditorMode('form')
    setPreviewImage(null)
    onClose()
  }
  const updateMutation = useMutation({
    mutationFn: (content: BudgetContentUpdateInput) =>
      budgetContentAdminApi.updateCandidate(candidate!, content),
    onSuccess: (result) => {
      toast.success(result.publishedQuestionId
        ? 'Editorial version saved and published'
        : 'Editorial version saved')
      setEditing(false)
      setEditorMode('form')
      void queryClient.invalidateQueries({ queryKey: ['admin', 'budget-content'] })
    },
    onError: (error) => toast.error(extractApiErrorMessage(error, 'Failed to save candidate')),
  })

  return (
    <Modal
      isOpen={Boolean(candidate)}
      onClose={close}
      title={detail?.name || 'Candidate details'}
      className="max-w-[1440px]"
      footer={
        detail && editing ? (
          <div key="edit-actions" className="flex justify-end gap-2">
            <Button
              type="button"
              variant="ghost"
              disabled={updateMutation.isPending}
              onClick={() => {
                setEditing(false)
                setEditorMode('form')
              }}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              form={BUDGET_CANDIDATE_EDIT_FORM_ID}
              disabled={updateMutation.isPending}
            >
              {editorMode === 'json' ? <SearchCheck size={14} /> : <Save size={14} />}
              {updateMutation.isPending
                ? 'Saving…'
                : editorMode === 'json'
                  ? 'Apply JSON and review'
                  : 'Save editorial version'}
            </Button>
          </div>
        ) : detail ? (
          <div key="review-actions" className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="ghost" size="sm" onClick={close}>Close</Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => {
                setEditorMode('form')
                setEditing(true)
              }}
            >
              <Pencil size={14} /> Edit data
            </Button>
            <Button
              type="button"
              variant="danger"
              size="sm"
              disabled={busy}
              onClick={() => onReview('REJECTED')}
            >
              <XCircle size={14} /> Reject
            </Button>
            {!approved && (
              <Button
                type="button"
                size="sm"
                disabled={busy}
                onClick={() => onReview('EDITORIAL_APPROVED')}
              >
                <CheckCircle2 size={14} /> Approve
              </Button>
            )}
          </div>
        ) : undefined
      }
    >
      {detailQuery.isLoading && (
        <div className="flex h-72 items-center justify-center text-white/35">
          <Loader2 size={24} className="animate-spin" />
        </div>
      )}

      {detailQuery.isError && (
        <div className="rounded-xl border border-red-400/20 bg-red-400/[.07] p-5 text-sm text-red-300">
          Failed to load candidate details.
        </div>
      )}

      {detail && editing && (
        <BudgetCandidateEditForm
          key={`${detail.wikidataId}:${detail.category}`}
          candidate={detail}
          saving={updateMutation.isPending}
          onModeChange={setEditorMode}
          onSave={(input) => updateMutation.mutate(input)}
        />
      )}

      {detail && !editing && (
        <div className="space-y-6">
          <div className="grid gap-5 lg:grid-cols-[1.25fr_.75fr]">
            <div className="overflow-hidden rounded-2xl border border-white/[.08] bg-black/20">
              {detail.image.url ? (
                <button
                  type="button"
                  aria-label={`Open ${detail.name} image`}
                  onClick={() => setPreviewImage(detail.image.url)}
                  className="block w-full cursor-zoom-in bg-black/20"
                >
                  <img
                    src={`${detail.image.url}?thumbnail=true`}
                    alt={detail.image.description || detail.name}
                    className="mx-auto max-h-[480px] w-full object-contain"
                  />
                </button>
              ) : (
                <div className="flex aspect-video items-center justify-center text-white/20">
                  <ImageIcon size={36} />
                </div>
              )}
              <div className="flex flex-wrap items-center justify-between gap-2 border-t border-white/[.06] px-4 py-3 text-[11px] text-white/35">
                <span>{detail.image.authorName} · {detail.image.licenseLabel}</span>
                <a
                  href={detail.image.sourcePageUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-1 hover:text-white/70"
                >
                  Wikimedia source <ExternalLink size={11} />
                </a>
              </div>
            </div>

            <div className="space-y-3">
              <div className="rounded-2xl border border-white/[.08] bg-white/[.025] p-4">
                <p className="text-[10px] font-semibold uppercase tracking-[.15em] text-emerald-300/70">
                  Editorial status
                </p>
                <p className="mt-2 text-lg font-semibold text-white/90">
                  {detail.reviewStatus.replaceAll('_', ' ')}
                </p>
                {detail.reviewedBy && (
                  <p className="mt-1 text-xs text-white/35">
                    {detail.reviewedBy} · {detail.reviewedAt ? new Date(detail.reviewedAt).toLocaleString() : ''}
                  </p>
                )}
                {detail.reviewNote && <p className="mt-3 text-sm text-white/55">{detail.reviewNote}</p>}
              </div>

              <div className="grid grid-cols-2 gap-2">
                <Value label="Quality" value={`${detail.qualityScore}/100`} />
                <Value label="Image quality" value={`${detail.image.qualityScore}/100`} />
                <Value label="Pageviews / month" value={number.format(detail.monthlyPageviews)} />
                <Value label="Sitelinks" value={number.format(detail.sitelinkCount)} />
                <Value label="Interest" value={`${detail.interestScore}/100`} />
                <Value label="Source rank" value={detail.sourceRank} />
                <Value label="Cost confidence" value={`${detail.costConfidenceScore}/100`} />
                <Value label="Evidence tier" value={detail.evidenceTier.replaceAll('_', ' ')} />
              </div>
            </div>
          </div>

          <section>
            <p className="mb-2 text-[10px] font-semibold uppercase tracking-[.16em] text-white/28">Description</p>
            <p className="whitespace-pre-wrap text-sm leading-6 text-white/65">
              {detail.description || 'No useful description was returned by Wikidata.'}
            </p>
          </section>

          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
            <Value label="Wikidata" value={detail.wikidataId} />
            <Value label="Category" value={detail.category.replaceAll('_', ' ')} />
            <Value label="Difficulty" value={detail.difficulty} />
            <Value label="Year" value={detail.projectYear} />
            <Value label="Country" value={`${detail.countryName} (${detail.countryCode})`} />
            <Value label="Region" value={detail.regionCode.replaceAll('_', ' ')} />
            <Value label="Image size" value={`${number.format(detail.image.width)} × ${number.format(detail.image.height)}`} />
            <Value label="File size" value={`${(detail.image.sourceBytes / 1_000_000).toFixed(2)} MB`} />
          </div>

          <section className="rounded-2xl border border-white/[.08] bg-white/[.02] p-4">
            <div className="mb-3 flex items-center gap-2">
              <SearchCheck size={16} className="text-emerald-300" />
              <h3 className="text-sm font-semibold">Cost source</h3>
            </div>
            {detail.cost ? (
              <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                <Value label="Answer cost (USD)" value={usd.format(detail.cost.answerCostUsd)} />
                <Value label="Source amount" value={`${number.format(detail.cost.sourceAmount)} ${detail.cost.sourceCurrency}`} />
                <Value label="Cost year" value={detail.cost.costYear} />
                <div className="sm:col-span-2 lg:col-span-4">
                  <Value label="Normalization" value={detail.cost.normalizationNote} />
                </div>
                <a
                  href={detail.cost.referenceUrl || detail.cost.sourceUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-1 text-xs text-emerald-300 hover:text-emerald-200 sm:col-span-2 lg:col-span-4"
                >
                  {detail.cost.sourceName} <ExternalLink size={11} />
                </a>
              </div>
            ) : (
              <div className="flex items-start gap-3 rounded-xl border border-amber-400/15 bg-amber-400/[.05] p-3 text-sm text-amber-200/65">
                <Flag size={16} className="mt-0.5 shrink-0" />
                This candidate has no usable normalized cost and cannot be published.
              </div>
            )}
          </section>

          {detail.issues.length > 0 && (
            <section>
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-[.16em] text-amber-300/60">
                {approved ? 'Accepted quality warnings' : 'Quality flags'}
              </p>
              <div className="flex flex-wrap gap-2">
                {detail.issues.map((issue) => (
                  <span key={issue} className="rounded-lg border border-amber-400/15 bg-amber-400/[.06] px-2.5 py-1.5 text-xs text-amber-200/70">
                    {issue.replaceAll('_', ' ')}
                  </span>
                ))}
              </div>
            </section>
          )}

          {detail.qualityFlags.length > 0 && (
            <section>
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-[.16em] text-amber-300/60">
                {approved ? 'Accepted pipeline warnings' : 'Pipeline quality gate'}
              </p>
              <div className="flex flex-wrap gap-2">
                {detail.qualityFlags.map((flag) => (
                  <span key={flag} className="rounded-lg border border-amber-400/15 bg-amber-400/[.06] px-2.5 py-1.5 text-xs text-amber-200/70">
                    {flag.replaceAll('_', ' ')}
                  </span>
                ))}
              </div>
            </section>
          )}

          <section className="grid gap-2 sm:grid-cols-2">
            <a href={detail.sourceUrl} target="_blank" rel="noreferrer" className="rounded-xl border border-white/[.08] p-3 text-sm text-white/55 hover:bg-white/[.04] hover:text-white/80">
              Wikidata entity <ExternalLink size={12} className="ml-1 inline" />
            </a>
            <a href={detail.image.originalUrl} target="_blank" rel="noreferrer" className="rounded-xl border border-white/[.08] p-3 text-sm text-white/55 hover:bg-white/[.04] hover:text-white/80">
              Original image <ExternalLink size={12} className="ml-1 inline" />
            </a>
          </section>

          {previewImage && (
            <HintImageModal
              src={previewImage}
              isOpen
              isPanorama={false}
              onClose={() => setPreviewImage(null)}
            />
          )}

        </div>
      )}
    </Modal>
  )
}
