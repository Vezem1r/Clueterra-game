import { Bot, Play, RefreshCw, Sparkles } from "lucide-react";

import type { AiLaneOverviewResponse } from "@/api/admin";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { useTranslation } from "@/i18n";

import { shortenModelName } from "./ai-helpers";

interface AiHeaderProps {
  review: AiLaneOverviewResponse | undefined;
  generation: AiLaneOverviewResponse | undefined;
  promptVersion: string | undefined;
  activeRunsCount: number;
  onRefresh: () => void;
  onStartReview: () => void;
  onStartGeneration: () => void;
  isStartingReview: boolean;
  isStartingGeneration: boolean;
}

export function AiHeader({
  review,
  generation,
  promptVersion,
  activeRunsCount,
  onRefresh,
  onStartReview,
  onStartGeneration,
  isStartingReview,
  isStartingGeneration,
}: AiHeaderProps) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-bold tracking-tight text-white sm:text-2xl">
            {t.admin.ai}
          </h1>
          <Badge variant={activeRunsCount > 0 ? "info" : "neutral"} dot>
            {activeRunsCount > 0 ? `${activeRunsCount} active` : "Idle"}
          </Badge>
        </div>
        <div className="mt-2 flex items-center gap-0 divide-x divide-white/[.08] text-[11px] text-white/30">
          {promptVersion && (
            <span className="pr-3">
              prompt <span className="text-white/45">{promptVersion}</span>
            </span>
          )}
          {review && (
            <span className="px-3 inline-flex items-center gap-1">
              <Bot size={10} className="text-sky-400/50" />
              <span className="text-white/45">
                {shortenModelName(review.model)}
              </span>
              {!review.schedulerEnabled && (
                <span className="text-white/20">(manual)</span>
              )}
            </span>
          )}
          {generation && (
            <span className="pl-3 inline-flex items-center gap-1">
              <Sparkles size={10} className="text-emerald-400/50" />
              <span className="text-white/45">
                {shortenModelName(generation.model)}
              </span>
              {!generation.schedulerEnabled && (
                <span className="text-white/20">(manual)</span>
              )}
            </span>
          )}
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-1.5 rounded-xl border border-white/[.06] bg-white/[.03] p-1.5">
        <Button variant="ghost" size="sm" onClick={onRefresh}>
          <RefreshCw size={13} />
          Refresh
        </Button>
        <div className="h-5 w-px bg-white/[.08]" />
        <Button size="sm" onClick={onStartReview} isLoading={isStartingReview}>
          <Play size={13} />
          Start Review
        </Button>
        <Button
          variant="secondary"
          size="sm"
          onClick={onStartGeneration}
          isLoading={isStartingGeneration}
        >
          <Sparkles size={13} />
          Start Generation
        </Button>
      </div>
    </div>
  );
}
