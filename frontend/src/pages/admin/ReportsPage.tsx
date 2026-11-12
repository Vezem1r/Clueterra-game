import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Flag,
  CheckCircle, XCircle, Clock, Wrench, Edit2,
  History,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { Badge } from '@/components/ui/Badge'
import { SkeletonTable } from '@/components/ui/Skeleton'
import { EmptyState } from '@/components/ui/EmptyState'
import { CustomSelect } from '@/components/ui/CustomSelect'
import { AdminTableHeader } from '@/components/admin/AdminTableHeader'
import { AdminFilterBar } from '@/components/admin/AdminFilterBar'
import { TableToolbarButtons } from '@/components/admin/TableToolbarButtons'
import { useAdminTable } from '@/hooks/useAdminTable'
import type { ColumnDef } from '@/hooks/useAdminTable'
import { adminApi } from '@/api/admin'
import { useTranslation } from '@/i18n'
import { useToastStore } from '@/store/toastStore'
import type { HintReportAdminDto, ResolveReportRequest } from '@/types'
import { cn } from '@/utils/cn'

const ACTIONS_GRAD = 'linear-gradient(to right, transparent, rgb(15,15,20) 28px, rgb(15,15,20))'

const ALL_COLUMNS: ColumnDef[] = [
  { key: 'hintText',     label: 'Hint',        sortKey: undefined,     defaultVisible: true,  defaultWidth: 280 },
  { key: 'countryName',  label: 'Country',     sortKey: undefined,     defaultVisible: true,  defaultWidth: 110 },
  { key: 'userEmail',    label: 'Reporter',    sortKey: undefined,     defaultVisible: true,  defaultWidth: 170 },
  { key: 'reportType',   label: 'Type',        sortKey: 'reportType',  defaultVisible: true,  defaultWidth: 100 },
  { key: 'description',  label: 'Description', sortKey: undefined,     defaultVisible: true,  defaultWidth: 220 },
  { key: 'status',       label: 'Status',      sortKey: 'status',      defaultVisible: true,  defaultWidth: 95  },
  { key: 'createdAt',    label: 'Date',        sortKey: 'createdAt',   defaultVisible: true,  defaultWidth: 100 },
  { key: 'resolvedByEmail', label: 'Resolved By', sortKey: undefined,  defaultVisible: false, defaultWidth: 150 },
  { key: 'adminNotes',   label: 'Notes',       sortKey: undefined,     defaultVisible: false, defaultWidth: 180 },
  { key: 'actions',      label: '',            sortKey: undefined,     defaultVisible: true,  defaultWidth: 60  },
]

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { variant: 'warning' | 'success' | 'error' | 'neutral'; icon: typeof Clock }> = {
    PENDING:  { variant: 'warning', icon: Clock },
    REVIEWED: { variant: 'success', icon: CheckCircle },
    FIXED:    { variant: 'success', icon: Wrench },
    REJECTED: { variant: 'error',   icon: XCircle },
  }
  const cfg = map[status] || { variant: 'neutral' as const, icon: Clock }
  return (
    <Badge variant={cfg.variant} className="text-[10px]">
      <cfg.icon size={10} className="mr-0.5" />{status}
    </Badge>
  )
}

function CellValue({ col, report }: { col: ColumnDef; report: HintReportAdminDto }) {
  switch (col.key) {
    case 'hintText':
      return <span className="text-sm text-white/55 truncate" title={report.hintText}>{report.hintText}</span>
    case 'countryName':
      return <span className="text-xs text-white/35 truncate">{report.countryName}</span>
    case 'userEmail':
      return <span className="text-sm text-white/50 truncate" title={report.userEmail}>{report.userEmail}</span>
    case 'reportType':
      return <Badge variant="neutral" className="text-[10px]">{report.reportType}</Badge>
    case 'description':
      return (
        <span className="text-xs text-white/35 truncate" title={report.description || ''}>
          {report.description || '\u2014'}
        </span>
      )
    case 'status':
      return <StatusBadge status={report.status} />
    case 'createdAt':
      return <span className="text-xs text-white/25">{new Date(report.createdAt).toLocaleDateString()}</span>
    case 'resolvedByEmail':
      return <span className="text-xs text-white/35 truncate">{report.resolvedByEmail || '\u2014'}</span>
    case 'adminNotes':
      return (
        <span className="text-xs text-white/35 truncate" title={report.adminNotes || ''}>
          {report.adminNotes || '\u2014'}
        </span>
      )
    default:
      return null
  }
}

