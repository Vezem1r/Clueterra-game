import { Binary, ChevronRight, Search, Sparkles } from "lucide-react";

import type { AiRun } from "@/api/admin";
import { Badge } from "@/components/ui/Badge";
import { Tooltip } from "@/components/ui/Tooltip";
import { cn } from "@/utils/cn";

import {
  formatCompactNumber,
  formatRelativeTime,
  formatShortDateTime,
  getStatusBarColor,
  getStatusVariant,
  parseErrorMessage,
  shortenModelName,
} from "./ai-helpers";

interface AiRunRowProps {
  run: AiRun;
  onOpen: (runId: string) => void;
}

const TYPE_CONFIG = {
  REVIEW: { icon: Search, color: "text-sky-400/70" },
  GENERATION: { icon: Sparkles, color: "text-emerald-400/70" },
  EMBEDDING: { icon: Binary, color: "text-violet-400/70" },
} as const;

export function AiRunRow({ run, onOpen }: AiRunRowProps) {
  const error = parseErrorMessage(run.errorMessage);
  const model = shortenModelName(run.model);
  const items = run.processedItems || run.preparedItems;
  const inTok = formatCompactNumber(
    run.actualInputTokens || run.estimatedInputTokens,
  );
  const outTok = formatCompactNumber(
    run.actualOutputTokens || run.estimatedOutputTokens,
  );
  const isFailed = run.status === "FAILED" || run.status === "ERROR";
  const cfg = TYPE_CONFIG[run.runType as keyof typeof TYPE_CONFIG];
  const TypeIcon = cfg?.icon;

  return (
    <button
      aria-label={`Open ${run.runType.toLowerCase()} run details`}
      onClick={() => onOpen(run.id)}
      className={cn(
        "group flex w-full cursor-pointer items-center gap-2 rounded-lg px-1 py-2 text-left transition-all sm:gap-2.5",
        isFailed
          ? "bg-red-500/[.03] hover:bg-red-500/[.06]"
          : "hover:bg-white/[.03]",
      )}
    >
      <div
        className={cn(
          "w-0.5 self-stretch rounded-full shrink-0",
          getStatusBarColor(run.status),
        )}
      />

      <Badge
        variant={getStatusVariant(run.status)}
        dot
        className="shrink-0 text-[10px]"
      >
        {run.status}
      </Badge>

      {TypeIcon && <TypeIcon size={12} className={cn("shrink-0", cfg.color)} />}

      <span className="hidden w-[68px] shrink-0 truncate text-[11px] text-white/30 md:block">
        {model}
      </span>

      <div className="hidden items-center gap-2.5 text-[11px] font-mono tabular-nums text-white/35 lg:flex">
        <span>
          {items} <span className="text-white/15">items</span>
        </span>
        <span
          className={
            run.changedItems > 0 ? "text-emerald-400/50" : "text-white/20"
          }
        >
          {run.changedItems}{" "}
          <span
            className={
              run.changedItems > 0 ? "text-emerald-400/25" : "text-white/15"
            }
          >
            chg
          </span>
        </span>
        <span className="text-white/20">
          {inTok}
          <span className="text-white/10">/</span>
          {outTok}
        </span>
      </div>

      {error && (
        <span className="min-w-0 flex-1 truncate text-[11px] text-red-400/45">
          {error}
        </span>
      )}

      {!error && <span className="flex-1" />}

      <Tooltip content={formatShortDateTime(run.createdAt)}>
        <span className="w-[52px] shrink-0 text-right text-[11px] tabular-nums text-white/25">
          {formatRelativeTime(run.createdAt)}
        </span>
      </Tooltip>

      <ChevronRight
        size={12}
        className="shrink-0 text-white/[.06] transition-colors group-hover:text-white/20"
      />
    </button>
  );
}
