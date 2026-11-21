const STAFF_BILLING = ['administrativo', 'director'];
const STAFF_INTAKE = ['preceptor', 'administrativo'];

const toPath = (path) => () => path;

const forRoles = (roles, build) => ({ roles, build });

const alumno = (path) => forRoles(['alumno'], toPath(path));
const alumnoOrUnknown = (path) => forRoles(['alumno', null], toPath(path));
const docente = (path) => forRoles(['docente'], toPath(path));
const docenteOrUnknown = (path) => forRoles(['docente', null], toPath(path));
const staff = (roles, slug) => forRoles(roles, ({ base }) => `/${base}/${slug}`);
const anyone = (build) => ({ roles: null, build });

const billingStaff = (slug) => staff(STAFF_BILLING, slug);

const withId = (build, fallback) => ({ id, base }) => (id ? build({ id, base }) : fallback({ base }));

const SUBJECTS_TAB = alumno('/alumno/mis-carreras?tab=materias');
const PAYMENTS = alumno('/alumno/pagos');
const TEACHER_SECTIONS = docente('/docente/mis-comisiones');
const CLOSURES = staff(STAFF_BILLING, 'cierres-de-comision');
const STUDENT_GRADES = alumnoOrUnknown('/alumno/mis-carreras?tab=notas');
const STUDENT_PROGRAMS = alumnoOrUnknown('/alumno/mis-carreras');
const PAYMENT_PROOF_BILLING = billingStaff('facturacion');

