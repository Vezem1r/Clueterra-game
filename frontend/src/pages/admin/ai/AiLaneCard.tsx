import { Binary, Bot, ScanSearch, Sparkles } from "lucide-react";

import type {
  AiEmbeddingOverviewResponse,
  AiLaneOverviewResponse,
} from "@/api/admin";
import { Badge } from "@/components/ui/Badge";
import { cn } from "@/utils/cn";

import {
  formatCompactNumber,
  formatNumber,
  formatShortDateTime,
  shortenModelName,
} from "./ai-helpers";

type LaneType = "review" | "generation";

interface AiLaneCardProps {
  lane: AiLaneOverviewResponse;
  type: LaneType;
  embedding?: AiEmbeddingOverviewResponse;
}

const LANE_CONFIG = {
  review: {
    title: "Review",
    icon: Bot,
    gradient: "from-sky-400/80 via-sky-400/40 to-transparent",
    iconBorder: "border-sky-400/20",
    iconBg: "bg-sky-400/10",
    iconText: "text-sky-400",
    barFill: "bg-sky-400/40",
    barShadow: "shadow-[0_0_6px_rgba(56,189,248,0.2)]",
  },
  generation: {
    title: "Generation",
    icon: Sparkles,
    gradient: "from-emerald-400/80 via-emerald-400/40 to-transparent",
    iconBorder: "border-emerald-400/20",
    iconBg: "bg-emerald-400/10",
    iconText: "text-emerald-400",
    barFill: "bg-emerald-400/40",
    barShadow: "shadow-[0_0_6px_rgba(52,211,153,0.2)]",
  },
} as const;

function QuotaRow({
  label,
  used,
  limit,
  cfg,
}: {
  label: string;
  used: number;
  limit: number;
  cfg: (typeof LANE_CONFIG)[LaneType];
}) {
  const pct = limit > 0 ? Math.min(100, Math.round((used / limit) * 100)) : 0;
  const isHigh = pct >= 80;

  return (
    <div className="flex items-center justify-between py-1">
      <span className="text-[11px] text-white/25">{label}</span>
      <div className="flex items-center gap-2">
        <div className="h-[5px] w-16 overflow-hidden rounded-full bg-white/[.06]">
          <div
            className={cn(
              "h-full rounded-full transition-all",
              isHigh
                ? "bg-amber-400 shadow-[0_0_6px_rgba(251,191,36,0.2)]"
                : cn(cfg.barFill, cfg.barShadow),
            )}
            style={{ width: `${pct}%` }}
          />
        </div>
        <span className="min-w-[4rem] text-right text-[11px] font-mono tabular-nums text-white/40">
          {formatNumber(used)}
          <span className="text-white/15"> / {formatNumber(limit)}</span>
        </span>
      </div>
    </div>
  );
}

