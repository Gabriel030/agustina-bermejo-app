import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useFinancialDashboard() {
  return useQuery({
    queryKey: ['financial-dashboard'],
    queryFn: () => apiFetch('/financial-dashboard'),
  });
}

export const DEBTORS_PAGE_SIZE = 25;

export function useDebtors({ sortBy = 'DEBT_AGE', page = 0, size = DEBTORS_PAGE_SIZE } = {}) {
  return useQuery({
    queryKey: ['financial-dashboard', 'debtors', { sortBy, page, size }],
    queryFn: () => apiFetch(`/financial-dashboard/debtors/page?sortBy=${sortBy}&page=${page}&size=${size}`),
    placeholderData: (previous) => previous,
  });
}

export function useFirstBillingMonth() {
  return useQuery({
    queryKey: ['billing-metrics', 'first-month'],
    queryFn: () => apiFetch('/billing/metrics/first-month'),
  });
}
