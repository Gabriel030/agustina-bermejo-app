import { WEEKDAYS } from './classroomAvailability';
import { MINIMUM_MODULES, SHIFTS, buildModules } from './classroomModules';

const EVENING_SHIFT = SHIFTS.find((shift) => shift.id === 'EVENING');

export function shiftForSection(section) {
  return SHIFTS.find((shift) => shift.id === section?.shift) ?? EVENING_SHIFT;
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
    const selection = modules
      .filter((module) => module.startTime >= start && module.endTime <= end)
      .map((module) => module.index);
    return { key: index, dayOfWeek: slot.dayOfWeek, classroomId: slot.classroomId, selection };
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

export function hasScheduleChanges(currentSlots, nextSlots) {
  if (currentSlots.length !== nextSlots.length) return true;
  return nextSlots.some((slot, index) => !sameSlot(currentSlots[index], slot));
}

export function formatSlot(slot, classroomName) {
  const day = WEEKDAYS.find((weekday) => weekday.id === slot.dayOfWeek)?.short ?? slot.dayOfWeek;
  const room = classroomName ?? slot.classroomName;
  return `${day} ${trimTime(slot.startTime)}–${trimTime(slot.endTime)}${room ? ` · ${room}` : ''}`;
}

export function buildScheduleRows(currentSlots, nextSlots, classroomNameById) {
  const total = Math.max(currentSlots.length, nextSlots.length);
  return Array.from({ length: total }, (_, index) => {
    const current = currentSlots[index];
    const next = nextSlots[index];
    return {
      key: index,
      label: `Franja ${index + 1}`,
      today: current ? formatSlot(current) : '—',
      next: next ? formatSlot(next, classroomNameById[next.classroomId]) : null,
      unchanged: sameSlot(current, next),
      removed: !next,
    };
  });
}

export function formatRequestedDate(requestedAt) {
  if (!requestedAt) return '';
  return new Date(requestedAt).toLocaleDateString('es-AR', { day: 'numeric', month: 'long', year: 'numeric' });
}
