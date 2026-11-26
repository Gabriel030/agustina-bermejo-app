import { describe, expect, it } from 'vitest';
import { hasRealScreen, visibleMenuItems } from '@/routes/menuAccess';
import { buildDrawerEntries, buildNavModel, isPathWithin } from '@/routes/navModel';
import { ROLE_MENU, groupMenuItems, menuItemsFor } from '@/routes/roleMenu';

const EXPECTED_TOP_ENTRIES = {
  alumno: [
    "link|/alumno/inicio|Inicio|/alumno/inicio",
    "group|studies|Estudios|/alumno/calendario,/alumno/mis-cursos,/alumno/mis-carreras,/alumno/mis-certificados",
    "link|/alumno/pagos|Pagos|/alumno/pagos",
    "catalog|catalog|Oferta académica|/catalogo",
    "link|/alumno/reuniones-informativas|Reuniones informativas|/alumno/reuniones-informativas",
  ],
  docente: [
    "link|/docente/mis-comisiones|Mis comisiones|/docente/mis-comisiones",
    "link|/docente/tomar-asistencia|Tomar asistencia|/docente/tomar-asistencia",
    "link|/docente/mesas-de-examen|Mesas de examen|/docente/mesas-de-examen",
  ],
  preceptor: [
    "link|/preceptor/alumnos|Alumnos|/preceptor/alumnos",
    "link|/preceptor/solicitudes-de-ingreso|Solicitudes de ingreso|/preceptor/solicitudes-de-ingreso",
    "link|/preceptor/inscripciones/listas-de-espera|Listas de espera|/preceptor/inscripciones/listas-de-espera",
    "link|/preceptor/oferta-academica|Oferta académica|/preceptor/oferta-academica",
    "link|/preceptor/mesas-de-examen|Mesas de examen|/preceptor/mesas-de-examen",
    "link|/preceptor/solicitudes-de-asistencia|Solicitudes de asistencia|/preceptor/solicitudes-de-asistencia",
    "link|/preceptor/periodos|Períodos|/preceptor/periodos",
  ],
  administrativo: [
    "link|/administrativo/inicio|Inicio|/administrativo/inicio",
    "group|people|Personas|/administrativo/alumnos,/administrativo/personal,/administrativo/comunicaciones,/administrativo/invitaciones",
    "group|enrollment|Inscripciones|/administrativo/solicitudes-de-ingreso,/administrativo/inscripciones/listas-de-espera",
    "group|academic|Académico|wide|offering=/administrativo/gestion-academica,/administrativo/periodos,/administrativo/feriados;coursework=/administrativo/mesas-de-examen,/administrativo/cierres-de-comision,/administrativo/solicitudes-de-asistencia;graduation=/administrativo/egresos-por-verificar,/administrativo/egresos,/administrativo/plantilla-de-certificados,/administrativo/firmantes,/administrativo/constancias,/administrativo/constancias-por-excepcion",
    "group|billing|Facturación|/administrativo/facturacion,/administrativo/emision-mensual,/administrativo/recordatorios-de-pago,/administrativo/medios-de-pago,/administrativo/precios,/administrativo/descuentos,/administrativo/financiero",
    "link|/administrativo/aprobaciones|Aprobaciones|/administrativo/aprobaciones",
  ],
  subdirector: [
    "link|/subdirector/inicio|Inicio|/subdirector/inicio",
    "group|people|Personas|/subdirector/alumnos,/subdirector/personal,/subdirector/comunicaciones,/subdirector/invitaciones",
    "group|enrollment|Inscripciones|/subdirector/solicitudes-de-ingreso,/subdirector/inscripciones/listas-de-espera",
    "group|academic|Académico|wide|offering=/subdirector/gestion-academica,/subdirector/periodos,/subdirector/feriados;coursework=/subdirector/mesas-de-examen,/subdirector/cierres-de-comision,/subdirector/solicitudes-de-asistencia;graduation=/subdirector/egresos-por-verificar,/subdirector/egresos,/subdirector/plantilla-de-certificados,/subdirector/firmantes,/subdirector/constancias",
    "group|billing|Facturación|/subdirector/facturacion,/subdirector/emision-mensual,/subdirector/recordatorios-de-pago,/subdirector/medios-de-pago,/subdirector/precios,/subdirector/descuentos,/subdirector/financiero",
    "link|/subdirector/aprobaciones|Aprobaciones|/subdirector/aprobaciones",
    "link|/subdirector/permisos|Roles y permisos|/subdirector/permisos",
  ],
  director: [
    "link|/director/inicio|Inicio|/director/inicio",
    "group|people|Personas|/director/alumnos,/director/personal,/director/comunicaciones,/director/invitaciones",
    "link|/director/inscripciones/listas-de-espera|Listas de espera|/director/inscripciones/listas-de-espera",
    "group|academic|Académico|wide|offering=/director/oferta-academica,/director/gestion-academica,/director/esquemas-evaluacion,/director/periodos;coursework=/director/mesas-de-examen,/director/cierres-de-comision;graduation=/director/egresos,/director/plantilla-de-certificados,/director/firmantes,/director/constancias,/director/constancias-por-excepcion",
    "group|billing|Facturación|/director/facturacion,/director/emision-mensual,/director/recordatorios-de-pago,/director/medios-de-pago,/director/terminos-del-pago-anual,/director/precios,/director/descuentos,/director/financiero",
    "group|institution|Institución|/director/ajustes-del-instituto,/director/importacion-inicial,/director/auditoria,/director/permisos,/director/backups",
    "link|/director/aprobaciones|Aprobaciones|/director/aprobaciones",
  ],
  platform_admin: [
    "link|/platform_admin/modulos|Módulos|/platform_admin/modulos",
    "link|/platform_admin/primer-director|Primer director|/platform_admin/primer-director",
    "link|/platform_admin/auditoria|Auditoría|/platform_admin/auditoria",
    "link|/platform_admin/backups|Backups|/platform_admin/backups",
  ],
};

