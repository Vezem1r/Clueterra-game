export function formatNumber(value: number | null | undefined) {
  return new Intl.NumberFormat().format(value ?? 0);
}

export function formatCompactNumber(value: number | null | undefined) {
  const n = value ?? 0;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}k`;
  return String(n);
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  return new Date(value).toLocaleString();
}

export function formatShortDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const d = new Date(value);
  const month = d.toLocaleString("en", { month: "short" });
  const day = d.getDate();
  const hours = d.getHours().toString().padStart(2, "0");
  const minutes = d.getMinutes().toString().padStart(2, "0");
  return `${month} ${day}, ${hours}:${minutes}`;
}

export function formatPercent(value: number | null | undefined) {
  if (value == null) return "—";
  return `${Math.round(value)}%`;
}

export function formatDuration(
  start: string | null | undefined,
  end: string | null | undefined,
) {
  if (!start || !end) return "—";
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (ms < 0) return "—";
  const totalSeconds = Math.round(ms / 1000);
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${seconds}s`;
}

const ITEM_STATUS_PENDING = "PENDING";
const ITEM_STATUS_APPROVED = "APPROVED";
const ITEM_STATUS_REJECTED = "REJECTED";
const ITEM_STATUS_APPLIED = "APPLIED";
const ITEM_STATUS_CANCELLED = "CANCELLED";

export function isAwaitingApply(itemStatus: string) {
  return (
    itemStatus === ITEM_STATUS_PENDING || itemStatus === ITEM_STATUS_APPROVED
  );
}

export function isTerminalItemStatus(itemStatus: string) {
  return itemStatus === ITEM_STATUS_APPLIED || itemStatus === ITEM_STATUS_CANCELLED;
}

export function canApproveItem(itemStatus: string) {
  return (
    itemStatus === ITEM_STATUS_PENDING || itemStatus === ITEM_STATUS_REJECTED
  );
}

export function canRejectItem(itemStatus: string) {
  return isAwaitingApply(itemStatus);
}

export function getStatusVariant(status: string) {
  switch (status) {
    case "COMPLETED":
    case "APPLIED":
    case "APPROVED":
    case "ACTIVE":
    case "READY":
      return "success" as const;
    case "RUNNING":
    case "PREPARED":
    case "PENDING":
    case "DRAFT":
    case "PENDING_APPROVAL":
      return "info" as const;
    case "FAILED":
    case "REJECTED":
    case "ERROR":
      return "error" as const;
    case "SKIPPED":
    case "STALE":
    case "CANCELLED":
    case "DUPLICATE":
      return "warning" as const;
    default:
      return "neutral" as const;
  }
}

export function camelToLabel(key: string) {
  return key
    .replace(/([A-Z])/g, " $1")
    .replace(/^./, (s) => s.toUpperCase())
    .trim();
}

const SNAPSHOT_KEYS = [
  "hintText",
  "text",
  "difficulty",
  "qualityScore",
  "category",
  "hintType",
  "source",
  "sourceIndicator",
  "autoApplyEligible",
  "reviewValidationSummary",
  "retryAfterDays",
  "reviewRetryAfterAt",
  "semanticSimilarity",
  "embeddingValidated",
  "embeddingModel",
  "imageUrl",
  "storedImageUrl",
  "factSummary",
  "evidenceSummary",
];

export function getSnapshotEntries(
  snapshot: Record<string, unknown> | null | undefined,
) {
  if (!snapshot) return [];

  const preferred = SNAPSHOT_KEYS.filter((key) => key in snapshot).map(
    (key) => [key, snapshot[key]] as const,
  );

  const additional = Object.entries(snapshot)
    .filter(([key]) => !SNAPSHOT_KEYS.includes(key))
    .slice(0, 6);

  return [...preferred, ...additional];
}

