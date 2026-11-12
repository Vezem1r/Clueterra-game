import { useMemo, useState } from "react";
import { AlertTriangle, Check, Loader2 } from "lucide-react";

import type { AiChangeItem, AiChangeSetDetailResponse } from "@/api/admin";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Tabs } from "@/components/ui/Tabs";

import { formatNumber, getStatusVariant, isAwaitingApply } from "./ai-helpers";
import { AiChangeItemCard } from "./AiChangeItemCard";
import { AiProposalCard } from "./AiProposalCard";

interface AiChangeSetDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  data: AiChangeSetDetailResponse | undefined;
  isLoading: boolean;
  onApplyChangeSet: () => void;
  onApproveAll: (itemTypes: string[]) => void;
  onRejectAll: (itemTypes?: string[]) => void;
  isApplying: boolean;
  isApprovingAll: boolean;
  isRejectingAll: boolean;
  onApproveItem: (itemId: string) => void;
  onRejectItem: (itemId: string) => void;
  pendingItemActionId: string | null;
  isApprovingItem: boolean;
  isRejectingItem: boolean;
}

type ReviewTab = "attention" | "edits";

const GENERATION_TYPES = new Set(["GENERATION", "HINT_GENERATION"]);
const ATTENTION_TYPES = ["HINT_DISABLE", "HINT_ESCALATE", "IMAGE_REPLACE"];
const EDIT_TYPES = ["HINT_UPDATE"];
const ATTENTION_TYPE_SET = new Set(ATTENTION_TYPES);
const EMPTY_ITEMS: AiChangeItem[] = [];

export function AiChangeSetDetailModal({
  isOpen,
  onClose,
  data,
  isLoading,
  onApplyChangeSet,
  onApproveAll,
  onRejectAll,
  isApplying,
  isApprovingAll,
  isRejectingAll,
  onApproveItem,
  onRejectItem,
  pendingItemActionId,
  isApprovingItem,
  isRejectingItem,
}: AiChangeSetDetailModalProps) {
  const cs = data?.changeSet;
  const isGeneration = cs ? GENERATION_TYPES.has(cs.changeSetType) : false;
  const items = data?.items ?? EMPTY_ITEMS;
  const attentionItems = useMemo(
    () => items.filter((item) => ATTENTION_TYPE_SET.has(item.itemType)),
    [items],
  );
  const editItems = useMemo(
    () => items.filter((item) => !ATTENTION_TYPE_SET.has(item.itemType)),
    [items],
  );
  const [reviewTabSelection, setReviewTabSelection] = useState<{
    changeSetId: string;
    tab: ReviewTab;
  } | null>(null);
  const defaultReviewTab: ReviewTab = attentionItems.length > 0 ? "attention" : "edits";
  const reviewTab = reviewTabSelection && reviewTabSelection.changeSetId === cs?.id
    ? reviewTabSelection.tab
    : defaultReviewTab;

  const visibleReviewItems = reviewTab === "attention" ? attentionItems : editItems;
  const activeReviewTypes = reviewTab === "attention" ? ATTENTION_TYPES : EDIT_TYPES;
  const footerItems = isGeneration ? items : visibleReviewItems;
  const pendingCount = footerItems.filter((item) => item.status === "PENDING").length;
  const approvedCount = footerItems.filter((item) => item.status === "APPROVED").length;
  const rejectedCount = footerItems.filter((item) => item.status === "REJECTED").length;
  const openCount = footerItems.filter((item) => isAwaitingApply(item.status)).length;

  const footerSummary = `${pendingCount} pending / ${approvedCount} approved / ${rejectedCount} rejected`;
  const actionFooter = cs && openCount > 0 ? (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-xs text-white/40">{footerSummary}</p>
      <div className="flex flex-wrap gap-2">
        {pendingCount > 0 && (
          <Button
            variant="danger"
            size="sm"
            onClick={() => onRejectAll(isGeneration ? undefined : activeReviewTypes)}
            isLoading={isRejectingAll}
          >
            <AlertTriangle size={13} />
            Reject pending
          </Button>
        )}
        {!isGeneration && pendingCount > 0 && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => onApproveAll(activeReviewTypes)}
            isLoading={isApprovingAll}
          >
            <Check size={13} />
            Approve pending
          </Button>
        )}
        {(isGeneration || approvedCount > 0) && (
          <Button size="sm" onClick={onApplyChangeSet} isLoading={isApplying}>
            <Check size={13} />
            {isGeneration && pendingCount > 0 ? "Apply all" : "Apply approved"}
          </Button>
        )}
      </div>
    </div>
  ) : undefined;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={cs ? (isGeneration ? "Generated Hints" : "Review Changes") : "Change Set"}
      className={isGeneration ? "max-w-4xl" : "max-w-5xl"}
      footer={actionFooter}
    >
      {!data && isLoading && (
        <div className="flex items-center gap-3 text-sm text-white/55">
          <Loader2 size={16} className="animate-spin" />
          Loading change set...
        </div>
      )}
      {cs && data && (
        <div className="space-y-4">
          <div>
            <div className="flex flex-wrap items-center gap-1.5">
              <Badge variant={getStatusVariant(cs.status)} dot>{cs.status}</Badge>
              <Badge variant="neutral">{cs.changeSetType}</Badge>
            </div>
            <h3 className="mt-2 text-[15px] font-semibold text-white">{cs.title}</h3>
            {cs.summary && <p className="mt-1 text-xs text-white/40">{cs.summary}</p>}
          </div>

          <div className="grid grid-cols-3 gap-2">
            <Metric value={cs.totalItems} label="Total" className="text-white/85" />
            <Metric value={cs.appliedItems} label="Applied" className="text-emerald-400" />
            <Metric value={cs.rejectedItems} label="Rejected" className="text-red-400" />
          </div>

          {isGeneration ? (
            <GenerationContent
              data={data}
              onApproveItem={onApproveItem}
              onRejectItem={onRejectItem}
              pendingItemActionId={pendingItemActionId}
              isApprovingItem={isApprovingItem}
              isRejectingItem={isRejectingItem}
            />
          ) : (
            <>
              <Tabs
                tabs={[
                  { id: "attention", label: "Needs decision", shortLabel: "Decision", count: attentionItems.length },
                  { id: "edits", label: "Text & difficulty", shortLabel: "Edits", count: editItems.length },
                ]}
                activeTab={reviewTab}
                onChange={(tab) => cs && setReviewTabSelection({
                  changeSetId: cs.id,
                  tab: tab as ReviewTab,
                })}
              />
              <ReviewContent
                items={visibleReviewItems}
                emptyMessage={reviewTab === "attention"
                  ? "No disable, escalation, or image decisions need attention."
                  : "No text or difficulty edits in this review."}
                onApproveItem={onApproveItem}
                onRejectItem={onRejectItem}
                pendingItemActionId={pendingItemActionId}
                isApprovingItem={isApprovingItem}
                isRejectingItem={isRejectingItem}
              />
            </>
          )}
        </div>
      )}
    </Modal>
  );
}

