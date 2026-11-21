export const ACTOR_NAME_MIN_LENGTH = 2;

export const EMPTY_AUDIT_FILTERS = { actions: null, roles: null, entityType: '', entityId: '', actorName: '', from: '', to: '' };

export const hasAuditFilters = (filters) => JSON.stringify(filters) !== JSON.stringify(EMPTY_AUDIT_FILTERS);
export const isAuditRangeInvalid = (filters) => Boolean(filters.from && filters.to && filters.from > filters.to);

export const normalizeActorName = (text) => {
  const trimmed = text.trim();
  return trimmed.length >= ACTOR_NAME_MIN_LENGTH ? trimmed : '';
};
