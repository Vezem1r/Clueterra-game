import { Check, Clock, Eye, Loader2, X } from "lucide-react";

import type { AiRunDetailResponse, AiRunStage } from "@/api/admin";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { cn } from "@/utils/cn";

import {
  formatCompactNumber,
  formatDateTime,
  formatDuration,
  formatNumber,
  getSummaryEntries,
  getStatusVariant,
} from "./ai-helpers";

interface AiRunDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  data: AiRunDetailResponse | undefined;
  isLoading: boolean;
  onOpenChangeSet: (changeSetId: string) => void;
}

function MetricBox({
  label,
  value,
}: {
  label: string;
  value: string | number;
}) {
  return (
    <div className="rounded-lg border border-white/[.06] bg-white/[.02] px-3 py-2.5 text-center">
      <p className="text-lg font-semibold tabular-nums text-white/85">
        {value}
      </p>
      <p className="mt-0.5 text-[10px] uppercase tracking-[0.08em] text-white/25">
        {label}
      </p>
    </div>
  );
}

function SummaryGrid({
  data,
}: {
  data: Record<string, unknown> | null | undefined;
}) {
  const entries = getSummaryEntries(data);
  const REDUNDANT_KEYS = new Set([
    "changedItems",
    "generatedItems",
    "processedItems",
  ]);
  const filtered = entries.filter((e) => !REDUNDANT_KEYS.has(e.key));
  if (filtered.length === 0) return null;

  return (
    <div className="rounded-lg border border-white/[.05] bg-white/[.015] px-4 py-3">
      <p className="mb-2.5 text-[10px] uppercase tracking-[0.12em] text-white/20">
        Summary
      </p>
      <div className="grid grid-cols-2 gap-x-6 gap-y-2.5 sm:grid-cols-3">
        {filtered.map(({ key, label, value }) => (
          <div key={key} className="min-w-0">
            <p className="text-[10px] text-white/25">{label}</p>
            <p
              className="mt-0.5 truncate text-[13px] text-white/70"
              title={value}
            >
              {value}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}

function StageNode({
  stage,
  isLast,
}: {
  stage: AiRunStage;
  isLast: boolean;
}) {
  const summary = stage.resultSummary ?? stage.payload ?? {};
  const entries = getSummaryEntries(summary);
  const isCompleted = stage.status === "COMPLETED";
  const isRunning = stage.status === "RUNNING";
  const isFailed = stage.status === "FAILED" || stage.status === "ERROR";

  return (
    <div className="relative flex gap-3 pb-4 last:pb-0">
      {!isLast && (
        <div className="absolute left-[10px] top-[26px] bottom-0 w-px bg-white/[.08]" />
      )}
      <div className="relative z-10 mt-1 flex shrink-0">
        <div
          className={cn(
            "flex h-[22px] w-[22px] items-center justify-center rounded-full border-2",
            isCompleted &&
              "border-emerald-400/50 bg-emerald-400/10 text-emerald-400",
            isRunning && "border-accent/50 bg-accent/10 text-accent",
            isFailed && "border-red-400/50 bg-red-400/10 text-red-400",
            !isCompleted &&
              !isRunning &&
              !isFailed &&
              "border-white/20 bg-white/[.04] text-white/30",
          )}
        >
          {isCompleted && <Check size={10} />}
          {isRunning && <Loader2 size={10} className="animate-spin" />}
          {isFailed && <X size={10} />}
        </div>
      </div>

      <div className="flex-1 min-w-0 rounded-lg border border-white/[.05] bg-white/[.015] p-3.5">
        <div className="flex flex-wrap items-center justify-between gap-y-1.5">
          <div className="flex items-center gap-2 min-w-0">
            <Badge
              variant={getStatusVariant(stage.status)}
              dot
              className="text-[10px]"
            >
              {stage.status}
            </Badge>
            <span className="text-[11px] font-medium text-white/50 truncate">
              {stage.stageType}
            </span>
            <span className="text-[11px] text-white/20 shrink-0">
              #{stage.stageIndex}
            </span>
          </div>
          <div className="flex items-center gap-3 text-[11px] font-mono tabular-nums text-white/35">
            <span>{formatNumber(stage.itemCount)} items</span>
            <span>
              {formatCompactNumber(
                stage.actualInputTokens || stage.estimatedInputTokens,
              )}{" "}
              in
            </span>
            <span>
              {formatCompactNumber(
                stage.actualOutputTokens || stage.estimatedOutputTokens,
              )}{" "}
              out
            </span>
          </div>
        </div>

        {stage.errorMessage && (
          <div className="mt-2 rounded-md border border-red-500/15 bg-red-500/[.06] px-3 py-1.5 text-[11px] text-red-300/80">
            {stage.errorMessage}
          </div>
        )}

        {entries.length > 0 && (
          <div className="mt-3 grid grid-cols-2 gap-x-5 gap-y-2 border-t border-white/[.04] pt-3 sm:grid-cols-3">
            {entries.map(({ key, label, value }) => (
              <div key={key} className="min-w-0">
                <p className="text-[10px] text-white/20">{label}</p>
                <p
                  className="mt-0.5 truncate text-[12px] leading-snug text-white/60"
                  title={value}
                >
                  {value}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export function AiRunDetailModal({
  isOpen,
  onClose,
  data,
  isLoading,
  onOpenChangeSet,
}: AiRunDetailModalProps) {
  const run = data?.run;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={run ? `${run.runType} Run` : "Run Details"}
      className="max-w-3xl"
    >
      {!data && isLoading && (
        <div className="flex items-center gap-3 py-8 text-sm text-white/40">
          <Loader2 size={16} className="animate-spin" />
          Loading...
        </div>
      )}
      {run && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge variant={getStatusVariant(run.status)} dot>
              {run.status}
            </Badge>
            <Badge variant="neutral">{run.triggerType}</Badge>
            {run.requestedBy && (
              <span className="text-[11px] text-white/30">
                by {run.requestedBy}
              </span>
            )}
          </div>

          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <MetricBox label="Items" value={formatNumber(run.processedItems)} />
            <MetricBox label="Changes" value={formatNumber(run.changedItems)} />
            <MetricBox
              label="Input tok"
              value={formatCompactNumber(
                run.actualInputTokens || run.estimatedInputTokens,
              )}
            />
            <MetricBox
              label="Output tok"
              value={formatCompactNumber(
                run.actualOutputTokens || run.estimatedOutputTokens,
              )}
            />
          </div>

          <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-xs sm:grid-cols-4">
            <div>
              <p className="text-white/25">Model</p>
              <p className="mt-0.5 text-white/70">{run.model}</p>
            </div>
            <div>
              <p className="text-white/25">Duration</p>
              <p className="mt-0.5 text-white/70">
                {formatDuration(run.createdAt, run.completedAt)}
              </p>
            </div>
            <div>
              <p className="text-white/25">Generated</p>
              <p className="mt-0.5 text-white/70">
                {formatNumber(run.generatedItems)}
              </p>
            </div>
            <div className="flex items-end">
              <div className="flex items-center gap-1.5 text-[11px] text-white/25">
                <Clock size={11} />
                {formatDateTime(run.createdAt)}
              </div>
            </div>
          </div>

          {run.errorMessage && (
            <div className="rounded-md border border-red-500/15 bg-red-500/[.06] px-3 py-2 text-[12px] leading-relaxed text-red-300/80">
              {run.errorMessage}
            </div>
          )}

          <SummaryGrid data={run.summary} />

          {data.stages.length > 0 && (
            <div>
              <div className="mb-3 flex items-center gap-2">
                <p className="text-[12px] font-medium uppercase tracking-[0.06em] text-white/30">
                  Stages
                </p>
                <span className="rounded bg-white/[.06] px-1.5 py-0.5 text-[10px] tabular-nums text-white/35">
                  {data.stages.length}
                </span>
              </div>
              <div className="relative">
                {data.stages.map((stage, i) => (
                  <StageNode
                    key={stage.id}
                    stage={stage}
                    isLast={i === data.stages.length - 1}
                  />
                ))}
              </div>
            </div>
          )}

          {data.changeSet && (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-white/[.05] border-l-2 border-l-emerald-400/30 bg-white/[.015] px-4 py-3">
              <div className="flex items-center gap-2 min-w-0">
                <Badge
                  variant={getStatusVariant(data.changeSet.status)}
                  dot
                  className="text-[10px] shrink-0"
                >
                  {data.changeSet.status}
                </Badge>
                <span className="text-[13px] text-white/70 truncate">
                  {data.changeSet.title}
                </span>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => onOpenChangeSet(data.changeSet!.id)}
              >
                <Eye size={13} />
                Open
              </Button>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
