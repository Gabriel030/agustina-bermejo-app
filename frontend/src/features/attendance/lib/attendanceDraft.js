export const DRAFT_TTL_MS = 12 * 60 * 60 * 1000;

function draftKey(type, sectionId, classDate) {
  return `attendanceDraft:${type}:${sectionId}:${classDate}`;
}

export function saveAttendanceDraft(type, sectionId, classDate, draft, now = Date.now()) {
  try {
    localStorage.setItem(draftKey(type, sectionId, classDate), JSON.stringify({ ...draft, savedAt: now }));
  } catch {
    return;
  }
}

export function clearAttendanceDraft(type, sectionId, classDate) {
  try {
    localStorage.removeItem(draftKey(type, sectionId, classDate));
  } catch {
    return;
  }
}

export function loadAttendanceDraft(type, sectionId, classDate, now = Date.now()) {
  try {
    const key = draftKey(type, sectionId, classDate);
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    const draft = JSON.parse(raw);
    const valid =
      draft &&
      typeof draft.savedAt === 'number' &&
      now - draft.savedAt <= DRAFT_TTL_MS &&
      draft.estados &&
      typeof draft.estados === 'object' &&
      Object.keys(draft.estados).length > 0;
    if (!valid) {
      localStorage.removeItem(key);
      return null;
    }
    return {
      estados: draft.estados,
      topic: typeof draft.topic === 'string' ? draft.topic : '',
      observations: typeof draft.observations === 'string' ? draft.observations : '',
    };
  } catch {
    return null;
  }
}