function ReportRow({ report, dataCols, dataColsTemplate, hasActions, actionsRefCb, onResolve }: {
  report: HintReportAdminDto; dataCols: ColumnDef[]; dataColsTemplate: string
  hasActions: boolean; actionsRefCb: (node: HTMLDivElement | null) => void
  onResolve: (r: HintReportAdminDto) => void
}) {
  return (
    <div className="group">
      <div className="hidden sm:flex items-center min-w-max border-b border-white/[.04] last:border-0 hover:bg-white/[.02] transition-colors">
        <div className="grid gap-0 px-3 py-3 flex-1 min-w-0 items-center" style={{ gridTemplateColumns: dataColsTemplate }}>
          {dataCols.map(col => (
            <div key={col.key} className="overflow-hidden px-2">
              <CellValue col={col} report={report} />
            </div>
          ))}
        </div>
        {hasActions && (
          <div ref={actionsRefCb} className="sticky right-0 z-10 shrink-0 flex items-center gap-0.5 justify-end pl-8 pr-2 ml-auto"
            style={{ background: ACTIONS_GRAD }}>
            {report.status === 'PENDING' && (
              <Button variant="ghost" size="sm" onClick={() => onResolve(report)} title="Resolve">
                <Edit2 size={13} />
              </Button>
            )}
          </div>
        )}
      </div>

      <div className="sm:hidden flex flex-col gap-1.5 px-4 py-3 border-b border-white/[.04]">
        <div className="flex items-center justify-between gap-2">
          <span className="text-sm text-white/55 truncate flex-1">{report.hintText}</span>
          <StatusBadge status={report.status} />
        </div>
        <div className="flex items-center gap-2 text-xs text-white/30">
          <span>{report.countryName}</span>
          <span>&middot;</span>
          <Badge variant="neutral" className="text-[10px]">{report.reportType}</Badge>
          <span>&middot;</span>
          <span className="truncate">{report.userEmail}</span>
        </div>
        {report.description && (
          <p className="text-xs text-white/25 truncate">{report.description}</p>
        )}
        <div className="flex items-center justify-between">
          <span className="text-xs text-white/20">{new Date(report.createdAt).toLocaleDateString()}</span>
          {report.status === 'PENDING' && (
            <Button variant="outline" size="sm" onClick={() => onResolve(report)}>
              Resolve
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}

export function ReportsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const toast = useToastStore()

  const [tab, setTab] = useState<'pending' | 'history'>('pending')
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState('')

  const { containerRef, ...table } = useAdminTable({
    storageKey: 'admin-reports',
    columns: ALL_COLUMNS,
    defaultSort: 'createdAt',
    defaultSortDir: 'desc',
    pinnedColumns: ['actions'],
    onSortChange: () => setPage(0),
  })

  const switchTab = (t: 'pending' | 'history') => {
    setTab(t)
    setPage(0)
    setStatusFilter('')
  }

  const [resolving, setResolving] = useState<HintReportAdminDto | null>(null)
  const [resolveStatus, setResolveStatus] = useState<ResolveReportRequest['status']>('REVIEWED')
  const [resolveNotes, setResolveNotes] = useState('')
  const [pendingCount, setPendingCount] = useState<number | null>(null)
  const [applyToAll, setApplyToAll] = useState(false)
  const [loadingCount, setLoadingCount] = useState(false)

  const { data: reports, isLoading } = useQuery({
    queryKey: ['admin-reports', tab, statusFilter, page, table.sortBy, table.sortDir],
    queryFn: () => {
      if (tab === 'pending') {
        return adminApi.getReports({ page, size: 20, status: 'PENDING', sortBy: table.sortBy, sortDir: table.sortDir })
      }
      return adminApi.getReports({
        page,
        size: 20,
        status: statusFilter || undefined,
        excludeStatus: statusFilter ? undefined : 'PENDING',
        sortBy: table.sortBy,
        sortDir: table.sortDir,
      })
    },
  })

  const resolveMutation = useMutation({
    mutationFn: ({ id, request }: { id: string; request: ResolveReportRequest }) =>
      adminApi.resolveReport(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-reports'] })
      setResolving(null)
      setResolveNotes('')
      setPendingCount(null)
      setApplyToAll(false)
      toast.addToast({ type: 'success', message: 'Report resolved' })
    },
  })

  const handleResolve = () => {
    if (!resolving) return
    resolveMutation.mutate({
      id: resolving.id,
      request: {
        status: resolveStatus,
        adminNotes: resolveNotes || undefined,
        applyToAll: pendingCount && pendingCount > 1 ? applyToAll : undefined,
      },
    })
  }

  const openResolve = async (report: HintReportAdminDto) => {
    setResolving(report)
    setResolveStatus('REVIEWED')
    setResolveNotes('')
    setApplyToAll(false)
    setPendingCount(null)
    setLoadingCount(true)
    try {
      const count = await adminApi.countPendingForHint(report.hintId)
      setPendingCount(count)
    } catch {
      setPendingCount(1)
    } finally {
      setLoadingCount(false)
    }
  }

  const hasFilters = !!statusFilter
  const activeFilterCount = statusFilter ? 1 : 0

  return (
    <div className="flex flex-col h-full">

      <div className="shrink-0 bg-[#08080c] px-5 sm:px-6 lg:px-8 pt-5 sm:pt-6 lg:pt-8 pb-5 space-y-5">

        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-white">{t.admin.reports}</h1>
            <p className="text-white/40 text-sm mt-1">Review and resolve user-submitted hint reports</p>
          </div>
          <div className="flex items-center gap-1.5">
            <TableToolbarButtons
              showColPicker={table.showColPicker}
              onToggleColPicker={() => table.setShowColPicker(o => !o)}
              onFitToContainer={table.handleFitToContainer}
              onResetWidths={table.handleResetWidths}
              toggleableColumns={table.toggleableColumns}
              visibleCols={table.visibleCols}
              onToggleCol={table.toggleCol}
              onShowAllCols={table.showAllCols}
              onResetCols={table.resetCols}
            />
          </div>
        </div>

        <AdminFilterBar
          activeFilterCount={activeFilterCount}
          onClearFilters={() => { setStatusFilter(''); setPage(0) }}
          hasFilters={hasFilters}
          resultCount={reports?.totalElements}
        >
          <div className="flex items-center gap-1 bg-white/[.04] rounded-lg p-0.5 shrink-0">
            <button
              onClick={() => switchTab('pending')}
              className={cn(
                'px-3 py-1.5 rounded-md text-xs font-medium transition-colors',
                tab === 'pending' ? 'bg-white/[.1] text-white' : 'text-white/35 hover:text-white/55',
              )}
            >
              <Clock size={12} className="inline mr-1 -mt-0.5" />
              Pending
            </button>
            <button
              onClick={() => switchTab('history')}
              className={cn(
                'px-3 py-1.5 rounded-md text-xs font-medium transition-colors',
                tab === 'history' ? 'bg-white/[.1] text-white' : 'text-white/35 hover:text-white/55',
              )}
            >
              <History size={12} className="inline mr-1 -mt-0.5" />
              History
            </button>
          </div>
          {tab === 'history' && (
            <CustomSelect
              value={statusFilter}
              onChange={v => { setStatusFilter(v); setPage(0) }}
              options={[
                { value: '', label: 'All Resolved' },
                { value: 'REVIEWED', label: 'Reviewed' },
                { value: 'FIXED', label: 'Fixed' },
                { value: 'REJECTED', label: 'Rejected' },
              ]}
              placeholder="All Resolved"
            />
          )}
        </AdminFilterBar>
      </div>

      <div className="flex-1 min-h-0 px-5 sm:px-6 lg:px-8 pb-3">

        <div ref={containerRef} className="h-full border border-white/[.06] rounded-xl bg-white/[.03] overflow-auto scrollbar scrollbar-thumb-white/15 scrollbar-track-transparent [&::-webkit-scrollbar-corner]:bg-transparent">

          <AdminTableHeader
            dataCols={table.dataCols}
            hasActions={table.hasActions}
            dataColsTemplate={table.dataColsTemplate}
            actionsW={table.actionsW}
            sortBy={table.sortBy}
            sortDir={table.sortDir}
            onSort={table.handleSort}
            onResizeStart={table.onResizeStart}
            getWidth={table.getWidth}
          />

          {isLoading ? (
            <div className="p-5"><SkeletonTable rows={8} cols={table.dataCols.length} /></div>
          ) : !reports?.content?.length ? (
            <EmptyState
              icon={Flag}
              title={tab === 'pending' ? 'No pending reports' : t.common.noResults}
              className="py-16"
            />
          ) : (
            reports.content.map(report => (
              <ReportRow
                key={report.id}
                report={report}
                dataCols={table.dataCols}
                dataColsTemplate={table.dataColsTemplate}
                hasActions={table.hasActions}
                actionsRefCb={table.actionsRefCb}
                onResolve={openResolve}
              />
            ))
          )}
        </div>
      </div>

      {reports && reports.totalPages > 1 && (
        <div className="shrink-0 bg-[#08080c] border-t border-white/[.06] px-5 sm:px-6 lg:px-8 py-3">
          <Pagination
            page={page}
            totalPages={reports.totalPages}
            totalElements={reports.totalElements}
            onPageChange={setPage}
          />
        </div>
      )}

      <Modal isOpen={!!resolving} onClose={() => { setResolving(null); setPendingCount(null); setApplyToAll(false) }} title={t.admin.resolveReport}>
        <div className="space-y-4">
          {resolving && (
            <div className="p-3 rounded-xl bg-white/[.04] text-sm text-white/55 space-y-1">
              <p className="text-[10px] text-white/25">Report #{resolving.id}</p>
              <p>{resolving.hintText}</p>
              <p className="text-xs text-white/25">{resolving.countryName} &mdash; {resolving.userEmail}</p>
              {resolving.description && (
                <p className="text-white/40">{resolving.description}</p>
              )}
            </div>
          )}
          <div>
            <label className="block text-xs text-white/35 mb-1.5">Status</label>
            <CustomSelect
              value={resolveStatus}
              onChange={v => setResolveStatus(v as ResolveReportRequest['status'])}
              options={[
                { value: 'REVIEWED', label: 'Reviewed' },
                { value: 'FIXED', label: 'Fixed' },
                { value: 'REJECTED', label: t.common.rejected },
              ]}
              placeholder="Status"
            />
          </div>
          <div>
            <label className="block text-xs text-white/35 mb-1.5">{t.admin.adminNotes}</label>
            <textarea
              value={resolveNotes}
              onChange={e => setResolveNotes(e.target.value)}
              placeholder="Optional notes..."
              className="w-full h-24 px-4 py-3 bg-white/[.04] border border-white/[.08] rounded-xl text-white text-sm resize-none focus:outline-none focus:border-white/20"
            />
          </div>
          {!loadingCount && pendingCount !== null && pendingCount > 1 && (
            <label
              onClick={() => setApplyToAll(v => !v)}
              className="flex items-center gap-2.5 p-3 rounded-xl bg-amber-500/[.06] border border-amber-500/15 cursor-pointer select-none"
            >
              <div
                className={cn(
                  'w-4 h-4 rounded border flex items-center justify-center shrink-0 transition-colors',
                  applyToAll ? 'bg-amber-500 border-amber-500' : 'border-white/20 bg-white/[.04]'
                )}
              >
                {applyToAll && (
                  <svg width="9" height="7" viewBox="0 0 9 7" fill="none">
                    <path d="M1 3.5L3.5 6L8 1" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                )}
              </div>
              <span className="text-sm text-amber-200/80">
                Apply to all {pendingCount} pending reports for this hint
              </span>
            </label>
          )}
          {loadingCount && (
            <div className="flex items-center gap-2 text-xs text-white/25">
              <div className="w-3 h-3 border border-white/20 border-t-white/60 rounded-full animate-spin" />
              Checking other reports...
            </div>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => { setResolving(null); setPendingCount(null); setApplyToAll(false) }}>{t.common.cancel}</Button>
            <Button onClick={handleResolve} isLoading={resolveMutation.isPending}>
              {applyToAll && pendingCount ? `Resolve ${pendingCount} reports` : t.common.confirm}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
