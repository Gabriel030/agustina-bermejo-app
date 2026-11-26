import { describe, expect, it } from 'vitest';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { ROLE_MENU, groupMenuItems, menuGroupSlugs, menuItemsFor, pagesFor } from '@/routes/roleMenu';
import { roleRoutesFor } from '@/routes/featureRoutes';
import { hasRealScreen } from '@/routes/menuAccess';

const LEGACY_REDIRECT_PATHS = ['listas-de-espera'];
const PENDING_SCREENS = { alumno: [], administrativo: [], director: [] };

describe('role menu manifest', () => {
  it('defines a manifest entry for every configured role', () => {
    expect(Object.keys(ROLE_MENU).sort()).toEqual(Object.keys(ROLE_CONFIG).sort());
  });

  it.each(Object.keys(ROLE_MENU))('derives the pages of %s from the manifest', (role) => {
    expect(ROLE_CONFIG[role].pages).toEqual(pagesFor(role));
  });

  it.each(Object.keys(ROLE_MENU))('gives every top level route of %s a manifest entry', (role) => {
    const slugs = pagesFor(role);
    const topLevel = roleRoutesFor(role).map((route) => route.path).filter((path) => !path.includes('/') && !LEGACY_REDIRECT_PATHS.includes(path));
    const orphans = topLevel.filter((path) => !slugs.includes(path));
    expect(orphans).toEqual([]);
  });

  it.each(Object.keys(ROLE_MENU))('gives every menu item of %s a real screen except the declared pending ones', (role) => {
    const pending = PENDING_SCREENS[role] ?? [];
    const missing = pagesFor(role).filter((slug) => !hasRealScreen(role, slug));
    expect(missing.filter((slug) => !pending.includes(slug) && slug !== 'inicio')).toEqual([]);
  });

  it.each(Object.keys(ROLE_MENU))('does not repeat destinations in the menu of %s', (role) => {
    const targets = menuItemsFor(role).map((item) => item.to);
    expect(new Set(targets).size).toBe(targets.length);
  });

  it('keeps the billing corridas screen reachable without a menu entry', () => {
    expect(pagesFor('administrativo')).toContain('corridas-de-facturacion');
    expect(menuItemsFor('administrativo').map((item) => item.to)).not.toContain('/administrativo/corridas-de-facturacion');
  });

  it('labels the academic management entry with the same name for administrative and director', () => {
    const label = (role) => menuItemsFor(role).find((item) => item.to.endsWith('/gestion-academica')).label;
    expect(label('administrativo')).toBe('Gestión académica');
    expect(label('director')).toBe('Gestión académica');
  });

  it('gives the director the academic offering and keeps the administrative out of it', () => {
    expect(pagesFor('director')).toContain('oferta-academica');
    expect(pagesFor('administrativo')).not.toContain('oferta-academica');
  });
  it('maps every visible director menu entry to a group', () => {
    const slugs = ROLE_MENU.director.filter((entry) => entry.slug && !entry.hidden).map((entry) => entry.slug);
    expect(slugs.filter((slug) => !menuGroupSlugs('director').includes(slug))).toEqual([]);
  });

  it('keeps every director item once and only in its group', () => {
    const items = menuItemsFor('director');
    const groups = groupMenuItems('director', items);
    expect(groups.flatMap((group) => group.items.map((item) => item.to)).sort()).toEqual(items.map((item) => item.to).sort());
    expect(groups.find((group) => group.id === 'billing').items.map((item) => item.label)).toContain('Emisión mensual');
  });

  it('drops groups left without visible items', () => {
    const withoutAcademic = menuItemsFor('director').filter(
      (item) => !['oferta-academica', 'gestion-academica', 'esquemas-evaluacion', 'mesas-de-examen', 'cierres-de-comision', 'periodos', 'constancias', 'constancias-por-excepcion', 'egresos', 'plantilla-de-certificados', 'firmantes'].some((slug) => item.to.endsWith(slug)),
    );
    expect(groupMenuItems('director', withoutAcademic).map((group) => group.id)).not.toContain('academic');
  });

  it('keeps unmapped items in the headless first group instead of losing them', () => {
    const extra = { to: '/director/nuevo', label: 'Nuevo', icon: null };
    const groups = groupMenuItems('director', [...menuItemsFor('director'), extra]);
    expect(groups[0].label).toBeNull();
    expect(groups[0].items).toContain(extra);
  });

  it('has no grouping for roles without a definition', () => {
    expect(groupMenuItems('docente', menuItemsFor('docente'))).toBeNull();
  });

  it('groups the student menu keeping order, the catalog link and the options entry', () => {
    const groups = groupMenuItems('alumno', menuItemsFor('alumno'));
    expect(groups.map((group) => group.label)).toEqual([null, 'Estudios', null, null, null]);
    expect(groups.flatMap((group) => group.items.map((item) => item.label))).toEqual([
      'Inicio', 'Calendario', 'Mis cursos', 'Mis carreras', 'Mis certificados', 'Pagos', 'Catálogo', 'Opciones',
    ]);
  });

  it('orders the director groups as defined with the approvals entry after the institution group', () => {
    const groups = groupMenuItems('director', menuItemsFor('director'));
    expect(groups.map((group) => group.id)).toEqual(['main', 'people', 'enrollment', 'academic', 'billing', 'institution', 'approvals']);
    expect(groups.find((group) => group.id === 'people').items.map((item) => item.label)).toEqual(['Alumnos', 'Personal', 'Comunicaciones', 'Invitaciones']);
  });

  it('lays the long director groups out in two columns', () => {
    const groups = groupMenuItems('director', menuItemsFor('director'));
    expect(groups.filter((group) => group.columns === 2).map((group) => group.id)).toEqual(['academic', 'billing']);
  });

  it.each(['docente', 'preceptor', 'administrativo', 'director'])('exposes the exam boards screen to %s with its route', (role) => {
    expect(pagesFor(role)).toContain('mesas-de-examen');
    expect(roleRoutesFor(role).map((route) => route.path)).toContain('mesas-de-examen');
  });

  it.each(['alumno', 'platform_admin'])('keeps the exam boards screen away from %s', (role) => {
    expect(pagesFor(role)).not.toContain('mesas-de-examen');
  });

  it('lists the exam boards of the director under the academic group', () => {
    const academic = groupMenuItems('director', menuItemsFor('director')).find((group) => group.id === 'academic');
    expect(academic.items.map((item) => item.to)).toContain('/director/mesas-de-examen');
  });

  it.each(['administrativo', 'director'])('exposes the commission closures list to %s with its route', (role) => {
    expect(pagesFor(role)).toContain('cierres-de-comision');
    expect(roleRoutesFor(role).map((route) => route.path)).toContain('cierres-de-comision');
  });

  it.each(['docente', 'preceptor', 'alumno', 'platform_admin'])('keeps the commission closures list away from %s', (role) => {
    expect(pagesFor(role)).not.toContain('cierres-de-comision');
  });

  it('lists the commission closures of the director under the academic group', () => {
    const academic = groupMenuItems('director', menuItemsFor('director')).find((group) => group.id === 'academic');
    expect(academic.items.map((item) => item.to)).toContain('/director/cierres-de-comision');
  });

  it('menu entry lives under Enrollments', () => {
    const enrollment = groupMenuItems('director', menuItemsFor('director')).find((group) => group.id === 'enrollment');
    expect(enrollment.label).toBe('Inscripciones');
    expect(enrollment.items.map((item) => item.to)).toEqual(['/director/inscripciones/listas-de-espera']);
    expect(roleRoutesFor('director').map((route) => route.path)).toContain('inscripciones/listas-de-espera');
    expect(pagesFor('director')).not.toContain('listas-de-espera');
  });

  it.each(['administrativo', 'subdirector'])('lists every visible %s slug exactly once in the menu groups', (role) => {
    const visible = ROLE_MENU[role].filter((entry) => entry.slug && !entry.hidden).map((entry) => entry.slug);
    const grouped = groupMenuItems(role, menuItemsFor(role)).flatMap((group) => group.items.map((item) => item.to.replace(`/${role}/`, '')));
    expect([...grouped].sort()).toEqual([...visible].sort());
    expect(new Set(grouped).size).toBe(grouped.length);
    expect(menuGroupSlugs(role).filter((slug) => !visible.includes(slug))).toEqual([]);
  });

  it('orders the administrative groups with the academic grid in the middle', () => {
    const groups = groupMenuItems('administrativo', menuItemsFor('administrativo'));
    expect(groups.map((group) => group.id)).toEqual(['main', 'people', 'enrollment', 'academic', 'billing', 'approvals']);
    expect(groups.find((group) => group.id === 'billing').items).toHaveLength(7);
    expect(groups.find((group) => group.id === 'billing').sections).toBeUndefined();
    expect(groups.find((group) => group.id === 'enrollment').items.map((item) => item.label)).toEqual(['Solicitudes de ingreso', 'Listas de espera']);
  });

  it('builds the academic sections of the administrative role', () => {
    const academic = groupMenuItems('administrativo', menuItemsFor('administrativo')).find((group) => group.id === 'academic');
    expect(academic.sections.map((section) => [section.label, section.items.length])).toEqual([
      ['Oferta y períodos', 3],
      ['Cursada', 3],
      ['Egresos y certificados', 6],
    ]);
    expect(academic.items).toHaveLength(12);
    expect(academic.items.map((item) => item.to)).toEqual(academic.sections.flatMap((section) => section.items.map((item) => item.to)));
  });

  it('keeps the exception certificates out of the subdirector and adds the permissions link last', () => {
    const groups = groupMenuItems('subdirector', menuItemsFor('subdirector'));
    const academic = groups.find((group) => group.id === 'academic');
    expect(academic.items).toHaveLength(11);
    expect(academic.items.map((item) => item.to)).not.toContain('/subdirector/constancias-por-excepcion');
    expect(groups.at(-1).items.map((item) => item.to)).toEqual(['/subdirector/permisos']);
  });

  it('drops academic sections left without visible items and the group when none remain', () => {
    const without = (slugs) => menuItemsFor('administrativo').filter((item) => !slugs.some((slug) => item.to.endsWith(`/${slug}`)));
    const noCoursework = groupMenuItems('administrativo', without(['mesas-de-examen', 'cierres-de-comision', 'solicitudes-de-asistencia']));
    expect(noCoursework.find((group) => group.id === 'academic').sections.map((section) => section.label)).toEqual(['Oferta y períodos', 'Egresos y certificados']);

    const allAcademic = menuGroupSlugs('administrativo').filter((slug) => !['inicio', 'aprobaciones'].includes(slug));
    const noAcademic = groupMenuItems('administrativo', without(allAcademic));
    expect(noAcademic.map((group) => group.id)).toEqual(['main', 'approvals']);
  });

  it.each(['docente', 'preceptor', 'platform_admin'])('keeps %s as a flat list without groups and with unique entries', (role) => {
    const items = menuItemsFor(role);

    expect(groupMenuItems(role, items)).toBeNull();
    expect(new Set(items.map((item) => item.to)).size).toBe(items.length);
  });

  it('keeps the preceptor entries in the agreed order', () => {
    expect(menuItemsFor('preceptor').map((item) => item.label)).toEqual([
      'Inicio',
      'Alumnos',
      'Solicitudes de ingreso',
      'Listas de espera',
      'Oferta académica',
      'Mesas de examen',
      'Solicitudes de asistencia',
      'Períodos',
    ]);
  });

  it('hides the platform admin home from the menu', () => {
    expect(menuItemsFor('platform_admin').map((item) => item.label)).toEqual(['Módulos', 'Primer director', 'Auditoría', 'Backups']);
  });
});