function Metric({ value, label, className }: { value: number; label: string; className: string }) {
  return (
    <div className="rounded-lg border border-white/[.06] bg-white/[.02] px-3 py-2.5 text-center">
      <p className={`text-lg font-semibold tabular-nums ${className}`}>{formatNumber(value)}</p>
      <p className="mt-0.5 text-[10px] uppercase tracking-[0.08em] text-white/25">{label}</p>
    </div>
  );
}

interface ContentProps {
  onApproveItem: (itemId: string) => void;
  onRejectItem: (itemId: string) => void;
  pendingItemActionId: string | null;
  isApprovingItem: boolean;
  isRejectingItem: boolean;
}

function ReviewContent({
  items,
  emptyMessage,
  onApproveItem,
  onRejectItem,
  pendingItemActionId,
  isApprovingItem,
  isRejectingItem,
}: ContentProps & { items: AiChangeItem[]; emptyMessage: string }) {
  if (items.length === 0) {
    return <p className="rounded-lg border border-white/[.05] bg-white/[.015] p-5 text-sm text-white/35">{emptyMessage}</p>;
  }
  return (
    <div className="space-y-2">
      {items.map((item) => (
        <AiChangeItemCard
          key={item.id}
          item={item}
          onApprove={onApproveItem}
          onReject={onRejectItem}
          isApproving={isApprovingItem && pendingItemActionId === item.id}
          isRejecting={isRejectingItem && pendingItemActionId === item.id}
        />
      ))}
    </div>
  );
}

function GenerationContent({
  data,
  onApproveItem,
  onRejectItem,
  pendingItemActionId,
  isApprovingItem,
  isRejectingItem,
}: ContentProps & { data: AiChangeSetDetailResponse }) {
  const proposals = data.proposals;
  const items = data.items;
  const itemByProposalId = new Map(items.map((item) => [item.proposalId, item]));
  if (proposals.length === 0 && items.length === 0) return null;

  return (
    <div className="space-y-2">
      {proposals.length > 0
        ? proposals.map((proposal) => {
            const linkedItem = itemByProposalId.get(proposal.id);
            return (
              <AiProposalCard
                key={proposal.id}
                proposal={proposal}
                linkedItem={linkedItem}
                onApprove={linkedItem ? () => onApproveItem(linkedItem.id) : undefined}
                onReject={linkedItem ? () => onRejectItem(linkedItem.id) : undefined}
                isApproving={isApprovingItem && pendingItemActionId === linkedItem?.id}
                isRejecting={isRejectingItem && pendingItemActionId === linkedItem?.id}
              />
            );
          })
        : items.map((item) => (
            <AiChangeItemCard
              key={item.id}
              item={item}
              onApprove={onApproveItem}
              onReject={onRejectItem}
              isApproving={isApprovingItem && pendingItemActionId === item.id}
              isRejecting={isRejectingItem && pendingItemActionId === item.id}
            />
          ))}
    </div>
  );
}
