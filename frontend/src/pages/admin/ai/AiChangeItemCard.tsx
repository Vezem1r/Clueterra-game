import { useMemo } from "react";
import { AlertTriangle, Check, ShieldAlert, ShieldCheck } from "lucide-react";

import type { AiChangeItem } from "@/api/admin";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { cn } from "@/utils/cn";

import {
  camelToLabel,
  canApproveItem,
  canRejectItem,
  getDiffKeys,
  getSnapshotEntries,
  getStatusVariant,
  isTerminalItemStatus,
  renderValue,
} from "./ai-helpers";

const ITEM_TYPE_BORDER: Record<string, string> = {
  HINT_CREATE: "border-l-emerald-400/40",
  HINT_UPDATE: "border-l-amber-400/40",
  HINT_DISABLE: "border-l-red-400/40",
  IMAGE_REPLACE: "border-l-sky-400/40",
  HINT_ESCALATE: "border-l-violet-400/60",
};

const ITEM_TYPE_LABEL: Record<string, string> = {
  HINT_CREATE: "New hint",
  HINT_UPDATE: "Text edit",
  HINT_DISABLE: "Disable",
  IMAGE_REPLACE: "Image replace",
  HINT_ESCALATE: "Needs review",
};

const ITEM_TYPE_VARIANT: Record<string, "warning" | "neutral"> = {
  HINT_ESCALATE: "warning",
};

function itemTypeLabel(item: AiChangeItem) {
  if (item.itemType !== "HINT_UPDATE") {
    return ITEM_TYPE_LABEL[item.itemType] ?? item.itemType;
  }
  const textChanged = item.originalSnapshot?.hintText !== item.proposedSnapshot?.hintText;
  const difficultyChanged = item.originalSnapshot?.difficulty !== item.proposedSnapshot?.difficulty;
  if (!textChanged && difficultyChanged) return "Difficulty edit";
  if (textChanged && difficultyChanged) return "Text & difficulty";
  return "Text edit";
}

function SnapshotPanel({
  title,
  snapshot,
  accent,
  diffKeys,
}: {
  title: string;
  snapshot: Record<string, unknown> | null | undefined;
  accent: "neutral" | "green";
  diffKeys?: Set<string>;
}) {
  const entries = getSnapshotEntries(snapshot);
  const borderColor =
    accent === "green"
      ? "border-emerald-500/15 bg-emerald-500/[.02]"
      : "border-white/[.05]";

  return (
    <div className={cn("rounded-lg border bg-black/15 p-3", borderColor)}>
      <p className="mb-2 text-[10px] uppercase tracking-[0.14em] text-white/25">
        {title}
      </p>
      {entries.length === 0 ? (
        <p className="text-xs text-white/25">Empty</p>
      ) : (
        <div className="space-y-1.5">
          {entries.map(([key, value]) => {
            const isDiff = diffKeys?.has(key);
            return (
              <div
                key={key}
                className={cn(
                  isDiff &&
                    "rounded-md border-l-2 border-l-amber-400/40 bg-amber-400/[.03] pl-2",
                )}
              >
                <p className="text-[10px] text-white/20">{camelToLabel(key)}</p>
                <p className="break-words text-xs text-white/70">
                  {renderValue(value)}
                </p>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface AiChangeItemCardProps {
  item: AiChangeItem;
  onApprove: (itemId: string) => void;
  onReject: (itemId: string) => void;
  isApproving: boolean;
  isRejecting: boolean;
}

export function AiChangeItemCard({
  item,
  onApprove,
  onReject,
  isApproving,
  isRejecting,
}: AiChangeItemCardProps) {
  const diffKeys = useMemo(
    () => getDiffKeys(item.originalSnapshot, item.proposedSnapshot),
    [item.originalSnapshot, item.proposedSnapshot],
  );

  const borderLeft = ITEM_TYPE_BORDER[item.itemType] ?? "border-l-white/10";
  const textValidation = item.itemType === "HINT_UPDATE"
    ? item.proposedSnapshot?.autoApplyEligible
    : undefined;
  const validationSummary = item.proposedSnapshot?.reviewValidationSummary;

  return (
    <div
      className={cn(
        "rounded-lg border border-white/[.05] border-l-2 bg-white/[.015] p-3",
        borderLeft,
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge variant={getStatusVariant(item.status)} dot>
              {item.status}
            </Badge>
            <Badge variant={ITEM_TYPE_VARIANT[item.itemType] ?? "neutral"}>
              {itemTypeLabel(item)}
            </Badge>
            {textValidation === true && (
              <span title={typeof validationSummary === "string" ? validationSummary : "Advisory edit checks passed"}>
                <Badge variant="success">
                  <ShieldCheck size={11} />
                  Edit check passed
                </Badge>
              </span>
            )}
            {textValidation === false && (
              <span title={typeof validationSummary === "string" ? validationSummary : "Edit check flagged this proposal"}>
                <Badge variant="warning">
                  <ShieldAlert size={11} />
                  Edit check flagged
                </Badge>
              </span>
            )}
            {(item.countryId || item.hintId) && (
              <span className="text-[11px] font-mono text-white/25">
                {item.countryId && `country:${item.countryId}`}
                {item.countryId && item.hintId && " / "}
                {item.hintId && `hint:${item.hintId}`}
              </span>
            )}
          </div>
          {item.reasonSummary && (
            <p className="mt-2 text-sm text-white/80">{item.reasonSummary}</p>
          )}
          {item.diffSummary && (
            <p className="mt-1 text-xs text-white/40">{item.diffSummary}</p>
          )}
        </div>
        {!isTerminalItemStatus(item.status) && (
          <div className="flex shrink-0 items-center gap-1.5">
            {canApproveItem(item.status) && item.proposedSnapshot?.autoRejected !== true && (
              <Button
                variant="outline"
                size="sm"
                isLoading={isApproving}
                onClick={() => onApprove(item.id)}
              >
                <Check size={13} />
                Approve
              </Button>
            )}
            {canRejectItem(item.status) && (
              <Button
                variant="danger"
                size="sm"
                isLoading={isRejecting}
                onClick={() => onReject(item.id)}
              >
                <AlertTriangle size={13} />
                Reject
              </Button>
            )}
          </div>
        )}
      </div>

      {(item.originalSnapshot || item.proposedSnapshot) && (
        <div
          className={cn(
            "mt-3 grid gap-2",
            item.originalSnapshot && item.proposedSnapshot
              ? "lg:grid-cols-2"
              : "",
          )}
        >
          {item.originalSnapshot && (
            <SnapshotPanel
              title="Original"
              snapshot={item.originalSnapshot}
              accent="neutral"
              diffKeys={diffKeys}
            />
          )}
          {item.proposedSnapshot && (
            <SnapshotPanel
              title="Proposed"
              snapshot={item.proposedSnapshot}
              accent="green"
              diffKeys={diffKeys}
            />
          )}
        </div>
      )}
    </div>
  );
}
