import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiFetchWithTotal } from '@/api/client';

export const LOST_BENEFITS_PAGE_SIZE = 10;

function buildPath({ discountDefinitionId, feeType, page, size }) {
  const params = new URLSearchParams();
  if (discountDefinitionId) params.set('discountDefinitionId', String(discountDefinitionId));
  if (feeType) params.set('feeType', feeType);
  params.set('page', String(page));
  params.set('size', String(size));
  return `/reports/billing/promotions/lost-benefits?${params.toString()}`;
}

export function useLostBenefits(filters) {
  return useQuery({
    queryKey: ['reports', 'lost-benefits', filters],
    queryFn: () => apiFetchWithTotal(buildPath(filters)),
    placeholderData: keepPreviousData,
  });
}
