export const EMPTY_AUDIT_FILTERS = { actions: null, roles: null, entityType: '', entityId: '', actorPersonId: '', from: '', to: '' };

export const hasAuditFilters = (filters) => JSON.stringify(filters) !== JSON.stringify(EMPTY_AUDIT_FILTERS);
export const isAuditRangeInvalid = (filters) => Boolean(filters.from && filters.to && filters.from > filters.to);
