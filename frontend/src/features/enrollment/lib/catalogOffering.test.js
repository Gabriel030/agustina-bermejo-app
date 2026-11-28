import { describe, expect, it } from 'vitest';
import {
  admissionBadge,
  describeOfferingView,
  groupOfferingByArea,
  nextInformativeSession,
  parseOfferingFilters,
  todayIsoDate,
} from '@/features/enrollment/lib/catalogOffering';

describe('admissionBadge', () => {
  const today = '2026-11-05';

  it('asks to check dates when there is no open cohort', () => {
    expect(admissionBadge([{ admissionOpen: false, admissionEnd: '2026-11-10' }], today).label).toBe('Consultá fechas');
    expect(admissionBadge([], today).label).toBe('Consultá fechas');
    expect(admissionBadge(undefined, today).label).toBe('Consultá fechas');
  });

  it('shows the closing date when the admission ends within 30 days', () => {
    expect(admissionBadge([{ admissionOpen: true, admissionEnd: '2026-12-05' }], today)).toEqual({ tone: 'warning', label: 'Cierra 05/12' });
    expect(admissionBadge([{ admissionOpen: true, admissionEnd: '2026-11-05' }], today).label).toBe('Cierra 05/11');
  });

  it('shows open admissions when the closing date is further than 30 days or unknown', () => {
    expect(admissionBadge([{ admissionOpen: true, admissionEnd: '2026-12-06' }], today).label).toBe('Inscripciones abiertas');
    expect(admissionBadge([{ admissionOpen: true }], today).label).toBe('Inscripciones abiertas');
  });

  it('uses the earliest closing date among the open cohorts', () => {
    const cohorts = [
      { admissionOpen: true, admissionEnd: '2027-03-01' },
      { admissionOpen: true, admissionEnd: '2026-11-30' },
    ];
    expect(admissionBadge(cohorts, today).label).toBe('Cierra 30/11');
  });
});

describe('describeOfferingView', () => {
  it('builds the breadcrumbs from the root to the last filter, which is not a link', () => {
    const { crumbs, title } = describeOfferingView({ area: 'DESIGN', level: 'BACHELOR', q: '' });
    expect(crumbs).toEqual([
      { label: 'Oferta académica', to: '/catalogo' },
      { label: 'Diseño', to: '/catalogo?area=DESIGN' },
      { label: 'De grado' },
    ]);
    expect(title).toBe('De grado · Diseño');
  });

  it('turns the search into the last crumb and keeps the other filters linked', () => {
    const { crumbs } = describeOfferingView({ area: 'DESIGN', level: null, q: 'foto' });
    expect(crumbs).toEqual([
      { label: 'Oferta académica', to: '/catalogo' },
      { label: 'Diseño', to: '/catalogo?area=DESIGN' },
      { label: 'Resultados para «foto»' },
    ]);
  });
});

describe('parseOfferingFilters', () => {
  it('keeps only known areas and levels', () => {
    const filters = parseOfferingFilters(new URLSearchParams('area=HEALTH&level=toString&q=%20x%20'));
    expect(filters).toEqual({ area: 'HEALTH', level: null, q: 'x' });
  });
});

describe('groupOfferingByArea', () => {
  it('orders the groups as the areas are defined and leaves items without area last', () => {
    const groups = groupOfferingByArea([
      { key: 'a', area: null },
      { key: 'b', area: 'HEALTH' },
      { key: 'c', area: 'BUSINESS_ADMINISTRATION' },
    ]);
    expect(groups.map((group) => group.label)).toEqual(['Administración y Negocios', 'Salud', 'Otras ofertas']);
  });
});

describe('nextInformativeSession', () => {
  it('returns the soonest scheduled session that has not started', () => {
    const now = new Date('2026-11-05T12:00:00Z');
    const sessions = [
      { id: 1, status: 'SCHEDULED', scheduledAt: '2026-12-01T12:00:00Z' },
      { id: 2, status: 'SCHEDULED', scheduledAt: '2026-11-10T12:00:00Z' },
      { id: 3, status: 'CANCELLED', scheduledAt: '2026-11-06T12:00:00Z' },
      { id: 4, status: 'SCHEDULED', scheduledAt: '2026-11-01T12:00:00Z' },
    ];
    expect(nextInformativeSession(sessions, now).id).toBe(2);
    expect(nextInformativeSession([], now)).toBeNull();
  });
});

describe('todayIsoDate', () => {
  it('formats the local date with zero padding', () => {
    expect(todayIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