const EXPECTED_DRAWER_ENTRIES = {
  alumno: [
    "link|/alumno/inicio|Inicio",
    "group|studies|Estudios{link|/alumno/calendario|Calendario;link|/alumno/mis-cursos|Mis cursos;link|/alumno/mis-carreras|Mis carreras;link|/alumno/mis-certificados|Mis certificados}",
    "link|/alumno/pagos|Pagos",
    "link|/catalogo|Oferta académica",
    "link|/alumno/reuniones-informativas|Reuniones informativas",
  ],
  docente: [
    "link|/docente/mis-comisiones|Mis comisiones",
    "link|/docente/tomar-asistencia|Tomar asistencia",
    "link|/docente/mesas-de-examen|Mesas de examen",
  ],
  preceptor: [
    "link|/preceptor/alumnos|Alumnos",
    "link|/preceptor/solicitudes-de-ingreso|Solicitudes de ingreso",
    "link|/preceptor/inscripciones/listas-de-espera|Listas de espera",
    "link|/preceptor/oferta-academica|Oferta académica",
    "link|/preceptor/mesas-de-examen|Mesas de examen",
    "link|/preceptor/solicitudes-de-asistencia|Solicitudes de asistencia",
    "link|/preceptor/periodos|Períodos",
  ],
  administrativo: [
    "link|/administrativo/inicio|Inicio",
    "group|people|Personas{link|/administrativo/alumnos|Alumnos;link|/administrativo/personal|Personal;link|/administrativo/comunicaciones|Comunicaciones;link|/administrativo/invitaciones|Invitaciones}",
    "group|enrollment|Inscripciones{link|/administrativo/solicitudes-de-ingreso|Solicitudes de ingreso;link|/administrativo/inscripciones/listas-de-espera|Listas de espera}",
    "group|academic|Académico{group|academic/offering|Oferta y períodos|count{link|/administrativo/gestion-academica|Gestión académica;link|/administrativo/periodos|Períodos;link|/administrativo/feriados|Feriados};group|academic/coursework|Cursada|count{link|/administrativo/mesas-de-examen|Mesas de examen;link|/administrativo/cierres-de-comision|Cierres de comisión;link|/administrativo/solicitudes-de-asistencia|Solicitudes de asistencia};group|academic/graduation|Egresos y certificados|count{link|/administrativo/egresos-por-verificar|Egresos por verificar;link|/administrativo/egresos|Egresos;link|/administrativo/plantilla-de-certificados|Plantilla de certificados;link|/administrativo/firmantes|Firmantes;link|/administrativo/constancias|Constancias;link|/administrativo/constancias-por-excepcion|Emitir por excepción}}",
    "group|billing|Facturación{link|/administrativo/facturacion|Facturación;link|/administrativo/emision-mensual|Emisión mensual;link|/administrativo/recordatorios-de-pago|Recordatorios de pago;link|/administrativo/medios-de-pago|Medios de pago;link|/administrativo/precios|Precios;link|/administrativo/descuentos|Descuentos;link|/administrativo/financiero|Financiero}",
    "link|/administrativo/aprobaciones|Aprobaciones",
  ],
  subdirector: [
    "link|/subdirector/inicio|Inicio",
    "group|people|Personas{link|/subdirector/alumnos|Alumnos;link|/subdirector/personal|Personal;link|/subdirector/comunicaciones|Comunicaciones;link|/subdirector/invitaciones|Invitaciones}",
    "group|enrollment|Inscripciones{link|/subdirector/solicitudes-de-ingreso|Solicitudes de ingreso;link|/subdirector/inscripciones/listas-de-espera|Listas de espera}",
    "group|academic|Académico{group|academic/offering|Oferta y períodos|count{link|/subdirector/gestion-academica|Gestión académica;link|/subdirector/periodos|Períodos;link|/subdirector/feriados|Feriados};group|academic/coursework|Cursada|count{link|/subdirector/mesas-de-examen|Mesas de examen;link|/subdirector/cierres-de-comision|Cierres de comisión;link|/subdirector/solicitudes-de-asistencia|Solicitudes de asistencia};group|academic/graduation|Egresos y certificados|count{link|/subdirector/egresos-por-verificar|Egresos por verificar;link|/subdirector/egresos|Egresos;link|/subdirector/plantilla-de-certificados|Plantilla de certificados;link|/subdirector/firmantes|Firmantes;link|/subdirector/constancias|Constancias}}",
    "group|billing|Facturación{link|/subdirector/facturacion|Facturación;link|/subdirector/emision-mensual|Emisión mensual;link|/subdirector/recordatorios-de-pago|Recordatorios de pago;link|/subdirector/medios-de-pago|Medios de pago;link|/subdirector/precios|Precios;link|/subdirector/descuentos|Descuentos;link|/subdirector/financiero|Financiero}",
    "link|/subdirector/aprobaciones|Aprobaciones",
    "link|/subdirector/permisos|Roles y permisos",
  ],
  director: [
    "link|/director/inicio|Inicio",
    "group|people|Personas{link|/director/alumnos|Alumnos;link|/director/personal|Personal;link|/director/comunicaciones|Comunicaciones;link|/director/invitaciones|Invitaciones}",
    "link|/director/inscripciones/listas-de-espera|Listas de espera",
    "group|academic|Académico{group|academic/offering|Oferta y períodos|count{link|/director/oferta-academica|Oferta académica;link|/director/gestion-academica|Gestión académica;link|/director/esquemas-evaluacion|Esquemas de evaluación;link|/director/periodos|Períodos};group|academic/coursework|Cursada|count{link|/director/mesas-de-examen|Mesas de examen;link|/director/cierres-de-comision|Cierres de comisión};group|academic/graduation|Egresos y certificados|count{link|/director/egresos|Egresos;link|/director/plantilla-de-certificados|Plantilla de certificados;link|/director/firmantes|Firmantes;link|/director/constancias|Constancias;link|/director/constancias-por-excepcion|Emitir por excepción}}",
    "group|billing|Facturación{link|/director/facturacion|Facturación;link|/director/emision-mensual|Emisión mensual;link|/director/recordatorios-de-pago|Recordatorios de pago;link|/director/medios-de-pago|Medios de pago;link|/director/terminos-del-pago-anual|Términos del pago anual;link|/director/precios|Precios;link|/director/descuentos|Descuentos;link|/director/financiero|Financiero}",
    "group|institution|Institución{link|/director/ajustes-del-instituto|Ajustes del instituto;link|/director/importacion-inicial|Importación inicial;link|/director/auditoria|Auditoría;link|/director/permisos|Roles y permisos;link|/director/backups|Backups}",
    "link|/director/aprobaciones|Aprobaciones",
  ],
  platform_admin: [
    "link|/platform_admin/modulos|Módulos",
    "link|/platform_admin/primer-director|Primer director",
    "link|/platform_admin/auditoria|Auditoría",
    "link|/platform_admin/backups|Backups",
  ],
};

