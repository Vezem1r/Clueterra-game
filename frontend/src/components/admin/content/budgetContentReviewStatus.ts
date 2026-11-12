import type { BudgetContentReviewStatus } from '@/api/budgetContentAdmin'

export function isBudgetContentApproved(status: BudgetContentReviewStatus) {
  return status === 'APPROVED' || status === 'EDITORIAL_APPROVED'
}
