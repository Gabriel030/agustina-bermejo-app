import { describe, expect, it } from 'vitest';
import { matchPath } from 'react-router-dom';
import { TIPO_NOTIFICACION_LABEL } from '@/lib/notificaciones';
import { NOTIFICATION_DESTINATIONS, notificationDestination } from '@/features/notifications/lib/notificationDestination';
import { SHARED_ROUTES, roleRoutesFor } from '@/routes/featureRoutes';

const ROLES = ['ALUMNO', 'DOCENTE', 'PRECEPTOR', 'ADMINISTRATIVO', 'DIRECTOR'];

function routeExists(role, destination) {
  const pathname = destination.split('?')[0];
  const [, segment, ...rest] = pathname.split('/');
  if (segment === role.toLowerCase()) {
    const relative = rest.join('/');
    return roleRoutesFor(role.toLowerCase()).some((route) => matchPath({ path: `/${route.path}`, end: true }, `/${relative}`));
  }
  return SHARED_ROUTES.some((route) => matchPath({ path: route.path, end: true }, pathname));
}

describe('notificationDestination', () => {
  it('maps every notification type that has a label', () => {
    const unmapped = Object.keys(TIPO_NOTIFICACION_LABEL).filter((type) => !NOTIFICATION_DESTINATIONS[type]);
    expect(unmapped).toEqual([]);
  });

  it('does not map types that do not exist', () => {
    const unknown = Object.keys(NOTIFICATION_DESTINATIONS).filter((type) => !(type in TIPO_NOTIFICACION_LABEL));
    expect(unknown).toEqual([]);
  });

  const cases = Object.keys(NOTIFICATION_DESTINATIONS).flatMap((type) =>
    ROLES.flatMap((role) => [
      [type, role, null],
      [type, role, 7],
    ]),
  );

  it.each(cases)('%s for %s (entity %s) leads to a route that exists for that role or to nowhere', (type, role, relatedEntityId) => {
    const destination = notificationDestination({ type, relatedEntityId }, role);
    if (destination === null) return;
    expect(destination).not.toMatch(/\/\/|undefined|null/);
    expect(routeExists(role, destination), `${role}: ${destination}`).toBe(true);
  });

  it.each([
    ['SUBJECT_WITHDRAWN', 'ALUMNO', '/alumno/mis-carreras?tab=materias'],
    ['WAITLIST_PAUSED_BY_SCHEDULE_OVERLAP', 'ALUMNO', '/alumno/mis-carreras?tab=materias'],
    ['WAITLIST_EXPIRING_SOON', 'ALUMNO', '/alumno/mis-carreras?tab=materias'],
    ['WAITLIST_REMOVED_BY_STAFF', 'ALUMNO', '/alumno/mis-carreras?tab=materias'],
    ['WAITLIST_ENTRY_EXPIRED', 'ALUMNO', '/alumno/mis-carreras?tab=materias'],
    ['REGULAR_CERTIFICATE_READY', 'ALUMNO', '/alumno/mis-carreras?tab=tramites'],
    ['ATTENDANCE_JUSTIFICATION_REJECTED', 'ALUMNO', '/alumno/mis-carreras?tab=asistencia'],
    ['SUBJECT_WITHDRAWN_TEACHER', 'DOCENTE', '/docente/mis-comisiones'],
    ['PAYMENT_RECEIPT_REJECTED', 'ALUMNO', '/alumno/pagos'],
    ['PAYMENT_CHARGED_BACK', 'ADMINISTRATIVO', '/administrativo/facturacion'],
    ['CLOSURE_REOPENED', 'DIRECTOR', '/director/cierres-de-comision'],
    ['TERM_CLOSURE_OVERDUE', 'DIRECTOR', '/director/cierres-de-comision'],
    ['EXAM_BOARD_AUTO_CLOSED', 'DIRECTOR', '/director/mesas-de-examen'],
    ['EXAM_BOARD_CANCELED', 'DOCENTE', '/docente/mesas-de-examen'],
    ['SUBJECT_EQUIVALENCE_RECORDED', 'ALUMNO', '/alumno/mis-carreras?tab=plan-de-estudio'],
    ['SUBJECT_EQUIVALENCE_VOIDED', 'ALUMNO', '/alumno/mis-carreras?tab=plan-de-estudio'],
  ])('sends %s to the right screen for %s', (type, role, expected) => {
    expect(notificationDestination({ type, relatedEntityId: null }, role)).toBe(expected);
  });

  it('uses the entity id when the destination is a detail screen', () => {
    expect(notificationDestination({ type: 'DRAFT_INVOICES_AWAITING_REVIEW', relatedEntityId: 7 }, 'DIRECTOR')).toBe(
      '/director/emision-mensual/7/borradores',
    );
    expect(notificationDestination({ type: 'AUTHORIZATION_REQUESTED', relatedEntityId: 4 }, 'ADMINISTRATIVO')).toBe('/administrativo/aprobaciones/4');
  });

  it('gives a subdirector the administrativo destinations under its own base', () => {
    expect(notificationDestination({ type: 'PROGRAM_APPLICATION_RECEIVED', relatedEntityId: 5 }, 'SUBDIRECTOR')).toBe('/subdirector/solicitudes-de-ingreso/5');
    expect(notificationDestination({ type: 'DRAFT_INVOICES_AWAITING_REVIEW', relatedEntityId: 7 }, 'SUBDIRECTOR')).toBe('/subdirector/emision-mensual/7/borradores');
    expect(notificationDestination({ type: 'CERTIFICATE_EXCEPTION_DECIDED', relatedEntityId: null }, 'SUBDIRECTOR')).toBe('/subdirector/inicio');
  });

  it('never sends a role to a screen that belongs to another one', () => {
    expect(notificationDestination({ type: 'SUBJECT_WITHDRAWN', relatedEntityId: 1 }, 'DOCENTE')).toBeNull();
    expect(notificationDestination({ type: 'NIGHTLY_RUN_FAILED', relatedEntityId: 1 }, 'ALUMNO')).toBeNull();
  });

  it('falls back to nowhere for unknown types, empty notifications and unknown roles', () => {
    expect(notificationDestination({ type: 'SOMETHING_NEW', relatedEntityId: 1 }, 'ALUMNO')).toBeNull();
    expect(notificationDestination(null, 'ALUMNO')).toBeNull();
    expect(notificationDestination({ type: 'SUBJECT_WITHDRAWN' }, null)).toBeNull();
    expect(notificationDestination({ type: 'SUBJECT_WITHDRAWN' }, 'PLATFORM_ADMIN')).toBeNull();
  });
});
