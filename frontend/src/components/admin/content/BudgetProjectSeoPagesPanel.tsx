import { useDeferredValue, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertCircle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  Eye,
  FilePenLine,
  FilePlus2,
  Globe2,
  Loader2,
  Plus,
  Search,
  ShieldCheck,
  TriangleAlert,
  Trash2,
  X,
} from 'lucide-react'
import {
  budgetContentAdminApi,
  type BudgetProjectSeoCandidate,
  type BudgetProjectSeoCandidateStatus,
  type BudgetProjectSeoFactInput,
  type BudgetProjectSeoPage,
  type BudgetProjectSeoPageInput,
  type BudgetProjectSeoReferenceInput,
  type BudgetProjectSeoStatus,
} from '@/api/budgetContentAdmin'
import { Button } from '@/components/ui/Button'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { useDebounce } from '@/hooks/useDebounce'
import { toast } from '@/store/toastStore'
import { extractApiErrorMessage } from '@/utils/errors'
import { cn } from '@/utils/cn'

const EMPTY_FORM: BudgetProjectSeoPageInput = {
  slug: '',
  costQuestion: '',
  seoTitle: '',
  metaDescription: '',
  keywords: '',
  intro: '',
  answerDetail: '',
  scopeText: '',
  contextText: '',
  methodologyText: '',
  sourceName: '',
  sourceUrl: '',
  references: [],
  facts: [],
  publicationStatus: 'DRAFT',
}

interface SeoEditorState {
  wikidataId: string
  projectName: string
  existing: BudgetProjectSeoPage | null
}

