import { daysSince } from '@/lib/aprobaciones';

export const DANGER_AGE_DAYS = 7;
export const WARNING_AGE_DAYS = 3;
export const EXAM_WINDOW_DAYS = 7;

export function oldestAgeDays(dates, total = dates.length) {
  if (dates.length < total) return null;
  const ages = dates.map((date) => daysSince(date)).filter((age) => age !== null);
  return ages.length ? Math.max(...ages) : null;
}

export function ageTone(ageDays) {
  if (ageDays === null || ageDays === undefined) return 'neutral';
  if (ageDays >= DANGER_AGE_DAYS) return 'danger';
  if (ageDays >= WARNING_AGE_DAYS) return 'warning';
  return 'neutral';
}

export function ageText(ageDays) {
  if (ageDays === null || ageDays === undefined) return null;
  if (ageDays === 0) return 'Más antiguo: hoy';
  return ageDays === 1 ? 'Más antiguo: hace 1 día' : `Más antiguo: hace ${ageDays} días`;
}

export function toCard(definition, queries, build) {
  if (queries.some((query) => query.isError)) {
    return { ...definition, state: 'error', retry: () => queries.filter((query) => query.isError).forEach((query) => query.refetch()) };
  }
  if (queries.some((query) => query.isLoading)) return { ...definition, state: 'loading' };
  return { ...definition, state: 'ready', ...build() };
}

export function upcomingBoards(boards, now = new Date()) {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const end = new Date(start.getTime() + (EXAM_WINDOW_DAYS + 1) * 86400000);
  return boards
    .filter((board) => {
      const date = new Date(board.examDate);
      return date >= start && date < end;
    })
    .sort((a, b) => a.examDate.localeCompare(b.examDate));
}

export function splitCards(cards) {
  const ready = cards.filter((card) => card.state === 'ready');
  const pending = ready
    .filter((card) => card.count > 0)
    .sort((a, b) => (b.ageDays ?? -1) - (a.ageDays ?? -1) || b.count - a.count);
  const unsettled = cards.filter((card) => card.state !== 'ready');
  const clear = ready.filter((card) => card.count === 0);
  const allClear = unsettled.length === 0 && pending.length === 0;
  return { pending, unsettled, clear, allClear };
}