function visibleItemsOf(role) {
  return visibleMenuItems(role, menuItemsFor(role));
}

function describeTopEntry(entry) {
  const base = `${entry.type}|${entry.id}|${entry.label ?? entry.item.label}`;
  if (entry.sections) {
    return `${base}|wide|${entry.sections.map((section) => `${section.id}=${section.items.map((item) => item.to).join(',')}`).join(';')}`;
  }
  return `${base}|${entry.item ? entry.item.to : entry.items.map((item) => item.to).join(',')}`;
}

function describeDrawerEntry(entry) {
  if (entry.type === 'link') return `link|${entry.id}|${entry.item.label}`;
  const count = entry.showCount ? '|count' : '';
  return `group|${entry.id}|${entry.label}${count}{${entry.children.map(describeDrawerEntry).join(';')}}`;
}

const roles = Object.keys(ROLE_MENU);

describe.each(roles)('buildNavModel for %s', (role) => {
  const items = visibleItemsOf(role);
  const model = buildNavModel(role, items);

  it('keeps only items with a real screen', () => {
    model.items.forEach((item) => {
      const slug = item.to.startsWith(`/${role}/`) ? item.to.slice(`/${role}/`.length) : null;
      if (slug !== null) expect(hasRealScreen(role, slug)).toBe(true);
    });
  });

  it('has no empty groups', () => {
    (model.groups ?? []).forEach((group) => expect(group.items.length).toBeGreaterThan(0));
  });

  it('exposes the same groups as groupMenuItems', () => {
    expect(model.groups).toEqual(groupMenuItems(role, items));
    expect(model.items).toBe(items);
  });

  it('keeps the top bar structure', () => {
    expect(model.entries.map(describeTopEntry)).toEqual(EXPECTED_TOP_ENTRIES[role]);
  });

  it('keeps the drawer structure', () => {
    expect(buildDrawerEntries(model.items, model.groups).map(describeDrawerEntry)).toEqual(EXPECTED_DRAWER_ENTRIES[role]);
  });

  it('tags every top bar entry with a kind', () => {
    model.entries.forEach((entry) => expect(['link', 'menu', 'panel']).toContain(entry.kind));
  });
});

