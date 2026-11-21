import { WEEKDAYS } from './classroomAvailability';
import { MINIMUM_MODULES, SHIFTS, buildModules } from './classroomModules';

export function shiftForSection(section) {
  const declared = SHIFTS.find((shift) => shift.id === section?.shift);
  if (declared) return declared;
  const slots = section?.slots ?? [];
  if (slots.length === 0) return null;
  return (
    SHIFTS.find((shift) =>
      slots.every((slot) => trimTime(slot.startTime) >= shift.start && trimTime(slot.endTime) <= shift.end),
    ) ?? null
  );
}

export function canRequestScheduleChange(cycle) {
  return cycle?.status === 'APPROVED';
}

export function hasPendingModification(section) {
  return section.pendingApproval === true || !!section.pendingModification;
}

function trimTime(time) {
  return time?.slice(0, 5);
}

export function slotsToDrafts(slots, shift) {
  const modules = buildModules(shift);
  return slots.map((slot, index) => {
    const start = trimTime(slot.startTime);
    const end = trimTime(slot.endTime);
    const covered = modules.filter((module) => module.startTime >= start && module.endTime <= end);
    const aligned =
      covered.length > 0 &&
      covered[0].startTime === start &&
      covered[covered.length - 1].endTime === end;
    return {
      key: index,
      dayOfWeek: slot.dayOfWeek,
      classroomId: slot.classroomId,
      selection: aligned ? covered.map((module) => module.index) : [],
      legacy: !aligned,
    };
  });
}

export function emptyDraft(key) {
  return { key, dayOfWeek: 'MONDAY', classroomId: null, selection: [] };
}

export function draftsToSlots(drafts, shift) {
  const modules = buildModules(shift);
  return drafts.map((draft) => ({
    classroomId: draft.classroomId,
    dayOfWeek: draft.dayOfWeek,
    startTime: modules[draft.selection[0]].startTime,
    endTime: modules[draft.selection[draft.selection.length - 1]].endTime,
  }));
}

export function draftErrors(drafts) {
  return drafts.flatMap((draft, index) => {
    if (draft.selection.length < MINIMUM_MODULES) {
      return [`Franja ${index + 1}: necesita al menos ${MINIMUM_MODULES} módulos seguidos.`];
    }
    if (!draft.classroomId) return [`Franja ${index + 1}: elegí un aula.`];
    return [];
  });
}

export function sameSlot(a, b) {
  return (
    !!a &&
    !!b &&
    Number(a.classroomId) === Number(b.classroomId) &&
    a.dayOfWeek === b.dayOfWeek &&
    trimTime(a.startTime) === trimTime(b.startTime) &&
    trimTime(a.endTime) === trimTime(b.endTime)
  );
}

export function slotKey(slot) {
  return `${slot.dayOfWeek}|${Number(slot.classroomId)}|${trimTime(slot.startTime)}|${trimTime(slot.endTime)}`;
}

export function hasScheduleChanges(currentSlots, nextSlots) {
  if (currentSlots.length !== nextSlots.length) return true;
  const nextKeys = new Set(nextSlots.map(slotKey));
  return currentSlots.some((slot) => !nextKeys.has(slotKey(slot)));
}

export function formatSlot(slot, classroomName) {
  const day = WEEKDAYS.find((weekday) => weekday.id === slot.dayOfWeek)?.short ?? slot.dayOfWeek;
  const room = classroomName ?? slot.classroomName;
  return `${day} ${trimTime(slot.startTime)}–${trimTime(slot.endTime)}${room ? ` · ${room}` : ''}`;
}

export function buildScheduleRows(currentSlots, nextSlots, classroomNameById = {}) {
  const nextKeys = new Set(nextSlots.map(slotKey));
  const currentKeys = new Set(currentSlots.map(slotKey));
  const replacements = nextSlots.filter((slot) => !currentKeys.has(slotKey(slot)));
  const rows = [];
  let replacementIndex = 0;
  currentSlots.forEach((current) => {
    if (nextKeys.has(slotKey(current))) {
      rows.push({ today: formatSlot(current), next: null, unchanged: true, removed: false });
      return;
    }
    const replacement = replacements[replacementIndex];
    replacementIndex += 1;
    rows.push({
      today: formatSlot(current),
      next: replacement ? formatSlot(replacement, classroomNameById[replacement.classroomId]) : null,
      unchanged: false,
      removed: !replacement,
    });
  });
  replacements.slice(replacementIndex).forEach((added) => {
    rows.push({
      today: '—',
      next: formatSlot(added, classroomNameById[added.classroomId]),
      unchanged: false,
      removed: false,
    });
  });
  return rows.map((row, index) => ({ ...row, key: index }));
}

export function formatRequestedDate(requestedAt) {
  if (!requestedAt) return '';
  return new Date(requestedAt).toLocaleDateString('es-AR', { day: 'numeric', month: 'long', year: 'numeric' });
}
