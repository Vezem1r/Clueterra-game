import {
  AlertTriangle,
  Check,
  Globe,
  MapPin,
  Search,
  ShieldAlert,
  ShieldCheck,
  Tag,
} from "lucide-react";

import type { AiChangeItem, AiGeneratedHintProposal } from "@/api/admin";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { cn } from "@/utils/cn";

import {
  canApproveItem,
  canRejectItem,
  formatPercent,
  getQualityColor,
  getStatusVariant,
} from "./ai-helpers";

interface AiProposalCardProps {
  proposal: AiGeneratedHintProposal;
  linkedItem?: AiChangeItem;
  onApprove?: () => void;
  onReject?: () => void;
  isApproving?: boolean;
  isRejecting?: boolean;
}

function DifficultyChip({ value }: { value: number | null }) {
  if (value == null) return <span className="text-white/30">--</span>;
  const color =
    value <= 30
      ? "text-emerald-400"
      : value <= 60
        ? "text-amber-400"
        : "text-red-400";

  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={cn("text-sm font-semibold tabular-nums", color)}>
        {value}
      </span>
      <span className="h-1 w-8 overflow-hidden rounded-full bg-white/[.08]">
        <span
          className={cn(
            "block h-full rounded-full",
            value <= 30
              ? "bg-emerald-400"
              : value <= 60
                ? "bg-amber-400"
                : "bg-red-400",
          )}
          style={{ width: `${value}%` }}
        />
      </span>
    </span>
  );
}