describe('buildNavModel kinds', () => {
  it('marks the catalog as panel and grouped menus as menu for alumno', () => {
    const kinds = buildNavModel('alumno', visibleItemsOf('alumno')).entries.map((entry) => `${entry.id}:${entry.kind}`);
    expect(kinds).toEqual(['/alumno/inicio:link', 'studies:menu', '/alumno/pagos:link', 'catalog:panel', '/alumno/reuniones-informativas:link']);
  });

  it('returns only links and null groups for roles without groups', () => {
    const model = buildNavModel('docente', visibleItemsOf('docente'));
    expect(model.groups).toBeNull();
    expect(model.entries.every((entry) => entry.kind === 'link')).toBe(true);
  });
});

describe('isPathWithin', () => {
  it('matches the exact path', () => {
    expect(isPathWithin('/director/alumnos', '/director/alumnos')).toBe(true);
  });

  it('matches nested paths', () => {
    expect(isPathWithin('/director/alumnos', '/director/alumnos/42/detalle')).toBe(true);
  });

  it('does not match a sibling that only shares a prefix', () => {
    expect(isPathWithin('/director/alumnos', '/director/alumnos-baja')).toBe(false);
  });

  it('does not match a parent of the link', () => {
    expect(isPathWithin('/director/alumnos', '/director')).toBe(false);
  });

  it('matches a path with a trailing slash', () => {
    expect(isPathWithin('/director/alumnos', '/director/alumnos/')).toBe(true);
  });
});
