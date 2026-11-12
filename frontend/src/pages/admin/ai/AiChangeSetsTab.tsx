import { useMemo } from "react";
import { Layers, Loader2 } from "lucide-react";

import type { AiChangeSet } from "@/api/admin";
import { Pagination } from "@/components/ui/Pagination";

import { AiChangeSetRow } from "./AiChangeSetRow";
import { groupByDate } from "./ai-helpers";

interface AiChangeSetsTabProps {
  changeSets: AiChangeSet[];
  isLoading: boolean;
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  onOpenChangeSet: (changeSetId: string) => void;
}

export function AiChangeSetsTab({
  changeSets,
  isLoading,
  page,
  totalPages,
  totalElements,
  onPageChange,
  onOpenChangeSet,
}: AiChangeSetsTabProps) {
  const grouped = useMemo(
    () => groupByDate(changeSets, (cs) => cs.createdAt),
    [changeSets],
  );

  if (isLoading && changeSets.length === 0) {
    return (
      <div className="flex items-center justify-center gap-3 py-12 text-sm text-white/40">
        <Loader2 size={16} className="animate-spin" />
        Loading change sets...
      </div>
    );
  }

  if (!isLoading && changeSets.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <div className="rounded-2xl border border-white/[.06] bg-white/[.03] p-4">
          <Layers size={24} className="text-white/20" />
        </div>
        <p className="text-sm text-white/40">No change sets yet</p>
        <p className="text-xs text-white/25">
          Change sets are created when AI runs produce results.
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
              {group.items.map((cs) => (
                <AiChangeSetRow
                  key={cs.id}
                  changeSet={cs}
                  onOpen={onOpenChangeSet}
                />
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
