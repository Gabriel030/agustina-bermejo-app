import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useFinancialDashboard() {
  return useQuery({
    queryKey: ['financial-dashboard'],
    queryFn: () => apiFetch('/financial-dashboard'),
  });
}

export function useDebtors(sortBy = 'DEBT_AGE') {
  return useQuery({
    queryKey: ['financial-dashboard', 'debtors', sortBy],
    queryFn: () => apiFetch(`/financial-dashboard/debtors?sortBy=${sortBy}`),
  });
}

export function useFirstBillingMonth() {
  return useQuery({
    queryKey: ['billing-metrics', 'first-month'],
    queryFn: () => apiFetch('/billing/metrics/first-month'),
  });
}