export function AiLaneCard({ lane, type, embedding }: AiLaneCardProps) {
  const cfg = LANE_CONFIG[type];
  const Icon = cfg.icon;
  const q = lane.quota;

  return (
    <div className="relative overflow-hidden rounded-xl border border-white/[.06] bg-white/[.02]">
      <div
        className={cn(
          "absolute left-0 right-0 top-0 h-0.5 bg-gradient-to-r",
          cfg.gradient,
        )}
      />

      <div className="flex items-center justify-between px-4 pt-4 pb-2">
        <div className="flex items-center gap-3">
          <div
            className={cn(
              "flex h-9 w-9 items-center justify-center rounded-xl border",
              cfg.iconBorder,
              cfg.iconBg,
            )}
          >
            <Icon size={16} className={cfg.iconText} />
          </div>
          <div>
            <span className="text-[13px] font-semibold text-white">
              {cfg.title}
            </span>
            <span className="ml-2 text-[11px] text-white/20">
              {shortenModelName(lane.model)}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Badge
            variant={lane.schedulerEnabled ? "success" : "warning"}
            dot
            className="text-[10px]"
          >
            {lane.schedulerEnabled ? "Auto" : "Manual"}
          </Badge>
          <span className="text-[10px] tabular-nums text-white/20">
            {lane.maxRequestsPerRun}/run
          </span>
        </div>
      </div>

      <div className="border-t border-white/[.04] px-4 py-1">
        <QuotaRow
          label="Req / min"
          used={q.requestsPerMinuteUsed}
          limit={q.requestsPerMinuteLimit}
          cfg={cfg}
        />
        <QuotaRow
          label="Req / day"
          used={q.requestsPerDayUsed}
          limit={q.requestsPerDayLimit}
          cfg={cfg}
        />
        {q.groundedPromptsPerDayLimit != null &&
          q.groundedPromptsPerDayUsed != null && (
            <QuotaRow
              label="Grounded / day"
              used={q.groundedPromptsPerDayUsed}
              limit={q.groundedPromptsPerDayLimit}
              cfg={cfg}
            />
          )}
      </div>

      <div className="grid grid-cols-3 gap-2 border-t border-white/[.04] p-3">
        <div className="rounded-lg border border-white/[.06] bg-white/[.02] px-2.5 py-2 text-center">
          <p className="text-sm font-semibold tabular-nums text-white/70">
            {formatCompactNumber(q.inputTokensToday)}
          </p>
          <p className="mt-0.5 text-[9px] uppercase tracking-[0.1em] text-white/20">
            Input
          </p>
        </div>
        <div className="rounded-lg border border-white/[.06] bg-white/[.02] px-2.5 py-2 text-center">
          <p className="text-sm font-semibold tabular-nums text-white/70">
            {formatCompactNumber(q.outputTokensToday)}
          </p>
          <p className="mt-0.5 text-[9px] uppercase tracking-[0.1em] text-white/20">
            Output
          </p>
        </div>
        <div className="rounded-lg border border-white/[.06] bg-white/[.02] px-2.5 py-2 text-center">
          <p
            className={cn(
              "text-sm font-semibold tabular-nums",
              q.failedToday > 0 ? "text-red-400/70" : "text-white/70",
            )}
          >
            {String(q.failedToday)}
          </p>
          <p className="mt-0.5 text-[9px] uppercase tracking-[0.1em] text-white/20">
            Failed
          </p>
        </div>
      </div>

      {type === "review" && lane.reviewCoverage && (
        <div className="border-t border-white/[.04] px-4 py-3">
          <div className="mb-2 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <ScanSearch size={13} className="text-sky-400/70" />
              <span className="text-[11px] font-medium text-white/45">
                Review coverage
              </span>
            </div>
            <span className="text-[10px] tabular-nums text-sky-400/65">
              {lane.reviewCoverage.coveragePercent.toFixed(1)}%
            </span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-white/[.06]">
            <div
              className="h-full rounded-full bg-sky-400/50 shadow-[0_0_8px_rgba(56,189,248,0.25)] transition-all"
              style={{ width: `${Math.min(100, lane.reviewCoverage.coveragePercent)}%` }}
            />
          </div>
          <div className="mt-1.5 flex items-center justify-between text-[10px]">
            <span className="tabular-nums text-white/35">
              {formatCompactNumber(lane.reviewCoverage.processedHints)} /{" "}
              {formatCompactNumber(lane.reviewCoverage.totalHints)} processed
            </span>
            <span className="tabular-nums text-amber-400/60">
              {formatCompactNumber(lane.reviewCoverage.pendingHints)} pending
            </span>
          </div>
          <div className="mt-1 flex items-center justify-between text-[9px] text-white/18">
            <span>{formatCompactNumber(lane.reviewCoverage.inReviewHints)} in review</span>
            <span>{formatCompactNumber(lane.reviewCoverage.actionPendingHints)} awaiting decision</span>
          </div>
        </div>
      )}

      {type === "generation" && embedding?.enabled && (
        <div className="border-t border-white/[.04] px-4 py-3">
          <div className="mb-2 flex items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-2">
              <Binary size={13} className="shrink-0 text-violet-400/70" />
              <span className="text-[11px] font-medium text-white/45">
                Local vectors
              </span>
              <span className="truncate text-[10px] text-white/20">
                {shortenModelName(embedding.model)} · {embedding.dimensions}d
              </span>
            </div>
            <Badge
              variant={embedding.available ? "success" : "warning"}
              dot
              className="shrink-0 text-[9px]"
            >
              {embedding.available ? "Ready" : "Offline"}
            </Badge>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-white/[.06]">
            <div
              className="h-full rounded-full bg-violet-400/50 shadow-[0_0_8px_rgba(167,139,250,0.25)] transition-all"
              style={{ width: `${Math.min(100, embedding.coveragePercent)}%` }}
            />
          </div>
          <div className="mt-1.5 flex items-center justify-between text-[10px]">
            <span className="tabular-nums text-white/35">
              {formatCompactNumber(embedding.embeddedHints)} /{" "}
              {formatCompactNumber(embedding.activeHints)} ready
            </span>
            <span
              className={cn(
                "tabular-nums",
                embedding.missingHints > 0
                  ? "text-amber-400/60"
                  : "text-emerald-400/60",
              )}
            >
              {embedding.coveragePercent.toFixed(1)}%
            </span>
          </div>
          <div className="mt-1 flex items-center justify-between text-[9px] text-white/18">
            <span>{formatCompactNumber(embedding.missingHints)} pending</span>
            <span>
              related {embedding.relatedThreshold.toFixed(2)} · duplicate{" "}
              {embedding.duplicateThreshold.toFixed(2)}
            </span>
          </div>
        </div>
      )}

      <div className="border-t border-white/[.04] px-4 py-1.5 text-right">
        <span className="text-[10px] text-white/12">
          Reset {formatShortDateTime(q.nextResetAt)}
        </span>
      </div>
    </div>
  );
}