export function renderValue(value: unknown) {
  if (value == null) return "—";
  if (Array.isArray(value)) return value.join(", ");
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

export function getSummaryEntries(
  summary: Record<string, unknown> | null | undefined,
): Array<{ key: string; label: string; value: string }> {
  if (!summary) return [];
  return Object.entries(summary).map(([key, val]) => ({
    key,
    label: camelToLabel(key),
    value: formatSummaryValue(key, val),
  }));
}

function formatSummaryValue(_key: string, value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "number") return formatNumber(value);
  if (typeof value === "object" && !Array.isArray(value)) {
    return Object.entries(value as Record<string, unknown>)
      .map(([k, v]) => `${k}: ${v}`)
      .join(", ");
  }
  if (Array.isArray(value)) return value.join(", ");
  return String(value);
}

export function formatRelativeTime(value: string | null | undefined): string {
  if (!value) return "--";
  const now = Date.now();
  const then = new Date(value).getTime();
  const diffMs = now - then;
  if (diffMs < 0) return "just now";
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHr = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHr / 24);

  if (diffSec < 60) return "just now";
  if (diffMin < 60) return `${diffMin}m ago`;
  if (diffHr < 24) return `${diffHr}h ago`;
  if (diffDay < 7) return `${diffDay}d ago`;
  return formatShortDateTime(value);
}

export function getStatusBarColor(status: string): string {
  switch (status) {
    case "COMPLETED":
    case "APPLIED":
    case "APPROVED":
      return "bg-emerald-400";
    case "RUNNING":
    case "PREPARED":
    case "PENDING":
    case "DRAFT":
      return "bg-accent";
    case "FAILED":
    case "REJECTED":
    case "ERROR":
      return "bg-red-400";
    case "SKIPPED":
    case "STALE":
      return "bg-amber-400";
    default:
      return "bg-white/20";
  }
}

export function getDiffKeys(
  original: Record<string, unknown> | null | undefined,
  proposed: Record<string, unknown> | null | undefined,
): Set<string> {
  if (!original || !proposed) return new Set();
  const changed = new Set<string>();
  for (const key of Object.keys(proposed)) {
    if (JSON.stringify(original[key]) !== JSON.stringify(proposed[key])) {
      changed.add(key);
    }
  }
  return changed;
}

export function getQualityColor(score: number | null | undefined): string {
  if (score == null) return "text-white/40";
  if (score >= 0.8) return "text-emerald-400";
  if (score >= 0.5) return "text-amber-400";
  return "text-red-400";
}

export function parseErrorMessage(
  raw: string | null | undefined,
): string | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (parsed?.error?.message) return parsed.error.message;
  } catch {
    // not JSON
  }
  return raw;
}

export function shortenModelName(model: string): string {
  return model
    .replace("gemini-", "")
    .replace("-preview", "")
    .replace("-lite", "-l");
}

export function getDateKey(dateStr: string | null | undefined) {
  if (!dateStr) return "unknown";
  const d = new Date(dateStr);
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

export function formatDateLabel(dateStr: string | null | undefined) {
  if (!dateStr) return "Unknown";
  const d = new Date(dateStr);
  const now = new Date();
  const today = `${now.getFullYear()}-${now.getMonth()}-${now.getDate()}`;
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  const yKey = `${yesterday.getFullYear()}-${yesterday.getMonth()}-${yesterday.getDate()}`;
  const key = getDateKey(dateStr);

  if (key === today) return "Today";
  if (key === yKey) return "Yesterday";
  return d.toLocaleDateString("en", { month: "short", day: "numeric" });
}

export function groupByDate<T>(
  items: T[],
  getDate: (item: T) => string | null | undefined,
): Array<{ label: string; items: T[] }> {
  const groups: Array<{ label: string; items: T[] }> = [];
  let currentKey = "";

  for (const item of items) {
    const key = getDateKey(getDate(item));
    if (key !== currentKey) {
      currentKey = key;
      groups.push({ label: formatDateLabel(getDate(item)), items: [] });
    }
    groups[groups.length - 1].items.push(item);
  }
  return groups;
}
