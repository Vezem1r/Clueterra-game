import { ChevronRight } from "lucide-react";

import type { AiChangeSet } from "@/api/admin";
import { Badge } from "@/components/ui/Badge";
import { Tooltip } from "@/components/ui/Tooltip";
import { cn } from "@/utils/cn";

import {
  formatRelativeTime,
  formatShortDateTime,
  getStatusBarColor,
  getStatusVariant,
} from "./ai-helpers";

interface AiChangeSetRowProps {
  changeSet: AiChangeSet;
  onOpen: (changeSetId: string) => void;
}

export function AiChangeSetRow({ changeSet, onOpen }: AiChangeSetRowProps) {
  const appliedPct =
    changeSet.totalItems > 0
      ? Math.round((changeSet.appliedItems / changeSet.totalItems) * 100)
      : 0;

  return (
    <button
      onClick={() => onOpen(changeSet.id)}
      className="group flex w-full cursor-pointer items-center gap-2 rounded-lg px-1 py-2 text-left transition-all hover:bg-white/[.03] sm:gap-2.5"
    >
      <div
        className={cn(
          "w-0.5 self-stretch rounded-full shrink-0",
          getStatusBarColor(changeSet.status),
        )}
      />

      <Badge
        variant={getStatusVariant(changeSet.status)}
        dot
        className="shrink-0 text-[10px]"
      >
        {changeSet.status}
      </Badge>

      <span className="min-w-0 flex-1 truncate text-[11px] text-white/40">
        {changeSet.title}
      </span>

      <div className="hidden items-center gap-3 sm:flex">
        <div className="flex items-center gap-2">
          <div className="h-1 w-14 overflow-hidden rounded-full bg-white/[.06]">
            <div
              className="h-full rounded-full bg-emerald-400/60 transition-all"
              style={{ width: `${appliedPct}%` }}
            />
          </div>
          <span className="text-[10px] font-mono tabular-nums text-white/30">
            {changeSet.appliedItems}
            <span className="text-white/15">/</span>
            {changeSet.totalItems}
          </span>
        </div>
        {changeSet.rejectedItems > 0 && (
          <span className="text-[10px] font-mono tabular-nums text-red-400/50">
            {changeSet.rejectedItems}
            <span className="ml-0.5 text-red-400/25">rej</span>
          </span>
        )}
      </div>

      <Tooltip content={formatShortDateTime(changeSet.createdAt)}>
        <span className="w-[52px] shrink-0 text-right text-[11px] tabular-nums text-white/25">
          {formatRelativeTime(changeSet.createdAt)}
        </span>
      </Tooltip>

      <ChevronRight
        size={12}
        className="shrink-0 text-white/[.06] transition-colors group-hover:text-white/20"
      />
    </button>
  );
}
