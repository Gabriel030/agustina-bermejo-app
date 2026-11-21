const ACTIONS = {
  STAFF_WITHDRAWN: ['Baja de personal', 'danger'],
  STAFF_REACTIVATED: ['Reactivación de personal', 'success'],
  STAFF_CREATED: ['Alta de personal', 'success'],
  PASSWORD_RESET_REQUESTED: ['Reseteo de clave solicitado', 'warning'],
  PASSWORD_RESET_COMPLETED: ['Reseteo de clave completado', 'warning'],
  PASSWORD_CHANGED: ['Cambio de clave', 'warning'],
  STUDENT_WITHDRAWN: ['Baja de alumno', 'danger'],
  STUDENT_REACTIVATED: ['Reactivación de alumno', 'success'],
  ENROLLMENT_WITHDRAWN_BY_STAFF: ['Baja de inscripción', 'danger'],
  ENROLLMENT_SECTION_CHANGED_BY_STAFF: ['Cambio de comisión', 'accent'],
  DATA_IMPORT_CREATED: ['Importación creada', 'neutral'],
  DATA_IMPORT_COMMITTED: ['Importación confirmada', 'success'],
  DATA_IMPORT_RETRIED: ['Importación reintentada', 'neutral'],
  DATA_IMPORT_SEALED: ['Importación sellada', 'neutral'],
  DATA_IMPORT_ROLLED_BACK: ['Importación revertida', 'danger'],
  DATA_IMPORT_ROLLBACK_BLOCKED: ['Reversión bloqueada', 'warning'],
  DATA_IMPORT_REPORT_DOWNLOADED: ['Reporte descargado', 'neutral'],
  CONTACT_CONSENT_GRANTED: ['Consentimiento otorgado', 'success'],
  CONTACT_CONSENT_REVOKED: ['Consentimiento revocado', 'warning'],
  WAITLIST_PROMOTED_AUTOMATICALLY: ['Promovido de lista de espera (auto)', 'success'],
  WAITLIST_PROMOTED_BY_STAFF: ['Promovido de lista de espera', 'success'],
  WAITLIST_PRIORITIZED_BY_STAFF: ['Priorizado en lista de espera', 'accent'],
  WAITLIST_REMOVED_BY_STAFF: ['Quitado de lista de espera', 'danger'],
  WAITLIST_EXPIRED: ['Lista de espera vencida', 'neutral'],
  BROADCAST_CONFIRMED: ['Difusión confirmada', 'success'],
  BROADCAST_CANCELLED: ['Difusión cancelada', 'danger'],
  BROADCAST_RECIPIENT_DATA_ANONYMIZED: ['Datos de destinatarios anonimizados', 'warning'],
  BROADCAST_RECIPIENTS_PURGED: ['Destinatarios de difusión eliminados', 'warning'],
  BROADCAST_COMPLETED: ['Difusión completada', 'success'],
  WHATSAPP_API_ENABLED: ['WhatsApp API habilitada', 'success'],
  WHATSAPP_API_DISABLED: ['WhatsApp API deshabilitada', 'warning'],
  GATEWAY_PAYMENT_AMOUNT_MISMATCH: ['Pago con monto distinto al esperado', 'danger'],
  GATEWAY_PAYMENT_CHECKOUT_EXPIRED: ['Checkout de pago vencido', 'neutral'],
  GATEWAY_PAYMENT_UNMATCHED: ['Pago sin comprobante asociado', 'warning'],
  GATEWAY_PAYMENT_DUPLICATE_FOR_CHECKOUT: ['Pago duplicado para un checkout', 'danger'],
  CERTIFICATE_SIGNATORY_CREATED: ['Firmante de certificado creado', 'success'],
  CERTIFICATE_SIGNATORY_UPDATED: ['Firmante de certificado modificado', 'neutral'],
  CERTIFICATE_SIGNATORY_PERMS_CHANGED: ['Permisos de firmante modificados', 'accent'],
  CERTIFICATE_SIGNATORY_DEACTIVATED: ['Firmante de certificado desactivado', 'danger'],
  CERTIFICATE_SIGNATORY_ACTIVATED: ['Firmante de certificado activado', 'success'],
  CERTIFICATE_SIGNATORY_SIGNATURE_UPLOADED: ['Firma de certificado cargada', 'neutral'],
  CERTIFICATE_SIGNATORY_SIGNATURE_DELETED: ['Firma de certificado eliminada', 'warning'],
  PERMISSION_GRID_CHANGED: ['Grilla de permisos modificada', 'accent'],
  GRADUATION_CERTIFICATE_DOWNLOADED: ['Certificado de egreso descargado', 'accent'],
  GRADUATION_CERTIFICATE_PDF_MISMATCH: ['PDF de certificado de egreso con hash distinto', 'warning'],
};

const ENTITIES = {
  PERSON: 'Persona',
  STUDENT: 'Alumno',
  SECTION_ENROLLMENT: 'Inscripción a comisión',
  COURSE_SECTION_ENROLLMENT: 'Inscripción a materia',
  PROGRAM_ENROLLMENT: 'Inscripción a carrera',
  COURSE_ENROLLMENT: 'Inscripción a cursada',
  DATA_IMPORT_BATCH: 'Lote de importación',
  BROADCAST: 'Difusión',
  INSTITUTE_SETTING: 'Configuración del instituto',
  INVOICE: 'Factura',
  GATEWAY_PAYMENT: 'Pago en línea',
  CERTIFICATE_SIGNATORY: 'Firmante de certificado',
  PERMISSION: 'Permiso',
  GRADUATION_CERTIFICATE: 'Certificado de egreso',
};

const ROLES = {
  DIRECTOR: ['Director', 'accent'],
  ADMINISTRATIVO: ['Administrativo', 'neutral'],
  PRECEPTOR: ['Preceptor', 'success'],
  DOCENTE: ['Docente', 'warning'],
  ALUMNO: ['Alumno', 'neutral'],
  PLATFORM_ADMIN: ['Plataforma', 'accent'],
  SYSTEM: ['Sistema', 'neutral'],
};

export const AUDIT_ACTION_CODES = Object.keys(ACTIONS);
export const AUDIT_ENTITY_CODES = Object.keys(ENTITIES);
export const AUDIT_ROLE_CODES = Object.keys(ROLES);

export const auditActionLabel = (code) => ACTIONS[code]?.[0] ?? code;
export const auditActionTone = (code) => ACTIONS[code]?.[1] ?? 'neutral';
export const auditEntityLabel = (code) => ENTITIES[code] ?? code;
export const auditRoleLabel = (code) => ROLES[code]?.[0] ?? code ?? '—';
export const auditRoleTone = (code) => ROLES[code]?.[1] ?? 'neutral';
