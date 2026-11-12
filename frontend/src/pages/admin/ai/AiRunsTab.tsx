import { useMemo } from "react";
import { Activity, Loader2 } from "lucide-react";

import type { AiRun } from "@/api/admin";
import { Pagination } from "@/components/ui/Pagination";

import { AiRunRow } from "./AiRunRow";
import { groupByDate } from "./ai-helpers";

interface AiRunsTabProps {
  runs: AiRun[];
  isLoading: boolean;
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  onOpenRun: (runId: string) => void;
}

export function AiRunsTab({
  runs,
  isLoading,
  page,
  totalPages,
  totalElements,
  onPageChange,
  onOpenRun,
}: AiRunsTabProps) {
  const grouped = useMemo(() => groupByDate(runs, (r) => r.createdAt), [runs]);

  if (isLoading && runs.length === 0) {
    return (
      <div className="flex items-center justify-center gap-3 py-12 text-sm text-white/40">
        <Loader2 size={16} className="animate-spin" />
        Loading runs...
      </div>
    );
  }

  if (!isLoading && runs.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <div className="rounded-2xl border border-white/[.06] bg-white/[.03] p-4">
          <Activity size={24} className="text-white/20" />
        </div>
        <p className="text-sm text-white/40">No AI runs yet</p>
        <p className="text-xs text-white/25">
          Start a review or generation to see results here.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div>
        {grouped.map((group) => (
          <div key={group.label}>
            <div className="flex items-center gap-3 px-2 py-2 first:pt-0">
              <span className="text-[10px] font-medium uppercase tracking-[0.12em] text-white/20">
                {group.label}
              </span>
              <div className="h-px flex-1 bg-white/[.04]" />
            </div>
            <div className="space-y-px">
              {group.items.map((run) => (
                <AiRunRow key={run.id} run={run} onOpen={onOpenRun} />
              ))}
            </div>
          </div>
        ))}
      </div>

      <Pagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={onPageChange}
      />
    </div>
  );
}
