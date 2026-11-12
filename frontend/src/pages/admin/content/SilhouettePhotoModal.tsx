import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ExternalLink, ImageOff, Lock, RotateCcw, Trash2 } from 'lucide-react'
import { adminApi, type SilhouettePhotoChange, type SilhouettePhotoFollowUp, type SilhouetteQuestionPreview } from '@/api/admin'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { useToastStore } from '@/store/toastStore'
import { extractApiErrorMessage } from '@/utils/errors'

export const SILHOUETTE_QUESTIONS_QUERY_KEY = 'admin-silhouette-questions'

const QID_PATTERN = /^Q[1-9][0-9]*$/
const COMMONS_SEARCH_URL = 'https://commons.wikimedia.org/w/index.php?search='
const WIKIDATA_ITEM_URL = 'https://www.wikidata.org/wiki/'
const LANDMARK_CLUE = 'LANDMARK'

const followUpMessages: Record<SilhouettePhotoFollowUp, string> = {
  NONE: '',
  CLUES_REGENERATING: ', silhouette clues are being regenerated',
  SYNC_STARTED: ', the photo sync is choosing it',
}

type PhotoAction =
  | { kind: 'replace', fileTitle: string, landmarkQid: string | null }
  | { kind: 'remove' }
  | { kind: 'automatic' }

interface SilhouettePhotoModalProps {
  question: SilhouetteQuestionPreview
  onClose: () => void
}