export const NOTIFICATION_DESTINATIONS = {
  SECTION_EVAL_CONFIG_PROPOSED: [forRoles(['director'], toPath('/director/aprobaciones/configuracion-evaluacion'))],
  SECTION_EVAL_CONFIG_APPROVED: [TEACHER_SECTIONS],
  SECTION_EVAL_CONFIG_REJECTED: [TEACHER_SECTIONS],
  SECTION_EVAL_CONFIG_REMINDER: [TEACHER_SECTIONS],
  OFFER_CYCLE_SUBMITTED: [
    forRoles(['director'], withId(({ id }) => `/director/aprobaciones/oferta-academica/${id}`, toPath('/director/aprobaciones/oferta-academica'))),
  ],
  OFFER_CYCLE_APPROVED: [staff(['preceptor', 'director'], 'oferta-academica')],
  OFFER_CYCLE_REJECTED: [staff(['preceptor', 'director'], 'oferta-academica')],
  SECTION_MODIFICATION_REQUESTED: [staff(['director'], 'aprobaciones')],
  SECTION_MODIFICATION_APPROVED: [staff(['preceptor'], 'oferta-academica')],
  SECTION_MODIFICATION_REJECTED: [staff(['preceptor'], 'oferta-academica')],
  ATTENDANCE_CHANGE_REQUEST_APPROVED: [docenteOrUnknown('/docente/tomar-asistencia?tab=pedidos')],
  ATTENDANCE_CHANGE_REQUEST_REJECTED: [docenteOrUnknown('/docente/tomar-asistencia?tab=pedidos')],
  ATTENDANCE_JUSTIFICATION_APPROVED: [alumno('/alumno/mis-carreras?tab=asistencia')],
  ATTENDANCE_JUSTIFICATION_REJECTED: [alumno('/alumno/mis-carreras?tab=asistencia')],
  GRADUATION_REQUEST_CREATED: [STUDENT_PROGRAMS],
  GRADUATION_REQUEST_PENDING_REVIEW: [
    forRoles(['administrativo'], withId(({ id }) => `/administrativo/egresos-por-verificar/${id}`, toPath('/administrativo/egresos-por-verificar'))),
    forRoles(['director'], withId(({ id }) => `/director/aprobaciones/egresos/${id}`, toPath('/director/aprobaciones/egresos'))),
    STUDENT_PROGRAMS,
  ],
  GRADUATION_REQUEST_VERIFIED: [
    forRoles(['director'], withId(({ id }) => `/director/aprobaciones/egresos/${id}`, toPath('/director/aprobaciones/egresos'))),
    STUDENT_PROGRAMS,
  ],
  GRADUATION_REQUEST_DEBT_BLOCKED: [STUDENT_PROGRAMS],
  GRADUATION_REQUEST_APPROVED: [STUDENT_PROGRAMS],
  GRADUATION_REQUEST_REJECTED: [STUDENT_PROGRAMS],
  GRADUATION_REQUEST_OUTDATED: [forRoles(['administrativo'], toPath('/administrativo/egresos-por-verificar')), STUDENT_PROGRAMS],
  DIRECTOR_APPROVAL_REQUEST_APPROVED: [staff(STAFF_INTAKE, 'alumnos')],
  DIRECTOR_APPROVAL_REQUEST_REJECTED: [staff(STAFF_INTAKE, 'alumnos')],
  COURSE_WAITLIST_EXPIRED: [alumno('/alumno/mis-cursos')],
  SUSPENSION_LIFTED: [alumno('/alumno/inicio')],
  STUDENT_REACTIVATED: [alumno('/alumno/inicio')],
  SUBJECT_WITHDRAWN: [SUBJECTS_TAB],
  WAITLIST_PROMOTED: [SUBJECTS_TAB],
  SUBJECT_WITHDRAWN_TEACHER: [TEACHER_SECTIONS],
  SECTION_CHANGED: [SUBJECTS_TAB],
  SECTION_CHANGED_TEACHER: [TEACHER_SECTIONS],
  REGULAR_CERTIFICATE_READY: [alumno('/alumno/mis-carreras?tab=tramites')],
  GRADUATION_CERTIFICATE_ISSUED: [alumno('/alumno/mis-carreras?tab=tramites')],
  GRADUATION_CERTIFICATE_REVOKED: [alumno('/alumno/mis-carreras?tab=tramites')],
  GRADUATION_CERTIFICATE_REISSUED: [alumno('/alumno/mis-carreras?tab=tramites')],
  REGULAR_CERTIFICATE_REJECTED: [alumno('/alumno/mis-carreras?tab=tramites')],
  CERTIFICATE_EXCEPTION_REQUESTED: [staff(['director'], 'aprobaciones')],
  CERTIFICATE_EXCEPTION_DECIDED: [staff(['administrativo', 'director'], 'inicio')],
  PAYMENT_RECEIPT_APPROVED: [PAYMENTS],
  PAYMENT_RECEIPT_REJECTED: [PAYMENTS],
  ANNOUNCEMENT_PUBLISHED: [
    forRoles(['alumno', null], withId(({ id }) => `/alumno/inicio?aviso=${id}`, toPath('/alumno/inicio'))),
  ],
  ANNOUNCEMENT_UPDATED: [
    forRoles(['alumno', null], withId(({ id }) => `/alumno/inicio?aviso=${id}`, toPath('/alumno/inicio'))),
  ],
  EXAM_BOARD_RESCHEDULED: [alumno('/alumno/mis-carreras?tab=mesas-de-examen'), docente('/docente/mesas-de-examen')],
  EXAM_BOARD_CANCELED: [alumno('/alumno/mis-carreras?tab=mesas-de-examen'), docente('/docente/mesas-de-examen')],
  GRADES_PUBLISHED: [STUDENT_GRADES],
  GRADE_CORRECTED: [STUDENT_GRADES],
  SUBJECT_APPROVAL_REVERTED: [SUBJECTS_TAB],
  ENROLLMENT_FEE_EXEMPTION_GRANTED: [PAYMENTS],
  ENROLLMENT_FEE_EXEMPTION_REVOKED: [PAYMENTS],
  LATE_PAYMENT_SETTINGS_CREATED: [PAYMENT_PROOF_BILLING],
  AUTHORIZATION_REQUESTED: [
    forRoles(['administrativo'], withId(({ id }) => `/administrativo/aprobaciones/${id}`, toPath('/administrativo/aprobaciones'))),
    forRoles(['director'], withId(({ id }) => `/director/aprobaciones/facturacion/${id}`, toPath('/director/aprobaciones/facturacion'))),
  ],
  AUTHORIZATION_REQUEST_EXPIRED: [
    forRoles(['administrativo'], toPath('/administrativo/aprobaciones')),
    forRoles(['director'], toPath('/director/aprobaciones/facturacion')),
  ],
  CONFIGURATION_CHANGE_APPROVED: [billingStaff('precios')],
  CONFIGURATION_CHANGE_REJECTED: [billingStaff('precios')],
  CONFIGURATION_VERSION_CANCELLED: [billingStaff('precios')],
  PRICE_CHANGE_APPROVED: [billingStaff('precios')],
  PRICE_CHANGE_REJECTED: [billingStaff('precios')],
  PROGRAM_APPLICATION_APPROVED: [
    anyone(withId(({ id }) => `/mis-solicitudes/${id}`, toPath('/catalogo?tab=mis-solicitudes'))),
  ],
  PROGRAM_APPLICATION_RECEIVED: [
    forRoles(STAFF_INTAKE, withId(({ id, base }) => `/${base}/solicitudes-de-ingreso/${id}`, ({ base }) => `/${base}/solicitudes-de-ingreso`)),
  ],
  PROGRAM_ENROLLMENT_STALLED: [staff(STAFF_INTAKE, 'solicitudes-de-ingreso')],
  PAYMENT_LEFT_AS_ACCOUNT_CREDIT: [PAYMENTS, PAYMENT_PROOF_BILLING],
  GATEWAY_PAYMENT_WITH_GATEWAY_OFF: [PAYMENT_PROOF_BILLING],
  PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT: [PAYMENTS, PAYMENT_PROOF_BILLING],
  PAYMENT_CHARGED_BACK: [PAYMENTS, PAYMENT_PROOF_BILLING],
  CREDIT_NOTE_ISSUED: [alumno('/alumno/pagos/notas-de-credito'), PAYMENT_PROOF_BILLING],
  LATE_FEE_ISSUED: [PAYMENTS, PAYMENT_PROOF_BILLING],
  LATE_FEE_ISSUED_AFTER_FISCAL_WINDOW: [PAYMENT_PROOF_BILLING],
  LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW: [PAYMENT_PROOF_BILLING],
  COLLECTION_REMINDER: [PAYMENTS],
  OVERDUE_CONTACT_LIST_UPDATED: [PAYMENT_PROOF_BILLING],
  DISCOUNT_QUOTA_EXHAUSTED: [billingStaff('descuentos')],
  PAYMENT_RECEIPT_REVIEW_OVERDUE: [PAYMENT_PROOF_BILLING],
  INCONSISTENT_ACCOUNT_DETECTED: [PAYMENT_PROOF_BILLING],
  NIGHTLY_RUN_FAILED: [billingStaff('corridas-de-facturacion')],
  NIGHTLY_RUN_MISSED: [billingStaff('corridas-de-facturacion')],
  NIGHTLY_RUN_COMPLETED_WITH_ERRORS: [billingStaff('corridas-de-facturacion')],
  MONTHLY_INVOICE_RUN_MISSED: [billingStaff('emision-mensual')],
  MONTHLY_INVOICE_ITEM_RETRIES_EXHAUSTED: [billingStaff('emision-mensual')],
  DRAFT_INVOICES_AWAITING_REVIEW: [
    forRoles(STAFF_BILLING, withId(({ id, base }) => `/${base}/emision-mensual/${id}/borradores`, ({ base }) => `/${base}/emision-mensual`)),
  ],
  HOLIDAYS_MISSING_FOR_NEXT_YEAR: [forRoles(['administrativo'], toPath('/administrativo/feriados'))],
  CLOSURE_APPROVED: [alumno('/alumno/mis-carreras?tab=notas')],
  CLOSURE_APPROVED_SUMMARY: [TEACHER_SECTIONS, CLOSURES],
  CLOSURE_REOPENED: [TEACHER_SECTIONS, CLOSURES],
  CLOSURE_EXCEPTION_GRANTED: [SUBJECTS_TAB],
  CLOSURE_EXCEPTION_GRANTED_TEACHER: [TEACHER_SECTIONS],
  CLOSURE_CONFIRMATION_REMINDER: [TEACHER_SECTIONS, CLOSURES],
};

export function notificationDestination(notification, activeRole) {
  if (!notification) return null;
  const entries = NOTIFICATION_DESTINATIONS[notification.type];
  if (!entries) return null;
  const role = activeRole ? activeRole.toLowerCase() : null;
  const entry = entries.find((candidate) => candidate.roles === null || candidate.roles.includes(role));
  if (!entry) return null;
  return entry.build({ id: notification.relatedEntityId ?? null, base: role }) ?? null;
}