export function BudgetProjectSeoPagesPanel() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [publicationStatus, setPublicationStatus] = useState('')
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState<SeoEditorState | null>(null)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [form, setForm] = useState<BudgetProjectSeoPageInput>(EMPTY_FORM)

  const deferredSearch = useDeferredValue(search.trim())
  const debouncedForm = useDebounce(form, 450)
  const pagesQuery = useQuery({
    queryKey: ['admin-budget-project-seo-pages', deferredSearch, publicationStatus, page],
    queryFn: () => budgetContentAdminApi.getSeoPages({
      page,
      size: 24,
      search: deferredSearch || undefined,
      publicationStatus: publicationStatus
        ? publicationStatus as BudgetProjectSeoStatus
        : undefined,
    }),
  })

  const validationQuery = useQuery({
    queryKey: ['admin-budget-project-seo-validation', editor?.wikidataId, debouncedForm],
    queryFn: () => budgetContentAdminApi.validateSeoPage(editor!.wikidataId, {
      ...debouncedForm,
      publicationStatus: 'DRAFT',
    }),
    enabled: editor !== null,
    retry: false,
  })

  const pages = pagesQuery.data?.content ?? []

  const saveMutation = useMutation({
    mutationFn: ({
      wikidataId,
      nextStatus,
    }: {
      wikidataId: string
      nextStatus: BudgetProjectSeoStatus
    }) => budgetContentAdminApi.saveSeoPage(wikidataId, { ...form, publicationStatus: nextStatus }),
    onSuccess: async (saved) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-budget-project-seo-pages'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-budget-project-seo-candidates'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-budget-project-seo-validation'] }),
      ])
      setForm(toInput(saved))
      setEditor({
        wikidataId: saved.wikidataId,
        projectName: saved.projectName || saved.seoTitle || saved.wikidataId,
        existing: saved,
      })
      toast.success(saved.publicationStatus === 'PUBLISHED' ? 'Project page published' : 'SEO draft saved')
    },
    onError: (error) => toast.error(extractApiErrorMessage(error, 'Failed to save the project page')),
  })

  const openCandidate = async (candidate: BudgetProjectSeoCandidate) => {
    try {
      if (candidate.publicationStatus) {
        const result = await budgetContentAdminApi.getSeoPages({ search: candidate.wikidataId, size: 10 })
        const existing = result.content.find((item) => item.wikidataId === candidate.wikidataId)
        if (!existing) throw new Error('The existing SEO page could not be loaded')
        setForm(toInput(existing))
        setEditor({ wikidataId: candidate.wikidataId, projectName: candidate.projectName, existing })
      } else {
        const suggestion = await budgetContentAdminApi.getSeoDraftSuggestion(candidate.wikidataId)
        setForm({ ...suggestion, publicationStatus: 'DRAFT' })
        setEditor({ wikidataId: candidate.wikidataId, projectName: candidate.projectName, existing: null })
      }
      setPickerOpen(false)
    } catch (error) {
      toast.error(extractApiErrorMessage(error, 'Failed to open this reviewed project'))
    }
  }

  const update = (
    field: Exclude<keyof BudgetProjectSeoPageInput, 'references' | 'facts' | 'publicationStatus'>,
    value: string,
  ) => {
    setForm((current) => ({ ...current, [field]: value }))
  }

  const save = (nextStatus: BudgetProjectSeoStatus) => {
    if (!editor || saveMutation.isPending) return
    saveMutation.mutate({ wikidataId: editor.wikidataId, nextStatus })
  }

  const saveDraft = () => {
    if (editor?.existing?.publicationStatus === 'PUBLISHED'
      && !window.confirm('Unpublish this page and move it back to drafts? It will leave the public catalogue and sitemap.')) {
      return
    }
    save('DRAFT')
  }

  const validation = validationQuery.data
  const publishDisabled = saveMutation.isPending
    || validationQuery.isFetching
    || !validation?.publishable

  return (
    <div className="space-y-5">
      <section className="rounded-2xl border border-white/[.08] bg-white/[.025] p-4 sm:p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-[10px] font-semibold uppercase tracking-[.14em] text-emerald-300/55">Search publication</p>
            <h2 className="mt-1 text-xl font-semibold tracking-tight text-white/85">Project SEO pages</h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-white/38">
              Choose an active reviewed project. Drafts stay private; publishing adds the server-rendered page to the catalogue and sitemap.
            </p>
          </div>
          <Button variant="secondary" onClick={() => setPickerOpen(true)}>
            <FilePlus2 size={14} /> Choose reviewed project
          </Button>
        </div>
      </section>

      <div className="flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
          <input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
            placeholder="Search SEO pages"
            className="h-10 w-full rounded-xl border border-white/[.08] bg-white/[.025] pl-9 pr-3 text-sm text-white outline-none placeholder:text-white/22 focus:border-emerald-400/35"
          />
        </div>
        <CustomSelect
          value={publicationStatus}
          onChange={(value) => {
            setPublicationStatus(value)
            setPage(0)
          }}
          options={[
            { value: '', label: 'All statuses' },
            { value: 'PUBLISHED', label: 'Published' },
            { value: 'DRAFT', label: 'Drafts' },
          ]}
        />
      </div>

      {pagesQuery.isLoading && <LoadingBlock />}
      {pagesQuery.isError && <ErrorBlock message="Could not load project SEO pages." />}
      {!pagesQuery.isLoading && !pagesQuery.isError && pages.length === 0 && (
        <EmptyBlock message="No project SEO pages match these filters." />
      )}

      {!pagesQuery.isLoading && !pagesQuery.isError && pages.length > 0 && (
        <>
          <div className="grid gap-3 lg:grid-cols-2">
            {pages.map((projectPage) => (
              <article key={projectPage.wikidataId} className="rounded-2xl border border-white/[.075] bg-white/[.022] p-4 sm:p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusPill status={projectPage.publicationStatus} />
                      <span className="text-[10px] text-white/28">{projectPage.wikidataId}</span>
                    </div>
                    <h3 className="mt-3 truncate text-base font-semibold text-white/82">{projectPage.projectName ?? projectPage.seoTitle}</h3>
                    <p className="mt-1 truncate text-xs text-white/30">
                      {projectPage.slug ? `/projects/${projectPage.slug}` : 'Slug not set'}
                    </p>
                  </div>
                  <Button variant="ghost" size="sm" onClick={() => {
                    setForm(toInput(projectPage))
                    setEditor({
                      wikidataId: projectPage.wikidataId,
                      projectName: projectPage.projectName ?? projectPage.seoTitle,
                      existing: projectPage,
                    })
                  }}>
                    <FilePenLine size={13} /> Edit
                  </Button>
                </div>
                <p className="mt-4 line-clamp-2 text-xs leading-5 text-white/38">{projectPage.metaDescription || 'No meta description yet.'}</p>
                <div className="mt-4 flex flex-wrap items-center gap-3 border-t border-white/[.055] pt-3 text-[10px] text-white/28">
                  <span className={projectPage.projectAvailable ? 'text-emerald-300/55' : 'text-red-300/65'}>
                    {projectPage.projectAvailable ? 'Project active' : 'Project unavailable'}
                  </span>
                  <span className={projectPage.imageAvailable ? 'text-emerald-300/55' : 'text-red-300/65'}>
                    {projectPage.imageAvailable ? 'Image ready' : 'Image unavailable'}
                  </span>
                  {projectPage.publicationStatus === 'PUBLISHED' && projectPage.slug && (
                    <a href={`/projects/${projectPage.slug}`} target="_blank" rel="noreferrer" className="ml-auto inline-flex items-center gap-1 text-violet-300/65 hover:text-violet-200">
                      Open <ExternalLink size={11} />
                    </a>
                  )}
                </div>
              </article>
            ))}
          </div>
          {pagesQuery.data && (
            <Pagination
              page={page}
              totalPages={pagesQuery.data.totalPages}
              totalElements={pagesQuery.data.totalElements}
              label="pages"
              onPage={setPage}
            />
          )}
        </>
      )}

      {pickerOpen && (
        <ProjectPicker onClose={() => setPickerOpen(false)} onSelect={openCandidate} />
      )}

      {editor && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/75 p-0 backdrop-blur-sm sm:items-center sm:p-6">
          <div className="max-h-[96dvh] w-full max-w-6xl overflow-y-auto rounded-t-3xl border border-white/[.09] bg-[#0b0d14] shadow-2xl sm:rounded-3xl">
            <div className="sticky top-0 z-10 flex items-center justify-between border-b border-white/[.07] bg-[#0b0d14]/95 px-5 py-4 backdrop-blur-xl sm:px-7">
              <div>
                <p className="text-[10px] uppercase tracking-[.13em] text-white/28">{editor.wikidataId}</p>
                <h2 className="mt-1 text-lg font-semibold text-white/85">{editor.projectName}</h2>
              </div>
              <button type="button" onClick={() => setEditor(null)} className="grid h-9 w-9 place-items-center rounded-xl text-white/35 hover:bg-white/[.05] hover:text-white/70" aria-label="Close editor">
                <X size={17} />
              </button>
            </div>

            <div className="grid gap-5 p-5 sm:p-7 lg:grid-cols-2">
              <div className="lg:col-span-2">
                <ValidationPanel loading={validationQuery.isFetching} failed={validationQuery.isError} validation={validation} />
              </div>
              <SeoField label="Slug" value={form.slug} onChange={(value) => update('slug', value)} maxLength={120} />
              <SeoField label="Exact cost question" value={form.costQuestion} onChange={(value) => update('costQuestion', value)} maxLength={240} />
              <SeoField label="SEO title" value={form.seoTitle} onChange={(value) => update('seoTitle', value)} maxLength={90} />
              <div className="lg:col-span-2"><SeoTextArea label="Meta description" value={form.metaDescription} onChange={(value) => update('metaDescription', value)} maxLength={220} rows={3} /></div>
              <div className="lg:col-span-2"><SeoField label="Keywords" value={form.keywords} onChange={(value) => update('keywords', value)} maxLength={600} /></div>
              <div className="lg:col-span-2"><SeoTextArea label="Introduction" value={form.intro} onChange={(value) => update('intro', value)} rows={5} /></div>
              <SeoTextArea label="Answer explanation" value={form.answerDetail} onChange={(value) => update('answerDetail', value)} rows={6} />
              <SeoTextArea label="Cost scope" value={form.scopeText} onChange={(value) => update('scopeText', value)} rows={6} />
              <SeoTextArea label="Project context" value={form.contextText} onChange={(value) => update('contextText', value)} rows={6} />
              <SeoTextArea label="Methodology" value={form.methodologyText} onChange={(value) => update('methodologyText', value)} rows={6} />
              <SeoField label="Source name" value={form.sourceName} onChange={(value) => update('sourceName', value)} maxLength={240} />
              <SeoField label="Source URL" value={form.sourceUrl} onChange={(value) => update('sourceUrl', value)} maxLength={2000} />
              <div className="lg:col-span-2">
                <ReferenceEditor
                  values={form.references}
                  onChange={(references) => setForm((current) => ({ ...current, references }))}
                />
              </div>
              <div className="lg:col-span-2">
                <FactEditor
                  values={form.facts}
                  onChange={(facts) => setForm((current) => ({ ...current, facts }))}
                />
              </div>
            </div>

            <div className="sticky bottom-0 flex flex-wrap items-center justify-between gap-3 border-t border-white/[.07] bg-[#0b0d14]/95 px-5 py-4 backdrop-blur-xl sm:px-7">
              <div className="flex items-center gap-3 text-xs text-white/28">
                {editor.existing ? (
                  <a
                    href={`/projects/preview/${editor.wikidataId}`}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1.5 text-violet-300/65 hover:text-violet-200"
                  >
                    <Eye size={13} /> Preview saved draft
                  </a>
                ) : 'Save the draft once to enable preview.'}
              </div>
              <div className="flex gap-2">
                <Button
                  variant={editor.existing?.publicationStatus === 'PUBLISHED' ? 'danger' : 'ghost'}
                  disabled={saveMutation.isPending}
                  onClick={saveDraft}
                >
                  {editor.existing?.publicationStatus === 'PUBLISHED' ? 'Unpublish to draft' : 'Save draft'}
                </Button>
                <Button variant="primary" disabled={publishDisabled} onClick={() => save('PUBLISHED')}>
                  {saveMutation.isPending ? <Loader2 size={14} className="animate-spin" /> : <Globe2 size={14} />} Publish
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

interface ProjectPickerProps {
  onClose: () => void
  onSelect: (candidate: BudgetProjectSeoCandidate) => Promise<void>
}

function ProjectPicker({ onClose, onSelect }: ProjectPickerProps) {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<BudgetProjectSeoCandidateStatus | ''>('MISSING')
  const [page, setPage] = useState(0)
  const [openingId, setOpeningId] = useState<string | null>(null)
  const debouncedSearch = useDebounce(search.trim(), 300)
  const query = useQuery({
    queryKey: ['admin-budget-project-seo-candidates', debouncedSearch, status, page],
    queryFn: () => budgetContentAdminApi.getSeoCandidates({
      page,
      size: 16,
      search: debouncedSearch || undefined,
      pageStatus: status,
    }),
  })

  const select = async (candidate: BudgetProjectSeoCandidate) => {
    setOpeningId(candidate.wikidataId)
    try {
      await onSelect(candidate)
    } finally {
      setOpeningId(null)
    }
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-end justify-center bg-black/80 p-0 backdrop-blur-sm sm:items-center sm:p-6">
      <div className="max-h-[92dvh] w-full max-w-5xl overflow-y-auto rounded-t-3xl border border-white/[.09] bg-[#0b0d14] shadow-2xl sm:rounded-3xl">
        <div className="sticky top-0 z-10 border-b border-white/[.07] bg-[#0b0d14]/95 px-5 py-4 backdrop-blur-xl sm:px-7">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-[10px] uppercase tracking-[.13em] text-emerald-300/55">Canonical reviewed data</p>
              <h2 className="mt-1 text-lg font-semibold text-white/85">Choose a project</h2>
            </div>
            <button type="button" onClick={onClose} className="grid h-9 w-9 place-items-center rounded-xl text-white/35 hover:bg-white/[.05] hover:text-white/70" aria-label="Close picker">
              <X size={17} />
            </button>
          </div>
          <div className="mt-4 flex flex-col gap-2 sm:flex-row">
            <div className="relative flex-1">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/25" />
              <input
                value={search}
                onChange={(event) => { setSearch(event.target.value); setPage(0) }}
                placeholder="Search project, country or Wikidata ID"
                className="h-10 w-full rounded-xl border border-white/[.08] bg-black/20 pl-9 pr-3 text-sm text-white outline-none placeholder:text-white/22 focus:border-emerald-400/35"
              />
            </div>
            <CustomSelect
              value={status}
              onChange={(value) => { setStatus(value as BudgetProjectSeoCandidateStatus | ''); setPage(0) }}
              options={[
                { value: 'MISSING', label: 'Not started' },
                { value: 'DRAFT', label: 'Drafts' },
                { value: 'PUBLISHED', label: 'Published' },
                { value: '', label: 'All reviewed projects' },
              ]}
            />
          </div>
        </div>

        <div className="p-5 sm:p-7">
          {query.isLoading && <LoadingBlock />}
          {query.isError && <ErrorBlock message="Could not load reviewed projects." />}
          {!query.isLoading && !query.isError && query.data?.content.length === 0 && (
            <EmptyBlock message="No reviewed projects match these filters." />
          )}
          <div className="grid gap-3 md:grid-cols-2">
            {query.data?.content.map((candidate) => (
              <article key={candidate.wikidataId} className="rounded-2xl border border-white/[.075] bg-white/[.022] p-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusPill status={candidate.publicationStatus ?? 'MISSING'} />
                      <span className="text-[10px] text-white/25">{candidate.wikidataId}</span>
                    </div>
                    <h3 className="mt-3 text-sm font-semibold text-white/82">{candidate.projectName}</h3>
                    <p className="mt-1 text-xs text-white/32">{candidate.countryName} · {categoryLabel(candidate.category)}{candidate.projectYear ? ` · ${candidate.projectYear}` : ''}</p>
                  </div>
                  <Button size="sm" variant="ghost" disabled={openingId !== null} onClick={() => select(candidate)}>
                    {openingId === candidate.wikidataId ? <Loader2 size={13} className="animate-spin" /> : <FilePenLine size={13} />}
                    {candidate.publicationStatus ? 'Edit' : 'Create'}
                  </Button>
                </div>
                <div className="mt-4 grid grid-cols-2 gap-2 text-[10px] text-white/32">
                  <Readiness ok={candidate.imageAvailable} label="Image" />
                  <Readiness ok={candidate.primaryCostAvailable} label="Primary cost" />
                  <span>Confidence {candidate.costConfidenceScore}/100</span>
                  <span>{candidate.factCount} project facts</span>
                </div>
              </article>
            ))}
          </div>
          {query.data && query.data.totalElements > 0 && (
            <Pagination
              page={page}
              totalPages={query.data.totalPages}
              totalElements={query.data.totalElements}
              label="projects"
              onPage={setPage}
            />
          )}
        </div>
      </div>
    </div>
  )
}

function ValidationPanel({ loading, failed, validation }: {
  loading: boolean
  failed: boolean
  validation: Awaited<ReturnType<typeof budgetContentAdminApi.validateSeoPage>> | undefined
}) {
  if (loading && !validation) {
    return <div className="flex items-center gap-2 rounded-2xl border border-white/[.07] bg-white/[.02] p-4 text-xs text-white/35"><Loader2 size={14} className="animate-spin" /> Checking publication readiness…</div>
  }
  if (failed) {
    return <ErrorBlock message="The publication validator could not run." />
  }
  if (!validation) return null

  return (
    <section className={cn(
      'rounded-2xl border p-4',
      validation.publishable
        ? 'border-emerald-400/20 bg-emerald-400/[.045]'
        : 'border-amber-300/18 bg-amber-300/[.035]',
    )}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          {validation.publishable
            ? <ShieldCheck size={17} className="text-emerald-300/80" />
            : <AlertCircle size={17} className="text-amber-200/75" />}
          <p className="text-sm font-semibold text-white/78">
            {validation.publishable ? 'Ready to publish' : `${validation.errorCount} required fix${validation.errorCount === 1 ? '' : 'es'}`}
          </p>
        </div>
        <span className="text-xs text-white/34">{validation.warningCount} warning{validation.warningCount === 1 ? '' : 's'}</span>
      </div>
      {validation.issues.length > 0 && (
        <div className="mt-3 grid gap-2 lg:grid-cols-2">
          {validation.issues.map((issue) => (
            <div key={`${issue.code}-${issue.field}`} className="flex items-start gap-2 rounded-xl border border-white/[.055] bg-black/15 px-3 py-2.5">
              {issue.severity === 'ERROR'
                ? <AlertCircle size={13} className="mt-0.5 shrink-0 text-red-300/75" />
                : <TriangleAlert size={13} className="mt-0.5 shrink-0 text-amber-200/65" />}
              <div>
                <p className="text-[9px] font-bold uppercase tracking-wider text-white/25">{issue.field}</p>
                <p className="mt-0.5 text-xs leading-5 text-white/52">{issue.message}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function toInput(page: BudgetProjectSeoPage): BudgetProjectSeoPageInput {
  return {
    slug: page.slug ?? '',
    costQuestion: page.costQuestion,
    seoTitle: page.seoTitle,
    metaDescription: page.metaDescription,
    keywords: page.keywords,
    intro: page.intro,
    answerDetail: page.answerDetail,
    scopeText: page.scopeText,
    contextText: page.contextText,
    methodologyText: page.methodologyText,
    sourceName: page.sourceName,
    sourceUrl: page.sourceUrl,
    references: page.references,
    facts: page.facts,
    publicationStatus: page.publicationStatus,
  }
}

function ReferenceEditor({ values, onChange }: {
  values: BudgetProjectSeoReferenceInput[]
  onChange: (values: BudgetProjectSeoReferenceInput[]) => void
}) {
  const updateReference = (index: number, field: keyof BudgetProjectSeoReferenceInput, value: string) => {
    onChange(values.map((reference, current) => current === index
      ? { ...reference, [field]: value }
      : reference))
  }

  return (
    <section className="rounded-2xl border border-white/[.07] bg-black/15 p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-white/72">Supporting references</h3>
          <p className="mt-1 text-xs text-white/30">Use direct sources for context, scope and supporting claims.</p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          disabled={values.length >= 12}
          onClick={() => onChange([...values, { name: '', url: '', claim: '' }])}
        >
          <Plus size={13} /> Add source
        </Button>
      </div>
      <div className="mt-4 space-y-3">
        {values.map((reference, index) => (
          <div key={index} className="grid gap-3 rounded-xl border border-white/[.06] p-3 lg:grid-cols-2">
            <SeoField label="Source name" value={reference.name} onChange={(value) => updateReference(index, 'name', value)} maxLength={240} />
            <SeoField label="Source URL" value={reference.url} onChange={(value) => updateReference(index, 'url', value)} maxLength={2000} />
            <div className="lg:col-span-2"><SeoTextArea label="Claim supported" value={reference.claim} onChange={(value) => updateReference(index, 'claim', value)} maxLength={500} rows={2} /></div>
            <button type="button" onClick={() => onChange(values.filter((_, current) => current !== index))} className="inline-flex w-fit items-center gap-1 text-xs text-red-300/60 hover:text-red-200">
              <Trash2 size={12} /> Remove
            </button>
          </div>
        ))}
        {values.length === 0 && <p className="text-xs text-white/24">No supporting references yet.</p>}
      </div>
    </section>
  )
}

function FactEditor({ values, onChange }: {
  values: BudgetProjectSeoFactInput[]
  onChange: (values: BudgetProjectSeoFactInput[]) => void
}) {
  const updateFact = (index: number, field: keyof BudgetProjectSeoFactInput, value: string) => {
    onChange(values.map((fact, current) => current === index
      ? { ...fact, [field]: value }
      : fact))
  }

  return (
    <section className="rounded-2xl border border-white/[.07] bg-black/15 p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-white/72">Additional sourced facts</h3>
          <p className="mt-1 text-xs text-white/30">Add useful facts that do not fit the canonical metric and milestone fields.</p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          disabled={values.length >= 12}
          onClick={() => onChange([...values, { label: '', value: '', sourceName: '', sourceUrl: '' }])}
        >
          <Plus size={13} /> Add fact
        </Button>
      </div>
      <div className="mt-4 space-y-3">
        {values.map((fact, index) => (
          <div key={index} className="grid gap-3 rounded-xl border border-white/[.06] p-3 lg:grid-cols-2">
            <SeoField label="Label" value={fact.label} onChange={(value) => updateFact(index, 'label', value)} maxLength={120} />
            <SeoField label="Value" value={fact.value} onChange={(value) => updateFact(index, 'value', value)} maxLength={500} />
            <SeoField label="Source name" value={fact.sourceName} onChange={(value) => updateFact(index, 'sourceName', value)} maxLength={240} />
            <SeoField label="Source URL" value={fact.sourceUrl} onChange={(value) => updateFact(index, 'sourceUrl', value)} maxLength={2000} />
            <button type="button" onClick={() => onChange(values.filter((_, current) => current !== index))} className="inline-flex w-fit items-center gap-1 text-xs text-red-300/60 hover:text-red-200">
              <Trash2 size={12} /> Remove
            </button>
          </div>
        ))}
        {values.length === 0 && <p className="text-xs text-white/24">No additional facts yet.</p>}
      </div>
    </section>
  )
}

function StatusPill({ status }: { status: BudgetProjectSeoCandidateStatus }) {
  const label = status === 'MISSING' ? 'Not started' : status
  return (
    <span className={cn(
      'rounded-full px-2 py-1 text-[9px] font-bold uppercase tracking-wider',
      status === 'PUBLISHED' && 'bg-emerald-400/10 text-emerald-300/75',
      status === 'DRAFT' && 'bg-amber-300/10 text-amber-200/65',
      status === 'MISSING' && 'bg-white/[.055] text-white/35',
    )}>
      {label}
    </span>
  )
}

function Readiness({ ok, label }: { ok: boolean; label: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1', ok ? 'text-emerald-300/55' : 'text-red-300/65')}>
      {ok ? <CheckCircle2 size={11} /> : <AlertCircle size={11} />} {label}
    </span>
  )
}

function Pagination({ page, totalPages, totalElements, label, onPage }: {
  page: number
  totalPages: number
  totalElements: number
  label: string
  onPage: (page: number) => void
}) {
  return (
    <div className="mt-4 flex items-center justify-between rounded-xl border border-white/[.07] px-3 py-2">
      <span className="text-xs text-white/35">{totalElements} {label}</span>
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
          <ChevronLeft size={14} /> Previous
        </Button>
        <span className="text-xs text-white/40">{page + 1} / {Math.max(totalPages, 1)}</span>
        <Button variant="ghost" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next <ChevronRight size={14} />
        </Button>
      </div>
    </div>
  )
}

function LoadingBlock() {
  return <div className="flex h-48 items-center justify-center"><Loader2 size={20} className="animate-spin text-white/30" /></div>
}

function ErrorBlock({ message }: { message: string }) {
  return <div className="rounded-2xl border border-red-400/20 bg-red-400/[.06] p-6 text-center text-sm text-red-300">{message}</div>
}

function EmptyBlock({ message }: { message: string }) {
  return <div className="rounded-2xl border border-dashed border-white/[.1] p-10 text-center text-sm text-white/40">{message}</div>
}

function categoryLabel(category: string) {
  return category.toLowerCase().replaceAll('_', ' ')
}

interface SeoFieldProps {
  label: string
  value: string
  onChange: (value: string) => void
  maxLength?: number
}

function SeoField({ label, value, onChange, maxLength }: SeoFieldProps) {
  return (
    <label className="block">
      <span className="mb-2 flex items-center justify-between text-[10px] font-semibold uppercase tracking-[.11em] text-white/35">
        {label}{maxLength && <span className="font-normal normal-case tracking-normal text-white/18">{value.length}/{maxLength}</span>}
      </span>
      <input value={value} maxLength={maxLength} onChange={(event) => onChange(event.target.value)} className="h-10 w-full rounded-xl border border-white/[.08] bg-black/20 px-3 text-sm text-white outline-none focus:border-emerald-400/35" />
    </label>
  )
}

interface SeoTextAreaProps extends SeoFieldProps {
  rows: number
}

function SeoTextArea({ label, value, onChange, maxLength = 4000, rows }: SeoTextAreaProps) {
  return (
    <label className="block">
      <span className="mb-2 flex items-center justify-between text-[10px] font-semibold uppercase tracking-[.11em] text-white/35">
        {label}<span className="font-normal normal-case tracking-normal text-white/18">{value.length}/{maxLength}</span>
      </span>
      <textarea value={value} maxLength={maxLength} rows={rows} onChange={(event) => onChange(event.target.value)} className="w-full resize-y rounded-xl border border-white/[.08] bg-black/20 px-3 py-2.5 text-sm leading-6 text-white outline-none focus:border-emerald-400/35" />
    </label>
  )
}