export function SilhouettePhotoModal({ question, onClose }: SilhouettePhotoModalProps) {
  const queryClient = useQueryClient()
  const toast = useToastStore()
  const [fileTitle, setFileTitle] = useState('')
  const [landmarkQid, setLandmarkQid] = useState('')
  const [confirmRemove, setConfirmRemove] = useState(false)
  const qid = landmarkQid.trim().toUpperCase()
  const qidInvalid = qid.length > 0 && !QID_PATTERN.test(qid)
  const manual = question.photoSelection === 'MANUAL'
  const staleClue = (question.photoUrl != null) !== question.hints.some(hint => hint.kind === LANDMARK_CLUE)

  const mutation = useMutation({
    mutationFn: (action: PhotoAction): Promise<SilhouettePhotoChange> => {
      if (action.kind === 'replace') return adminApi.replaceSilhouettePhoto(question.countryCode, action.fileTitle, action.landmarkQid)
      if (action.kind === 'remove') return adminApi.removeSilhouettePhoto(question.countryCode)
      return adminApi.automaticSilhouettePhoto(question.countryCode)
    },
    onSuccess: (change, action) => {
      const message = action.kind === 'replace' ? `${question.name}: photo replaced`
        : action.kind === 'remove' ? `${question.name}: photo removed` : `${question.name}: back to the automatic choice`
      toast.addToast({ type: 'success', message: message + followUpMessages[change.followUp] })
      void queryClient.invalidateQueries({ queryKey: [SILHOUETTE_QUESTIONS_QUERY_KEY] })
      onClose()
    },
    onError: error => toast.addToast({ type: 'error', message: extractApiErrorMessage(error, 'The photo was not changed') }),
  })
  const busy = mutation.isPending

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!fileTitle.trim() || qidInvalid) return
    mutation.mutate({ kind: 'replace', fileTitle: fileTitle.trim(), landmarkQid: qid || null })
  }

  return <>
    <Modal isOpen onClose={onClose} title={`${question.name} landmark photo`} className="max-w-xl">
      <div className="space-y-4 overflow-y-auto px-5 pb-5 pt-1 sm:px-6 sm:pt-4">
        <div className="relative aspect-[16/10] overflow-hidden rounded-xl bg-[#090b14]">
          {question.photoUrl
            ? <img src={question.photoUrl} alt={`Landmark photo for ${question.name}`} className="h-full w-full object-cover" />
            : <div className="flex h-full flex-col items-center justify-center gap-2 text-xs text-white/35"><ImageOff size={22} /> No landmark photo</div>}
          <span className={`absolute right-3 top-3 flex items-center gap-1 rounded-full border px-2.5 py-1 text-[10px] font-semibold backdrop-blur ${manual ? 'border-amber-200/30 bg-[#2a1d06]/85 text-amber-100' : 'border-white/15 bg-black/65 text-white/70'}`}
            title={manual && question.photoSelectedBy ? `Chosen by ${question.photoSelectedBy}` : 'Chosen by the photo sync'}>
            {manual && <Lock size={10} aria-hidden="true" />} {manual ? 'Chosen by hand' : 'Automatic'}
          </span>
        </div>

        <dl className="grid gap-1 text-xs">
          <div className="flex justify-between gap-3">
            <dt className="text-white/40">Landmark</dt>
            <dd className="truncate text-right text-white/75">
              {question.landmarkName && question.landmarkQid
                ? <a href={`${WIKIDATA_ITEM_URL}${question.landmarkQid}`} target="_blank" rel="noopener noreferrer" className="hover:text-white">{question.landmarkName}</a>
                : question.photoUrl ? 'Not named on Commons' : '—'}
            </dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="text-white/40">Wikipedia editions</dt>
            <dd className="text-right text-white/75">{question.landmarkSitelinks?.toLocaleString() ?? '—'}</dd>
          </div>
          {question.photoUrl && <div className="flex justify-between gap-3">
            <dt className="text-white/40">Credit</dt>
            <dd className="min-w-0 truncate text-right text-white/75">
              {question.photoLicenseUrl
                ? <a href={question.photoLicenseUrl} target="_blank" rel="noopener noreferrer" className="hover:text-white">{question.photoAuthorName ?? 'Unknown author'}, {question.photoLicenseLabel}</a>
                : `${question.photoAuthorName ?? 'Unknown author'}, ${question.photoLicenseLabel ?? 'no licence'}`}
            </dd>
          </div>}
          {question.photoWidth != null && question.photoHeight != null && <div className="flex justify-between gap-3">
            <dt className="text-white/40">Original size</dt>
            <dd className="text-right text-white/75">{question.photoWidth} × {question.photoHeight}</dd>
          </div>}
        </dl>

        {staleClue && <p className="flex items-start gap-2 rounded-lg border border-amber-200/20 bg-amber-200/[.05] px-2.5 py-2 text-[11px] text-amber-100/80">
          <AlertTriangle size={13} className="mt-px shrink-0" aria-hidden="true" />
          {question.photoUrl ? 'The silhouette question has no landmark clue yet.' : 'The silhouette question still offers a landmark clue.'} Run Generate silhouette questions.
        </p>}

        <div className="flex flex-wrap gap-2">
          {question.photoSourcePageUrl && <a href={question.photoSourcePageUrl} target="_blank" rel="noopener noreferrer"
            className="flex h-8 items-center gap-1.5 rounded-lg border border-white/[.12] px-3 text-xs text-white/70 hover:border-white/[.22] hover:text-white">
            <ExternalLink size={12} aria-hidden="true" /> Commons
          </a>}
          {manual && <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => mutation.mutate({ kind: 'automatic' })}>
            <RotateCcw size={12} aria-hidden="true" /> Back to automatic
          </Button>}
          {question.photoUrl && <Button type="button" size="sm" variant="danger" disabled={busy} onClick={() => setConfirmRemove(true)}>
            <Trash2 size={12} aria-hidden="true" /> Remove
          </Button>}
        </div>

        <form onSubmit={submit} className="space-y-2 rounded-xl border border-white/[.08] bg-black/25 p-3">
          <p className="text-xs font-medium text-white/80">{question.photoUrl ? 'Replace the photo' : 'Add a photo'}</p>
          <label className="block text-[11px] text-white/55">
            Wikimedia Commons file
            <Input value={fileTitle} onChange={event => setFileTitle(event.target.value)} autoFocus
              placeholder="File:Example.jpg or a Commons link" disabled={busy} containerClassName="mt-1"
              className="h-9 px-3 py-0 text-sm" />
          </label>
          <label className="block text-[11px] text-white/55">
            Landmark on Wikidata <span className="text-white/30">(optional, taken from the file otherwise)</span>
            <Input value={landmarkQid} onChange={event => setLandmarkQid(event.target.value)} placeholder="Q243" disabled={busy}
              aria-invalid={qidInvalid} error={qidInvalid ? 'Use a Wikidata id such as Q243' : undefined}
              containerClassName="mt-1" className="h-9 px-3 py-0 text-sm" />
          </label>
          <p className="text-[10px] leading-relaxed text-white/35">JPEG, at least 800 px wide, with a free licence. It is downloaded, checked and stored in the media library.{' '}
            <a href={`${COMMONS_SEARCH_URL}${encodeURIComponent(question.name)}`} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2 hover:text-white/70">Search Commons</a>
          </p>
          <Button type="submit" size="sm" isLoading={busy && mutation.variables?.kind === 'replace'} disabled={busy || !fileTitle.trim() || qidInvalid}>Check and save</Button>
        </form>
      </div>
    </Modal>
    <ConfirmDialog isOpen={confirmRemove} onClose={() => setConfirmRemove(false)} variant="danger"
      title={`Remove the ${question.name} photo?`} confirmLabel="Remove"
      message="The country stays without a landmark photo and its landmark clue until you add one or return it to the automatic choice."
      isLoading={busy} onConfirm={() => { setConfirmRemove(false); mutation.mutate({ kind: 'remove' }) }} />
  </>
}
