export const ACTOR_NAME_MIN_LENGTH = 2;

export const EMPTY_AUDIT_FILTERS = { actions: null, roles: null, entityType: '', entityId: '', actor: '', from: '', to: '' };

export const hasAuditFilters = (filters) => JSON.stringify(filters) !== JSON.stringify(EMPTY_AUDIT_FILTERS);
export const isAuditRangeInvalid = (filters) => Boolean(filters.from && filters.to && filters.from > filters.to);

export const actorSearchInput = (typedText, debouncedText) => (typedText.trim() === '' ? '' : debouncedText);

export const actorSearchFor = (text) => {
  const trimmed = text.trim();
  if (/^\d+$/.test(trimmed)) return { actorPersonId: trimmed, actorName: '' };
  return { actorPersonId: '', actorName: trimmed.length >= ACTOR_NAME_MIN_LENGTH ? trimmed : '' };
};
