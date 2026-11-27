import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabHistorial } from '@/features/attendance/pages/DocenteTomarAsistencia';

const statusReads = vi.hoisted(() => ({ count: 0 }));

vi.mock('@/features/attendance/lib/attendanceStatuses', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    STATUS_BY_VALUE: new Proxy(original.STATUS_BY_VALUE, {
      get(target, key) {
        statusReads.count += 1;
        return target[key];
      },
    }),
  };
});

const API = 'http://localhost:8080';
const STUDENTS = 20;
const DATES = 10;

function mockLargeHistory() {
  const classDates = Array.from({ length: DATES }, (_, i) => `2026-11-${String(i + 1).padStart(2, '0')}`);
  const rows = Array.from({ length: STUDENTS }, (_, s) => ({
    studentId: s + 1,
    studentName: `Alumno ${s + 1}`,
    attendancePercent: 90,
    cellsByDate: Object.fromEntries(classDates.map((d) => [d, { attendanceId: s * 100 + 1, status: 'PRESENT', justificationDeadline: null, justifiable: false, rejection: null }])),
  }));
  server.use(http.get(`${API}/program-sections/5/attendance/history`, () => HttpResponse.json({ classDates, rows })));
}

describe('TabHistorial hover', () => {
  it('re-renders only the hovered row and not the whole matrix', async () => {
    mockLargeHistory();
    renderWithProviders(<TabHistorial type="PROGRAM" sectionId={5} />);
    const row = (await screen.findByText('Alumno 3')).closest('tr');
    const cell = row.querySelector('td[data-date="2026-11-04"]');
    expect(statusReads.count).toBeGreaterThanOrEqual(STUDENTS * DATES);

    statusReads.count = 0;
    fireEvent.mouseEnter(row);
    fireEvent.mouseOver(cell);

    expect(statusReads.count).toBeLessThanOrEqual(DATES + 2);expect(statusReads.count).toBeLessThanOrEqual(DATES + 2);
    expect(row.firstChild).toHaveClass('bg-brand');
  });

  it('highlights the hovered date column and clears it on leaving the table', async () => {
    mockLargeHistory();
    renderWithProviders(<TabHistorial type="PROGRAM" sectionId={5} />);
    const row = (await screen.findByText('Alumno 3')).closest('tr');
    const table = row.closest('table');

    fireEvent.mouseOver(row.querySelector('td[data-date="2026-11-04"]'));
    expect(table.querySelectorAll('[data-hl]')).toHaveLength(STUDENTS + 1);
    table.querySelectorAll('[data-hl]').forEach((el) => expect(el.dataset.date).toBe('2026-11-04'));

    fireEvent.mouseLeave(table);
    expect(table.querySelectorAll('[data-hl]')).toHaveLength(0);
  });
});
