import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const AUDIT_LOG_PAGE_SIZE = 20;

export function useAuditLog(endpoint, filters, page) {
  return useQuery({
    queryKey: ['audit-log', endpoint, filters, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page - 1), size: String(AUDIT_LOG_PAGE_SIZE) });
      filters.actions?.forEach((action) => params.append('action', action));
      filters.roles?.forEach((role) => params.append('actorRole', role));
      if (filters.entityType) params.set('entityType', filters.entityType);
      if (filters.entityType && filters.entityId) params.set('entityId', filters.entityId);
      if (filters.actorPersonId) params.set('actorPersonId', filters.actorPersonId);
      if (filters.actorName) params.set('actorName', filters.actorName);
      if (filters.from) params.set('from', filters.from);
      if (filters.to) params.set('to', filters.to);
      return apiFetch(`${endpoint}?${params}`);
    },
    placeholderData: keepPreviousData,
    retry: (count, error) => error?.status !== 403 && count < 2,
  });
}