export function AiProposalCard({
  proposal,
  linkedItem,
  onApprove,
  onReject,
  isApproving,
  isRejecting,
}: AiProposalCardProps) {
  const itemStatus = linkedItem?.status ?? proposal.status;
  const showApprove = canApproveItem(itemStatus) && onApprove;
  const showReject = canRejectItem(itemStatus) && onReject;
  const embeddingValidation = linkedItem?.proposedSnapshot?.embeddingValidated;
  const showEmbeddingWarning =
    canApproveItem(itemStatus) && embeddingValidation !== true;
  const imageValidation = linkedItem?.proposedSnapshot?.imageValidated;
  const imageKind = linkedItem?.proposedSnapshot?.imageKind;
  const imageValidationSummary =
    linkedItem?.proposedSnapshot?.imageValidationSummary;
  const imageVisualDescription =
    linkedItem?.proposedSnapshot?.imageVisualDescription;

  return (
    <div className="rounded-lg border border-white/[.05] border-l-2 border-l-emerald-400/30 bg-white/[.015] p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge variant={getStatusVariant(itemStatus)} dot>
              {itemStatus}
            </Badge>
            {proposal.category && (
              <span className="inline-flex items-center gap-1 rounded-md bg-white/[.04] px-2 py-0.5 text-[11px] text-white/50">
                <Tag size={10} className="text-white/25" />
                {proposal.category}
              </span>
            )}
            {proposal.hintType && (
              <span className="inline-flex items-center gap-1 rounded-md bg-white/[.04] px-2 py-0.5 text-[11px] text-white/50">
                {proposal.hintType}
              </span>
            )}
            {showEmbeddingWarning && (
              <span title="Legacy proposal: the final hint text did not pass mandatory local embedding validation">
                <Badge variant="warning">
                  <ShieldAlert size={11} />
                  Embedding check missing
                </Badge>
              </span>
            )}
            {embeddingValidation === true && (
              <span title="Checked against same-country hints using a similarity threshold; this does not guarantee a unique fact">
                <Badge variant="info">
                  <ShieldCheck size={11} />
                  Embedding checked
                </Badge>
              </span>
            )}
            {imageValidation === true && (
              <span
                title={
                  typeof imageValidationSummary === "string"
                    ? imageValidationSummary
                    : "Gemini inspected the actual image pixels"
                }
              >
                <Badge variant="success">
                  <ShieldCheck size={11} />
                  {typeof imageKind === "string" ? imageKind : "Image"} checked
                </Badge>
              </span>
            )}
          </div>

          <p className="mt-3 text-[15px] leading-relaxed text-white/90">
            {proposal.hintText}
          </p>
        </div>

        {(showApprove || showReject) && (
          <div className="flex shrink-0 items-center gap-1.5">
            {showApprove && (
              <Button
                variant="outline"
                size="sm"
                isLoading={isApproving}
                onClick={onApprove}
              >
                <Check size={13} />
                Approve
              </Button>
            )}
            {showReject && (
              <Button
                variant="danger"
                size="sm"
                isLoading={isRejecting}
                onClick={onReject}
              >
                <AlertTriangle size={13} />
                Reject
              </Button>
            )}
          </div>
        )}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3 text-xs">
        <div className="inline-flex items-center gap-1.5">
          <MapPin size={11} className="text-white/20" />
          <span className="font-medium text-white/70">
            {proposal.countryName ?? proposal.countryCode ?? proposal.countryId}
          </span>
          {proposal.countryCode && (
            <span className="text-[10px] text-white/30">{proposal.countryCode}</span>
          )}
        </div>

        <div className="h-3 w-px bg-white/[.08]" />

        <div className="inline-flex items-center gap-1.5">
          <span className="text-white/30">Difficulty</span>
          <DifficultyChip value={proposal.difficulty} />
        </div>

        <div className="h-3 w-px bg-white/[.08]" />

        <div className="inline-flex items-center gap-1.5">
          <span className="text-white/30">Quality</span>
          <span
            className={cn(
              "text-sm font-semibold tabular-nums",
              getQualityColor(proposal.qualityScore),
            )}
          >
            {formatPercent(proposal.qualityScore)}
          </span>
        </div>

        {proposal.source && (
          <>
            <div className="h-3 w-px bg-white/[.08]" />
            <div className="inline-flex items-center gap-1.5">
              <Globe size={11} className="text-white/20" />
              <span className="font-mono text-[11px] text-white/40">
                {proposal.source}
              </span>
              {proposal.sourceYear && (
                <span className="text-white/25">{proposal.sourceYear}</span>
              )}
            </div>
          </>
        )}
      </div>

      {(proposal.factSummary ||
        proposal.evidenceSummary ||
        proposal.duplicateCheckSummary ||
        typeof imageVisualDescription === "string") && (
        <div className="mt-3 space-y-1.5 rounded-lg border border-white/[.04] bg-black/15 p-3 text-xs">
          {proposal.factSummary && (
            <p className="text-white/55">
              <span className="mr-1.5 inline-flex items-center rounded bg-white/[.06] px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wider text-white/30">
                Fact
              </span>
              {proposal.factSummary}
            </p>
          )}
          {proposal.evidenceSummary && (
            <p className="text-white/55">
              <span className="mr-1.5 inline-flex items-center rounded bg-white/[.06] px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wider text-white/30">
                Evidence
              </span>
              {proposal.evidenceSummary}
            </p>
          )}
          {proposal.duplicateCheckSummary && (
            <p className="text-white/55">
              <span className="mr-1.5 inline-flex items-center rounded bg-white/[.06] px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wider text-white/30">
                Dup
              </span>
              {proposal.duplicateCheckSummary}
            </p>
          )}
          {typeof imageVisualDescription === "string" && (
            <p className="text-white/55">
              <span className="mr-1.5 inline-flex items-center rounded bg-white/[.06] px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wider text-white/30">
                Vision
              </span>
              {imageVisualDescription}
            </p>
          )}
        </div>
      )}

      {proposal.searchTerms.length > 0 && (
        <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
          <Search size={11} className="text-white/15" />
          {proposal.searchTerms.map((term) => (
            <span
              key={term}
              className="rounded-md bg-white/[.04] px-2 py-0.5 text-[11px] text-white/40"
            >
              {term}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
