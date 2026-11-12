import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";

import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/authStore";
import { useToastStore } from "@/store/toastStore";
import { cn } from "@/utils/cn";

import { AiChangeSetDetailModal } from "./AiChangeSetDetailModal";
import { AiChangeSetsTab } from "./AiChangeSetsTab";
import { AiHeader } from "./AiHeader";
import { AiLaneCard } from "./AiLaneCard";
import { AiRunDetailModal } from "./AiRunDetailModal";
import { AiRunsTab } from "./AiRunsTab";

const REFETCH_INTERVAL = 15_000;
const PAGE_SIZE = 20;

export function AiPage() {
  const toast = useToastStore();
  const queryClient = useQueryClient();
  const { user } = useAuthStore();

  const [activeTab, setActiveTab] = useState<"runs" | "changes">("runs");
  const [runsPage, setRunsPage] = useState(0);
  const [changeSetsPage, setChangeSetsPage] = useState(0);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [selectedChangeSetId, setSelectedChangeSetId] = useState<string | null>(
    null,
  );
  const [pendingItemActionId, setPendingItemActionId] = useState<string | null>(
    null,
  );

  const overviewQuery = useQuery({
    queryKey: ["admin-ai-overview"],
    queryFn: adminApi.getAiOverview,
    refetchInterval: REFETCH_INTERVAL,
  });

  const runsQuery = useQuery({
    queryKey: ["admin-ai-runs", runsPage],
    queryFn: () => adminApi.getAiRuns(runsPage, PAGE_SIZE),
    refetchInterval: REFETCH_INTERVAL,
  });

  const changeSetsQuery = useQuery({
    queryKey: ["admin-ai-change-sets", changeSetsPage],
    queryFn: () => adminApi.getAiChangeSets(changeSetsPage, PAGE_SIZE),
    refetchInterval: REFETCH_INTERVAL,
  });

  const runDetailQuery = useQuery({
    queryKey: ["admin-ai-run-detail", selectedRunId],
    queryFn: () => adminApi.getAiRunDetail(selectedRunId!),
    enabled: !!selectedRunId,
    retry: false,
  });

  const changeSetDetailQuery = useQuery({
    queryKey: ["admin-ai-change-set-detail", selectedChangeSetId],
    queryFn: () => adminApi.getAiChangeSetDetail(selectedChangeSetId!),
    enabled: !!selectedChangeSetId,
    retry: false,
  });

  const refreshAll = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin-ai-overview"] }),
      queryClient.invalidateQueries({ queryKey: ["admin-ai-runs"] }),
      queryClient.invalidateQueries({ queryKey: ["admin-ai-change-sets"] }),
      queryClient.invalidateQueries({ queryKey: ["admin-ai-run-detail"] }),
      queryClient.invalidateQueries({
        queryKey: ["admin-ai-change-set-detail"],
      }),
    ]);
  };

  const startReviewMutation = useMutation({
    mutationFn: () => adminApi.startAiReview(user?.email),
    onSuccess: (response) => {
      toast.addToast({
        type: "success",
        message: response.message || "AI review run started",
      });
      void refreshAll();
      if (response.runId) {
        setSelectedRunId(response.runId);
        setActiveTab("runs");
      }
    },
    onError: (error) => {
      toast.addToast({
        type: "error",
        message:
          error instanceof Error
            ? error.message
            : "Failed to start AI review run",
      });
    },
  });

  const startGenerationMutation = useMutation({
    mutationFn: () => adminApi.startAiGeneration(user?.email),
    onSuccess: (response) => {
      toast.addToast({
        type: "success",
        message: response.message || "AI generation run started",
      });
      void refreshAll();
      if (response.runId) {
        setSelectedRunId(response.runId);
        setActiveTab("runs");
      }
    },
    onError: (error) => {
      toast.addToast({
        type: "error",
        message:
          error instanceof Error
            ? error.message
            : "Failed to start AI generation run",
      });
    },
  });

  const approveItemMutation = useMutation({
    mutationFn: async (itemId: string) => {
      setPendingItemActionId(itemId);
      return adminApi.approveAiChangeItem(selectedChangeSetId!, itemId);
    },
    onSuccess: (response) => {
      toast.addToast({
        type: "success",
        message: response.message || "Change approved",
      });
      void refreshAll();
    },
    onError: (error) => {
      toast.addToast({
        type: "error",
        message:
          error instanceof Error ? error.message : "Failed to approve change",
      });
    },
    onSettled: () => setPendingItemActionId(null),
  });

  const rejectItemMutation = useMutation({
    mutationFn: async (itemId: string) => {
      setPendingItemActionId(itemId);
      return adminApi.rejectAiChangeItem(selectedChangeSetId!, itemId);
    },
    onSuccess: (response) => {
      toast.addToast({
        type: "success",
        message: response.message || "Change rejected",
      });
      void refreshAll();
    },
    onError: (error) => {
      toast.addToast({
        type: "error",
        message:
          error instanceof Error ? error.message : "Failed to reject change",
      });
    },
    onSettled: () => setPendingItemActionId(null),
  });

  const applyChangeSetMutation = useMutation({
    mutationFn: () => adminApi.applyAiChangeSet(selectedChangeSetId!),
    onSuccess: (response) => {
      toast.addToast({
        type: "success",
        message: response.message || "Change set applied",
      });
      void refreshAll();
    },
    onError: (error) => {
      toast.addToast({
        type: "error",
        message:
          error instanceof Error ? error.message : "Failed to apply change set",
      });
    },
  });

  const rejectAllMutation = useMutation({
    mutationFn: (itemTypes?: string[]) =>
      adminApi.rejectAllAiChangeSet(selectedChangeSetId!, itemTypes),
    onSuccess: (response) => {
      toast.addToast({
        type: "success",
        message: response.message || "Change set rejected",
      });
      void refreshAll();
    },
    onError: (error) => {
      toast.addToast({
        type: "error",
        message:
          error instanceof Error
            ? error.message
            : "Failed to reject change set",
      });
    },
  });

  const approveAllMutation = useMutation({
    mutationFn: (itemTypes: string[]) =>
      adminApi.approveAllAiChangeSet(selectedChangeSetId!, itemTypes),
    onSuccess: (response) => {
      toast.addToast({
        type: "success",
        message: response.message || "Selected changes approved",
      });
      void refreshAll();
    },
    onError: (error) => {
      toast.addToast({
        type: "error",
        message:
          error instanceof Error
            ? error.message
            : "Failed to approve selected changes",
      });
    },
  });

  const overview = overviewQuery.data;
  const runsData = runsQuery.data;
  const changeSetsData = changeSetsQuery.data;

  const runs = runsData?.content ?? [];
  const activeRunsCount = runs.filter(
    (r) => r.status === "RUNNING" || r.status === "PREPARED",
  ).length;

  return (
    <div className="space-y-5 p-4 sm:p-5 lg:p-6">
      <AiHeader
        review={overview?.review}
        generation={overview?.generation}
        promptVersion={overview?.promptVersion}
        activeRunsCount={activeRunsCount}
        onRefresh={() => void refreshAll()}
        onStartReview={() => startReviewMutation.mutate()}
        onStartGeneration={() => startGenerationMutation.mutate()}
        isStartingReview={startReviewMutation.isPending}
        isStartingGeneration={startGenerationMutation.isPending}
      />

      {overview ? (
        <div className="grid gap-4 md:grid-cols-2">
          <AiLaneCard lane={overview.review} type="review" />
          <AiLaneCard
            lane={overview.generation}
            type="generation"
            embedding={overview.embedding}
          />
        </div>
      ) : overviewQuery.isLoading ? (
        <div className="flex items-center gap-3 rounded-xl border border-white/[.06] bg-white/[.02] p-5 text-sm text-white/40">
          <Loader2 size={16} className="animate-spin" />
          Loading AI overview...
        </div>
      ) : null}

      <div className="h-px bg-gradient-to-r from-transparent via-white/[.06] to-transparent" />

      <div className="rounded-xl border border-white/[.06] bg-white/[.02]">
        <div className="flex items-center gap-1 border-b border-white/[.04] px-3 py-2">
          {(["runs", "changes"] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={cn(
                "relative rounded-md px-4 py-1.5 text-[12px] font-medium transition-all cursor-pointer",
                activeTab === tab
                  ? "bg-white/[.08] text-white"
                  : "text-white/35 hover:text-white/55",
              )}
            >
              {tab === "runs" ? "Runs" : "Change Sets"}
              {activeTab === tab && (
                <span className="ml-1.5 text-[10px] tabular-nums text-white/40">
                  {tab === "runs"
                    ? (runsData?.totalElements ?? 0)
                    : (changeSetsData?.totalElements ?? 0)}
                </span>
              )}
            </button>
          ))}
        </div>

        <div className="p-3">
          {activeTab === "runs" && (
            <AiRunsTab
              runs={runs}
              isLoading={runsQuery.isLoading}
              page={runsPage}
              totalPages={runsData?.totalPages ?? 0}
              totalElements={runsData?.totalElements ?? 0}
              onPageChange={setRunsPage}
              onOpenRun={setSelectedRunId}
            />
          )}

          {activeTab === "changes" && (
            <AiChangeSetsTab
              changeSets={changeSetsData?.content ?? []}
              isLoading={changeSetsQuery.isLoading}
              page={changeSetsPage}
              totalPages={changeSetsData?.totalPages ?? 0}
              totalElements={changeSetsData?.totalElements ?? 0}
              onPageChange={setChangeSetsPage}
              onOpenChangeSet={setSelectedChangeSetId}
            />
          )}
        </div>
      </div>

      <AiRunDetailModal
        isOpen={!!selectedRunId}
        onClose={() => setSelectedRunId(null)}
        data={runDetailQuery.data}
        isLoading={runDetailQuery.isLoading}
        onOpenChangeSet={setSelectedChangeSetId}
      />

      <AiChangeSetDetailModal
        isOpen={!!selectedChangeSetId}
        onClose={() => setSelectedChangeSetId(null)}
        data={changeSetDetailQuery.data}
        isLoading={changeSetDetailQuery.isLoading}
        onApplyChangeSet={() => applyChangeSetMutation.mutate()}
        onApproveAll={(itemTypes) => approveAllMutation.mutate(itemTypes)}
        onRejectAll={(itemTypes) => rejectAllMutation.mutate(itemTypes)}
        isApplying={applyChangeSetMutation.isPending}
        isRejectingAll={rejectAllMutation.isPending}
        isApprovingAll={approveAllMutation.isPending}
        onApproveItem={(itemId) => approveItemMutation.mutate(itemId)}
        onRejectItem={(itemId) => rejectItemMutation.mutate(itemId)}
        pendingItemActionId={pendingItemActionId}
        isApprovingItem={approveItemMutation.isPending}
        isRejectingItem={rejectItemMutation.isPending}
      />
    </div>
  );
}
