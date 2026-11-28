# Inventario del manual de usuario (E35, rebanada C)

Fuente: `frontend/src/routes/roleMenu.js` (menús por rol) y `frontend/src/features/*/routes.jsx` (rutas reales), base `origin/develop` 79885507, 28-nov-2026. Subdirector hereda las rutas de Administrativo salvo las marcadas `excludeSubdirector` (`frontend/src/routes/featureRoutes.jsx`). Una entrada de menú solo se muestra si tiene pantalla real (`frontend/src/routes/menuAccess.js`).

Cifras: 146 rutas únicas (públicas y por rol), 114 entradas de menú entre los 7 roles, 55 tareas en el esqueleto.

Carpetas en inglés (kebab-case) por regla de nombres de archivo. Equivalencia con el Issue: primer-dia → `first-day`, permisos-y-roles → `roles-and-permissions`, armar-el-anio → `set-up-school-year`, mes-y-anio → `monthly-and-yearly`, alumnos → `students`, mesas-y-egreso → `exams-and-graduation`, importacion → `data-import`, avisos-e-invitaciones → `notices-and-invitations`, institucion → `institution`, cobranza → `billing`.

## 1. Tareas del esqueleto

| Tarea (archivo) | Título | Roles | Rebanada |
| --- | --- | --- | --- |
| `first-day/sign-in-and-activate.md` | Ingresar y activar la cuenta | Alumno, Docente, Preceptor, Administrativo, Subdirector, Director, Admin de plataforma | E |
| `first-day/recover-password.md` | Recuperar la contraseña | Alumno, Docente, Preceptor, Administrativo, Subdirector, Director, Admin de plataforma | E |
| `first-day/complete-profile.md` | Completar mi perfil | Alumno, Docente, Preceptor, Administrativo, Subdirector, Director, Admin de plataforma | E |
| `first-day/change-email.md` | Cambiar el email de la cuenta | Alumno, Docente, Preceptor, Administrativo, Subdirector, Director, Admin de plataforma | E |
| `first-day/switch-role.md` | Cambiar de rol | Alumno, Docente, Preceptor, Administrativo, Subdirector, Director, Admin de plataforma | E |
| `first-day/what-each-role-sees.md` | Entender qué ve cada rol | Alumno, Docente, Preceptor, Administrativo, Subdirector, Director, Admin de plataforma | E |
| `roles-and-permissions/who-can-do-what.md` | Saber quién puede hacer qué | Alumno, Docente, Preceptor, Administrativo, Subdirector, Director, Admin de plataforma | E |
| `roles-and-permissions/roles-and-permissions.md` | Asignar roles y permisos | Director, Subdirector | E |
| `roles-and-permissions/request-authorization.md` | Pedir y resolver una autorización | Administrativo, Subdirector, Director | E |
| `roles-and-permissions/staff-management.md` | Dar de alta y gestionar al personal | Administrativo, Subdirector, Director | E |
| `set-up-school-year/create-career.md` | Crear una carrera | Administrativo, Subdirector | F |
| `set-up-school-year/study-plan.md` | Versionar el plan de estudios | Administrativo, Subdirector | F |
| `set-up-school-year/create-subject-and-course.md` | Crear una materia y un curso | Administrativo, Subdirector, Director | F |
| `set-up-school-year/open-school-year.md` | Abrir el ciclo lectivo | Preceptor, Administrativo, Subdirector, Director | F |
| `set-up-school-year/open-cohort.md` | Abrir una cohorte | Preceptor | F |
| `set-up-school-year/assign-sections-and-preceptors.md` | Crear comisiones y asignar preceptores | Preceptor, Director | F |
| `set-up-school-year/review-academic-offer.md` | Revisar la oferta académica | Preceptor, Director | F |
| `set-up-school-year/approve-offer-cycle.md` | Aprobar la oferta del ciclo | Director | F |
| `set-up-school-year/holidays.md` | Cargar los feriados | Administrativo, Subdirector | F |
| `monthly-and-yearly/close-section.md` | Cerrar una comisión | Docente, Preceptor, Administrativo, Subdirector, Director | G |
| `monthly-and-yearly/end-of-term.md` | Hacer el fin de término | Preceptor, Administrativo, Subdirector, Director | G |
| `monthly-and-yearly/monthly-and-yearly-checklist.md` | Revisar lo que se hace cada mes y cada año | Preceptor, Administrativo, Subdirector, Director, Docente | G |
| `students/enrollment-applications.md` | Atender solicitudes de ingreso | Preceptor, Administrativo, Subdirector | H |
| `students/waitlists.md` | Gestionar las listas de espera | Preceptor, Administrativo, Subdirector, Director | H |
| `students/enroll-student.md` | Inscribir a un alumno | Preceptor, Administrativo, Subdirector, Director | H |
| `students/student-registry.md` | Consultar el padrón y el legajo del alumno | Preceptor, Administrativo, Subdirector, Director | H |
| `students/attendance-requests.md` | Resolver solicitudes de asistencia | Preceptor, Administrativo, Subdirector, Director | H |
| `students/take-attendance.md` | Tomar asistencia | Docente | H |
| `students/teacher-sections.md` | Ver mis comisiones | Docente | H |
| `students/grades-and-evaluation.md` | Cargar notas y revisar la evaluación | Docente, Director | H |
| `students/enroll-from-catalog.md` | Inscribirse desde el catálogo | Alumno | H |
| `students/student-courses-and-careers.md` | Ver mis cursos, carreras y calendario | Alumno | H |
| `students/withdraw-and-change-section.md` | Dar de baja una inscripción o cambiar de comisión | Alumno, Preceptor, Administrativo, Subdirector, Director | H |
| `students/enrollment-exceptions.md` | Resolver excepciones de inscripción | Director | H |
| `students/informative-sessions.md` | Reuniones informativas | Alumno | H |
| `exams-and-graduation/exam-boards.md` | Crear y gestionar mesas de examen | Docente, Preceptor, Administrativo, Subdirector, Director | I |
| `exams-and-graduation/exam-registration.md` | Inscribirse a una mesa de examen | Alumno | I |
| `exams-and-graduation/verify-graduation.md` | Verificar un egreso | Administrativo, Subdirector | I |
| `exams-and-graduation/issue-graduation-certificates.md` | Emitir egresos y certificados | Administrativo, Subdirector, Director | I |
| `exams-and-graduation/certificate-template-and-signatories.md` | Configurar la plantilla y los firmantes | Administrativo, Subdirector, Director | I |
| `exams-and-graduation/student-graduation-request.md` | Pedir la solicitud de egreso | Alumno | I |
| `exams-and-graduation/student-certificates.md` | Descargar mis constancias y verificar un certificado | Alumno | I |
| `exams-and-graduation/staff-certificates.md` | Emitir constancias | Administrativo, Subdirector, Director | I |
| `exams-and-graduation/debt-exception-certificates.md` | Emitir una constancia por excepción de deuda | Administrativo, Director | I |
| `data-import/import-initial-data.md` | Importar los datos iniciales | Director | J |
| `data-import/undo-import.md` | Deshacer una importación | Director | J |
| `notices-and-invitations/broadcasts.md` | Enviar una comunicación | Administrativo, Subdirector, Director | J |
| `notices-and-invitations/section-announcements.md` | Publicar avisos de comisión | Docente, Preceptor, Director | J |
| `notices-and-invitations/invitations.md` | Enviar invitaciones por oleadas | Administrativo, Subdirector, Director | J |
| `institution/institution-settings.md` | Configurar el instituto | Director | J |
| `institution/audit-log.md` | Consultar la auditoría | Director, Admin de plataforma | J |
| `institution/backups.md` | Hacer y restaurar backups | Director, Admin de plataforma | J |
| `institution/platform-modules.md` | Activar módulos de la plataforma | Admin de plataforma | J |
| `institution/first-director.md` | Crear el primer director | Admin de plataforma | J |
| `billing/billing-pending-redesign.md` | Cobranza (pendiente de rediseño) | Alumno, Administrativo, Subdirector, Director | J |

## 2. Pantallas y rutas reales (tabla principal)

Una fila por ruta. «En menú de» indica roles donde la ruta aparece como entrada de `roleMenu.js` («oculto» = declarada pero no visible). «Tarea» es el archivo que la cubre (la rebanada es E–J). Ninguna ruta queda sin sección salvo lo marcado abajo.

| Ruta | Roles con acceso | En menú de | Tarea del manual | Rebanada | Observación |
| --- | --- | --- | --- | --- | --- |
| `/activar` | todos | — | first-day/sign-in-and-activate | E | Entrada por enlace del correo |
| `/baja` | todos | — | notices-and-invitations/broadcasts | J | Enlace de darse de baja de comunicaciones (desde el correo) |
| `/cancelar-cambio-email` | todos | — | first-day/change-email | E | Entrada por enlace del correo |
| `/catalogo` | todos | alumno | students/enroll-from-catalog | H |  |
| `/catalogo/carreras/:id` | todos | — | students/enroll-from-catalog | H |  |
| `/catalogo/carreras/:id/inscribirme` | todos | — | students/enroll-from-catalog | H |  |
| `/catalogo/cursos/:id` | todos | — | students/enroll-from-catalog | H |  |
| `/catalogo/cursos/:id/inscribirme` | todos | — | students/enroll-from-catalog | H |  |
| `/confirmar-responsable` | todos | — | SIN SECCIÓN (verificar) | — |  |
| `/legal/privacidad` | todos | — | FUERA DEL MANUAL (pie legal) | — | Pie legal |
| `/legal/terminos` | todos | — | FUERA DEL MANUAL (pie legal) | — | Pie legal |
| `/login` | todos | — | first-day/sign-in-and-activate | E |  |
| `/mis-solicitudes` | todos | — | students/enroll-from-catalog | H | Redirección a /catalogo?tab=mis-solicitudes: posiblemente obsoleta |
| `/mis-solicitudes/:id` | todos | — | students/enroll-from-catalog | H |  |
| `/no-autorizado` | todos | — | first-day/sign-in-and-activate | E | Pantalla de error de acceso |
| `/olvide-mi-contrasena` | todos | — | first-day/recover-password | E |  |
| `/perfil` | todos | — | first-day/complete-profile | E |  |
| `/registrarse` | todos | — | first-day/sign-in-and-activate | E |  |
| `/revisar-cambio-email` | todos | — | first-day/change-email | E | Entrada por enlace del correo |
| `/terminos-pago-anual` | todos | — | billing/billing-pending-redesign | J |  |
| `/terminos-pago-anual/:versionNumber` | todos | — | billing/billing-pending-redesign | J |  |
| `/verificar-email` | todos | — | first-day/sign-in-and-activate | E | Entrada por enlace del correo |
| `/verificar/:code` | todos | — | exams-and-graduation/student-certificates | I | Entrada pública por código QR |
| `ajustes-del-instituto` | director | director | institution/institution-settings | J |  |
| `alumnos` | preceptor, administrativo, subdirector, director | preceptor, administrativo, subdirector, director | students/student-registry | H |  |
| `alumnos/:id` | preceptor, administrativo, subdirector, director | — | students/student-registry | H |  |
| `alumnos/:id/descuentos/nuevo` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `alumnos/:id/inscribir` | preceptor, administrativo, subdirector, director | — | students/enroll-student | H |  |
| `alumnos/:id/reembolsos/nuevo` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `alumnos/nuevo` | preceptor, administrativo, subdirector, director | — | students/student-registry | H |  |
| `aprobaciones` | administrativo, subdirector, director | administrativo, subdirector, director | roles-and-permissions/request-authorization | E |  |
| `aprobaciones/:id` | administrativo, subdirector | — | billing/billing-pending-redesign | J |  |
| `aprobaciones/configuracion-evaluacion` | director | — | students/grades-and-evaluation | H |  |
| `aprobaciones/egresos` | director | — | exams-and-graduation/issue-graduation-certificates | I |  |
| `aprobaciones/egresos/:id` | director | — | exams-and-graduation/issue-graduation-certificates | I |  |
| `aprobaciones/excepciones` | director | — | students/enrollment-exceptions | H |  |
| `aprobaciones/excepciones-por-reclamo` | director | — | exams-and-graduation/debt-exception-certificates | I |  |
| `aprobaciones/excepciones-por-reclamo/:id` | director | — | exams-and-graduation/debt-exception-certificates | I |  |
| `aprobaciones/excepciones/:id` | director | — | students/enrollment-exceptions | H |  |
| `aprobaciones/facturacion` | director | — | billing/billing-pending-redesign | J |  |
| `aprobaciones/facturacion/:id` | director | — | billing/billing-pending-redesign | J |  |
| `aprobaciones/oferta-academica` | director | — | set-up-school-year/approve-offer-cycle | F |  |
| `aprobaciones/oferta-academica/:id` | director | — | set-up-school-year/approve-offer-cycle | F |  |
| `auditoria` | director, platform_admin | director, platform_admin | institution/audit-log | J |  |
| `backups` | director, platform_admin | director, platform_admin | institution/backups | J |  |
| `calendario` | alumno | alumno | students/student-courses-and-careers | H |  |
| `cierre-de-comision/:sectionId` | preceptor, administrativo, subdirector, director | — | monthly-and-yearly/close-section | G |  |
| `cierres-de-comision` | administrativo, subdirector, director | administrativo, subdirector, director | monthly-and-yearly/close-section | G |  |
| `comunicaciones` | administrativo, subdirector, director | administrativo, subdirector, director | notices-and-invitations/broadcasts | J |  |
| `comunicaciones/:broadcastId` | administrativo, subdirector, director | — | notices-and-invitations/broadcasts | J |  |
| `comunicaciones/nuevo` | administrativo, subdirector, director | — | notices-and-invitations/broadcasts | J |  |
| `constancias` | administrativo, subdirector, director | administrativo, subdirector, director | exams-and-graduation/staff-certificates | I |  |
| `constancias-por-excepcion` | administrativo, director | administrativo, director | exams-and-graduation/debt-exception-certificates | I |  |
| `constancias/excepciones/:id` | administrativo | — | exams-and-graduation/debt-exception-certificates | I |  |
| `corridas-de-facturacion` | administrativo, subdirector, director | administrativo (oculto), subdirector (oculto), director (oculto) | billing/billing-pending-redesign | J | Oculta del menú; solo se llega por URL; cobranza pendiente de rediseño |
| `descuentos` | administrativo, subdirector, director | administrativo, subdirector, director | billing/billing-pending-redesign | J |  |
| `descuentos/:id` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `descuentos/nuevo` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `descuentos/promociones/nueva` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `egresos` | administrativo, subdirector, director | administrativo, subdirector, director | exams-and-graduation/issue-graduation-certificates | I |  |
| `egresos-por-verificar` | administrativo, subdirector | administrativo, subdirector | exams-and-graduation/verify-graduation | I |  |
| `egresos-por-verificar/:id` | administrativo, subdirector | — | exams-and-graduation/verify-graduation | I |  |
| `emision-mensual` | administrativo, subdirector, director | administrativo, subdirector, director | billing/billing-pending-redesign | J |  |
| `emision-mensual/:runId/borradores` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `esquemas-evaluacion` | director | director | students/grades-and-evaluation | H |  |
| `facturacion` | administrativo, subdirector, director | administrativo, subdirector, director | billing/billing-pending-redesign | J |  |
| `facturacion/alumnos/:studentId/cargar-comprobante` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `facturacion/cuotas/:invoiceId/nota-de-credito` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `feriados` | administrativo, subdirector | administrativo, subdirector | set-up-school-year/holidays | F |  |
| `financiero` | administrativo, subdirector, director | administrativo, subdirector, director | billing/billing-pending-redesign | J |  |
| `financiero/anios-lectivos` | director | — | set-up-school-year/open-school-year | F |  |
| `firmantes` | administrativo, subdirector, director | administrativo, subdirector, director | exams-and-graduation/certificate-template-and-signatories | I |  |
| `gestion-academica` | administrativo, subdirector, director | administrativo, subdirector, director | set-up-school-year/create-subject-and-course | F |  |
| `gestion-academica/:id` | administrativo, subdirector | — | set-up-school-year/create-subject-and-course | F |  |
| `gestion-academica/carreras/:id` | administrativo, subdirector | — | set-up-school-year/study-plan | F |  |
| `gestion-academica/carreras/:id/editar` | administrativo, subdirector | — | set-up-school-year/create-career | F |  |
| `gestion-academica/carreras/nueva` | administrativo, subdirector | — | set-up-school-year/create-career | F |  |
| `gestion-academica/cursos/:id` | administrativo, subdirector | — | set-up-school-year/create-subject-and-course | F |  |
| `gestion-academica/cursos/:id/editar` | administrativo, subdirector | — | set-up-school-year/create-subject-and-course | F |  |
| `gestion-academica/cursos/nueva` | administrativo, subdirector | — | set-up-school-year/create-subject-and-course | F |  |
| `gestion-academica/nueva` | administrativo, subdirector | — | set-up-school-year/create-subject-and-course | F |  |
| `importacion-inicial` | director | director | data-import/import-initial-data | J |  |
| `importacion-inicial/:id` | director | — | data-import/import-initial-data | J |  |
| `importacion-inicial/:id/deshacer` | director | — | data-import/undo-import | J |  |
| `importacion-inicial/nueva` | director | — | data-import/import-initial-data | J |  |
| `inicio` | alumno, administrativo, subdirector, director, platform_admin | alumno, docente, preceptor, administrativo, subdirector, director, platform_admin (oculto) | first-day/what-each-role-sees | E | Sin pantalla propia: redirige a la primera pantalla real del rol |
| `inscripciones/listas-de-espera` | preceptor, administrativo, subdirector, director | preceptor, administrativo, subdirector, director | students/waitlists | H |  |
| `inscripciones/listas-de-espera/:kind/:id` | preceptor, administrativo, subdirector, director | — | students/waitlists | H |  |
| `invitaciones` | administrativo, subdirector, director | administrativo, subdirector, director | notices-and-invitations/invitations | J |  |
| `invitaciones/:waveId` | administrativo, subdirector, director | — | notices-and-invitations/invitations | J |  |
| `invitaciones/:waveId/vista-previa` | administrativo, subdirector, director | — | notices-and-invitations/invitations | J |  |
| `invitaciones/nueva` | administrativo, subdirector, director | — | notices-and-invitations/invitations | J |  |
| `listas-de-espera` | preceptor, administrativo, subdirector, director | — | students/waitlists | H | Redirección legada a inscripciones/listas-de-espera: posiblemente obsoleta |
| `listas-de-espera/:kind/:id` | preceptor, administrativo, subdirector, director | — | students/waitlists | H | Redirección legada: posiblemente obsoleta |
| `medios-de-pago` | administrativo, subdirector, director | administrativo, subdirector, director | billing/billing-pending-redesign | J |  |
| `mesas-de-examen` | docente, preceptor, administrativo, subdirector, director | docente, preceptor, administrativo, subdirector, director | exams-and-graduation/exam-boards | I |  |
| `mis-carreras` | alumno | alumno | students/student-courses-and-careers | H |  |
| `mis-carreras/materias/:id` | alumno | — | students/student-courses-and-careers | H |  |
| `mis-certificados` | alumno | alumno | exams-and-graduation/student-certificates | I |  |
| `mis-comisiones` | docente | docente | students/teacher-sections | H |  |
| `mis-comisiones/:sectionId` | docente | — | students/teacher-sections | H |  |
| `mis-comisiones/:sectionId/avisos/:avisoId/editar` | docente | — | notices-and-invitations/section-announcements | J |  |
| `mis-comisiones/:sectionId/avisos/nuevo` | docente | — | notices-and-invitations/section-announcements | J |  |
| `mis-comisiones/:sectionId/cierre` | docente | — | monthly-and-yearly/close-section | G |  |
| `mis-cursos` | alumno | alumno | students/student-courses-and-careers | H |  |
| `mis-cursos/:id` | alumno | — | students/student-courses-and-careers | H |  |
| `modulos` | platform_admin | platform_admin | institution/platform-modules | J | Solo plataforma |
| `oferta-academica` | preceptor, director | preceptor, director | set-up-school-year/review-academic-offer | F |  |
| `oferta-academica/calendario` | preceptor, director | — | set-up-school-year/review-academic-offer | F |  |
| `oferta-academica/carreras/:programaId` | preceptor, director | — | set-up-school-year/review-academic-offer | F |  |
| `oferta-academica/ciclos/:cycleId/comisiones/:sectionId/avisos` | preceptor, director | — | notices-and-invitations/section-announcements | J |  |
| `oferta-academica/ciclos/:cycleId/comisiones/:sectionId/avisos/:announcementId` | preceptor, director | — | notices-and-invitations/section-announcements | J |  |
| `oferta-academica/ciclos/:cycleId/comisiones/:sectionId/inscriptos` | preceptor, director | — | students/student-registry | H |  |
| `oferta-academica/ciclos/:cycleId/comisiones/:sectionId/notas` | director | — | students/grades-and-evaluation | H |  |
| `oferta-academica/cursos/:cursoId` | preceptor, director | — | set-up-school-year/assign-sections-and-preceptors | F |  |
| `oferta-academica/nuevo-ciclo` | preceptor | — | set-up-school-year/open-cohort | F |  |
| `opciones` | alumno | — | first-day/complete-profile | E | Redirección legada a /perfil: posiblemente obsoleta |
| `pagos` | alumno | alumno | billing/billing-pending-redesign | J |  |
| `pagos/:invoiceId/online` | alumno | — | billing/billing-pending-redesign | J |  |
| `pagos/:invoiceId/seleccion` | alumno | — | billing/billing-pending-redesign | J |  |
| `pagos/:invoiceId/transferencia` | alumno | — | billing/billing-pending-redesign | J |  |
| `pagos/carrera/:enrollmentId` | alumno | — | billing/billing-pending-redesign | J |  |
| `pagos/carrera/:enrollmentId/matricula-anticipada` | alumno | — | billing/billing-pending-redesign | J |  |
| `pagos/curso/:enrollmentId` | alumno | — | billing/billing-pending-redesign | J |  |
| `pagos/estado` | alumno | — | billing/billing-pending-redesign | J |  |
| `pagos/notas-de-credito` | alumno | — | billing/billing-pending-redesign | J |  |
| `periodos` | preceptor, administrativo, subdirector, director | preceptor, administrativo, subdirector, director | set-up-school-year/open-school-year | F |  |
| `periodos/:termId/editar` | preceptor, administrativo, subdirector, director | — | set-up-school-year/open-school-year | F |  |
| `periodos/nuevo` | preceptor, administrativo, subdirector, director | — | set-up-school-year/open-school-year | F |  |
| `permisos` | subdirector, director | subdirector, director | roles-and-permissions/roles-and-permissions | E |  |
| `personal` | administrativo, subdirector, director | administrativo, subdirector, director | roles-and-permissions/staff-management | E |  |
| `personal/:id` | administrativo, subdirector, director | — | roles-and-permissions/staff-management | E |  |
| `plantilla-de-certificados` | administrativo, subdirector, director | administrativo, subdirector, director | exams-and-graduation/certificate-template-and-signatories | I |  |
| `precios` | administrativo, subdirector, director | administrativo, subdirector, director | billing/billing-pending-redesign | J |  |
| `precios/:priceId` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `precios/:priceId/nueva-version` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `primer-director` | platform_admin | platform_admin | institution/first-director | J |  |
| `recordatorios-de-pago` | administrativo, subdirector, director | administrativo, subdirector, director | billing/billing-pending-redesign | J |  |
| `recordatorios-de-pago/mails-simulados` | administrativo, subdirector, director | — | billing/billing-pending-redesign | J |  |
| `reuniones-informativas` | alumno | alumno | students/informative-sessions | H |  |
| `revisar-configuracion/:sectionId` | director | — | students/grades-and-evaluation | H |  |
| `solicitudes-de-asistencia` | preceptor, administrativo, subdirector, director | preceptor, administrativo, subdirector, director | students/attendance-requests | H |  |
| `solicitudes-de-ingreso` | preceptor, administrativo, subdirector | preceptor, administrativo, subdirector | students/enrollment-applications | H |  |
| `solicitudes-de-ingreso/:id` | preceptor, administrativo, subdirector | — | students/enrollment-applications | H |  |
| `terminos-del-pago-anual` | director | director | billing/billing-pending-redesign | J |  |
| `tomar-asistencia` | docente | docente | students/take-attendance | H |  |

## 3. Trámites del alumno que resuelve el personal

| Trámite | Lo ve el alumno en | Lo resuelve el personal en | Tarea | Rebanada | Pendiente de verificar |
| --- | --- | --- | --- | --- | --- |
| Constancia de alumno regular | Mis carreras › Trámites | `constancias` (administrativo, subdirector, director) | `exams-and-graduation/staff-certificates` | I | Qué tipos emite el personal: la pantalla de constancias no se leyó en detalle |
| Constancia de inscripción a examen | Mis carreras › Trámites | `constancias` | `exams-and-graduation/staff-certificates` | I | Ídem |
| Constancia de examen | Mis carreras › Trámites | `constancias` | `exams-and-graduation/staff-certificates` | I | Ídem |
| Constancia de materias aprobadas | Mis carreras › Trámites | `constancias` | `exams-and-graduation/staff-certificates` | I | Ídem |
| Baja de inscripción | Mis carreras › materia (`AlumnoMateriaDetalle`) | `alumnos/:id`, pestaña Académico (`WithdrawEnrollmentDialog`) | `students/withdraw-and-change-section` | H | Qué roles ven el botón de baja |
| Cambio de comisión | Trámites › Cambio de comisión (`ChangeMySectionFlow`) | Sin pantalla propia del personal encontrada | `students/withdraw-and-change-section` | H | Si el personal tiene un camino propio |
| Solicitudes de asistencia | Tomar asistencia › Solicitar modificación (docente) | `solicitudes-de-asistencia` (preceptor, administrativo, subdirector, director) | `students/attendance-requests` | H | Historial y flujo de resolución |
| Inscripción a mesa de examen | Mis carreras › pestaña de mesas (`TabMesasDeExamen`) | `mesas-de-examen` (preceptor, administrativo, subdirector, director) | `exams-and-graduation/exam-registration` | I | Si el personal inscribe alumnos a mesa desde otra pantalla |
| Cambio de email | Mi perfil; enlaces `/revisar-cambio-email` y `/cancelar-cambio-email` | Sin pantalla de cambio por el personal. En el legajo solo aparece el bloqueo de reseteo por cambio pendiente | `first-day/change-email` | E | Si el personal debe poder iniciarlo |
| Egreso y certificados | Trámites › Solicitud de egreso; Mis certificados | `egresos-por-verificar`, `egresos`, `aprobaciones/egresos`, `plantilla-de-certificados`, `firmantes` | `exams-and-graduation/verify-graduation`, `issue-graduation-certificates`, `certificate-template-and-signatories` | I | — |
| Excepciones por deuda | Constancias (pestaña propia del administrativo) | `constancias-por-excepcion` (administrativo, director; subdirector excluido), `aprobaciones/excepciones-por-reclamo` | `exams-and-graduation/debt-exception-certificates` | I | Quién aprueba el reclamo |

## 4. Pantallas sin entrada de menú

Se llegan desde otra pantalla o desde una notificación. Si la entrada no está confirmada en el código, la columna lo dice.

| Ruta | Roles | Entrada a la pantalla | Confirmado en código | Tarea del manual | Rebanada |
| --- | --- | --- | --- | --- | --- |
| `alumnos/:id` | Preceptor, Administrativo, Subdirector, Director | Dentro de «alumnos» (por la ruta) | No: inferido por la ruta | students/student-registry | H |
| `alumnos/:id/descuentos/nuevo` | Administrativo, Subdirector, Director | Dentro de «alumnos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `alumnos/:id/inscribir` | Preceptor, Administrativo, Subdirector, Director | Dentro de «alumnos» (por la ruta) | No: inferido por la ruta | students/enroll-student | H |
| `alumnos/:id/reembolsos/nuevo` | Administrativo, Subdirector, Director | Dentro de «alumnos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `alumnos/nuevo` | Preceptor, Administrativo, Subdirector, Director | Dentro de «alumnos» (por la ruta) | No: inferido por la ruta | students/student-registry | H |
| `aprobaciones/:id` | Administrativo, Subdirector | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `aprobaciones/configuracion-evaluacion` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | students/grades-and-evaluation | H |
| `aprobaciones/egresos` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | exams-and-graduation/issue-graduation-certificates | I |
| `aprobaciones/egresos/:id` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | exams-and-graduation/issue-graduation-certificates | I |
| `aprobaciones/excepciones` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | students/enrollment-exceptions | H |
| `aprobaciones/excepciones-por-reclamo` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | exams-and-graduation/debt-exception-certificates | I |
| `aprobaciones/excepciones-por-reclamo/:id` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | exams-and-graduation/debt-exception-certificates | I |
| `aprobaciones/excepciones/:id` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | students/enrollment-exceptions | H |
| `aprobaciones/facturacion` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `aprobaciones/facturacion/:id` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `aprobaciones/oferta-academica` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | set-up-school-year/approve-offer-cycle | F |
| `aprobaciones/oferta-academica/:id` | Director | Dentro de «aprobaciones» (por la ruta) | No: inferido por la ruta | set-up-school-year/approve-offer-cycle | F |
| `cierre-de-comision/:sectionId` | Preceptor, Administrativo, Subdirector, Director | Cierres de comisión (lista) y Comisión › Inscriptos del preceptor | Sí: closureList.js, StaffSectionRoster.jsx | monthly-and-yearly/close-section | G |
| `comunicaciones/:broadcastId` | Administrativo, Subdirector, Director | Dentro de «comunicaciones» (por la ruta) | No: inferido por la ruta | notices-and-invitations/broadcasts | J |
| `comunicaciones/nuevo` | Administrativo, Subdirector, Director | Dentro de «comunicaciones» (por la ruta) | No: inferido por la ruta | notices-and-invitations/broadcasts | J |
| `constancias/excepciones/:id` | Administrativo | Constancias (pestaña de excepciones propias) | Sí: OwnExceptionsTab.jsx | exams-and-graduation/debt-exception-certificates | I |
| `corridas-de-facturacion` | Administrativo, Subdirector, Director | Sin entrada de menú: botón dentro de Facturación y notificaciones de corrida | Sí: AdministrativoFacturacion.jsx, notificationDestination.js | billing/billing-pending-redesign | J |
| `descuentos/:id` | Administrativo, Subdirector, Director | Dentro de «descuentos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `descuentos/nuevo` | Administrativo, Subdirector, Director | Dentro de «descuentos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `descuentos/promociones/nueva` | Administrativo, Subdirector, Director | Dentro de «descuentos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `egresos-por-verificar/:id` | Administrativo, Subdirector | Dentro de «egresos-por-verificar» (por la ruta) | No: inferido por la ruta | exams-and-graduation/verify-graduation | I |
| `emision-mensual/:runId/borradores` | Administrativo, Subdirector, Director | Dentro de «emision-mensual» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `facturacion/alumnos/:studentId/cargar-comprobante` | Administrativo, Subdirector, Director | Dentro de «facturacion» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `facturacion/cuotas/:invoiceId/nota-de-credito` | Administrativo, Subdirector, Director | Dentro de «facturacion» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `financiero/anios-lectivos` | Director | Dentro de «financiero» (por la ruta) | No: inferido por la ruta | set-up-school-year/open-school-year | F |
| `gestion-academica/:id` | Administrativo, Subdirector | Dentro de «gestion-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/create-subject-and-course | F |
| `gestion-academica/carreras/:id` | Administrativo, Subdirector | Dentro de «gestion-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/study-plan | F |
| `gestion-academica/carreras/:id/editar` | Administrativo, Subdirector | Dentro de «gestion-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/create-career | F |
| `gestion-academica/carreras/nueva` | Administrativo, Subdirector | Dentro de «gestion-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/create-career | F |
| `gestion-academica/cursos/:id` | Administrativo, Subdirector | Dentro de «gestion-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/create-subject-and-course | F |
| `gestion-academica/cursos/:id/editar` | Administrativo, Subdirector | Dentro de «gestion-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/create-subject-and-course | F |
| `gestion-academica/cursos/nueva` | Administrativo, Subdirector | Dentro de «gestion-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/create-subject-and-course | F |
| `gestion-academica/nueva` | Administrativo, Subdirector | Dentro de «gestion-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/create-subject-and-course | F |
| `importacion-inicial/:id` | Director | Dentro de «importacion-inicial» (por la ruta) | No: inferido por la ruta | data-import/import-initial-data | J |
| `importacion-inicial/:id/deshacer` | Director | Dentro de «importacion-inicial» (por la ruta) | No: inferido por la ruta | data-import/undo-import | J |
| `importacion-inicial/nueva` | Director | Dentro de «importacion-inicial» (por la ruta) | No: inferido por la ruta | data-import/import-initial-data | J |
| `inicio` | Admin de plataforma | Sin padre en el menú | No: inferido por la ruta | first-day/what-each-role-sees | E |
| `inscripciones/listas-de-espera/:kind/:id` | Preceptor, Administrativo, Subdirector, Director | Dentro de «inscripciones/listas-de-espera» (por la ruta) | No: inferido por la ruta | students/waitlists | H |
| `invitaciones/:waveId` | Administrativo, Subdirector, Director | Dentro de «invitaciones» (por la ruta) | No: inferido por la ruta | notices-and-invitations/invitations | J |
| `invitaciones/:waveId/vista-previa` | Administrativo, Subdirector, Director | Dentro de «invitaciones» (por la ruta) | No: inferido por la ruta | notices-and-invitations/invitations | J |
| `invitaciones/nueva` | Administrativo, Subdirector, Director | Dentro de «invitaciones» (por la ruta) | No: inferido por la ruta | notices-and-invitations/invitations | J |
| `listas-de-espera` | Preceptor, Administrativo, Subdirector, Director | Sin padre en el menú | No: inferido por la ruta | students/waitlists | H |
| `listas-de-espera/:kind/:id` | Preceptor, Administrativo, Subdirector, Director | Sin padre en el menú | No: inferido por la ruta | students/waitlists | H |
| `mis-carreras/materias/:id` | Alumno | Dentro de «mis-carreras» (por la ruta) | No: inferido por la ruta | students/student-courses-and-careers | H |
| `mis-comisiones/:sectionId` | Docente | Dentro de «mis-comisiones» (por la ruta) | No: inferido por la ruta | students/teacher-sections | H |
| `mis-comisiones/:sectionId/avisos/:avisoId/editar` | Docente | Dentro de «mis-comisiones» (por la ruta) | No: inferido por la ruta | notices-and-invitations/section-announcements | J |
| `mis-comisiones/:sectionId/avisos/nuevo` | Docente | Dentro de «mis-comisiones» (por la ruta) | No: inferido por la ruta | notices-and-invitations/section-announcements | J |
| `mis-comisiones/:sectionId/cierre` | Docente | Dentro de «mis-comisiones» (por la ruta) | No: inferido por la ruta | monthly-and-yearly/close-section | G |
| `mis-cursos/:id` | Alumno | Dentro de «mis-cursos» (por la ruta) | No: inferido por la ruta | students/student-courses-and-careers | H |
| `oferta-academica/calendario` | Preceptor, Director | Oferta académica › carrera (botón de calendario) | Sí: PreceptorDetalleCarrera.jsx | set-up-school-year/review-academic-offer | F |
| `oferta-academica/carreras/:programaId` | Preceptor, Director | Dentro de «oferta-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/review-academic-offer | F |
| `oferta-academica/ciclos/:cycleId/comisiones/:sectionId/avisos` | Preceptor, Director | Dentro de «oferta-academica» (por la ruta) | No: inferido por la ruta | notices-and-invitations/section-announcements | J |
| `oferta-academica/ciclos/:cycleId/comisiones/:sectionId/avisos/:announcementId` | Preceptor, Director | Dentro de «oferta-academica» (por la ruta) | No: inferido por la ruta | notices-and-invitations/section-announcements | J |
| `oferta-academica/ciclos/:cycleId/comisiones/:sectionId/inscriptos` | Preceptor, Director | Dentro de «oferta-academica» (por la ruta) | No: inferido por la ruta | students/student-registry | H |
| `oferta-academica/ciclos/:cycleId/comisiones/:sectionId/notas` | Director | Dentro de «oferta-academica» (por la ruta) | No: inferido por la ruta | students/grades-and-evaluation | H |
| `oferta-academica/cursos/:cursoId` | Preceptor, Director | Dentro de «oferta-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/assign-sections-and-preceptors | F |
| `oferta-academica/nuevo-ciclo` | Preceptor | Dentro de «oferta-academica» (por la ruta) | No: inferido por la ruta | set-up-school-year/open-cohort | F |
| `opciones` | Alumno | Sin padre en el menú | No: inferido por la ruta | first-day/complete-profile | E |
| `pagos/:invoiceId/online` | Alumno | Dentro de «pagos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `pagos/:invoiceId/seleccion` | Alumno | Dentro de «pagos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `pagos/:invoiceId/transferencia` | Alumno | Dentro de «pagos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `pagos/carrera/:enrollmentId` | Alumno | Dentro de «pagos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `pagos/carrera/:enrollmentId/matricula-anticipada` | Alumno | Dentro de «pagos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `pagos/curso/:enrollmentId` | Alumno | Dentro de «pagos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `pagos/estado` | Alumno | Dentro de «pagos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `pagos/notas-de-credito` | Alumno | Dentro de «pagos» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `periodos/:termId/editar` | Preceptor, Administrativo, Subdirector, Director | Dentro de «periodos» (por la ruta) | No: inferido por la ruta | set-up-school-year/open-school-year | F |
| `periodos/nuevo` | Preceptor, Administrativo, Subdirector, Director | Dentro de «periodos» (por la ruta) | No: inferido por la ruta | set-up-school-year/open-school-year | F |
| `personal/:id` | Administrativo, Subdirector, Director | Dentro de «personal» (por la ruta) | No: inferido por la ruta | roles-and-permissions/staff-management | E |
| `precios/:priceId` | Administrativo, Subdirector, Director | Dentro de «precios» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `precios/:priceId/nueva-version` | Administrativo, Subdirector, Director | Dentro de «precios» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `recordatorios-de-pago/mails-simulados` | Administrativo, Subdirector, Director | Dentro de «recordatorios-de-pago» (por la ruta) | No: inferido por la ruta | billing/billing-pending-redesign | J |
| `revisar-configuracion/:sectionId` | Director | Aprobaciones › configuración de evaluación | Sí: DirectorAprobaciones.jsx, DirectorAprobacionesConfigEvaluacion.jsx | students/grades-and-evaluation | H |
| `solicitudes-de-ingreso/:id` | Preceptor, Administrativo, Subdirector | Solicitudes de ingreso (lista) y notificaciones | Sí: notificationDestination.js | students/enrollment-applications | H |

## 5. Pantallas fuera de todo menú

Entradas por enlace, correo, QR o pie. El personal no las busca en el menú, pero el manual debe explicarlas si el personal las guía a alguien.

- Públicas: `/login`, `/registrarse`, `/olvide-mi-contrasena`, `/activar`, `/verificar-email`, `/revisar-cambio-email`, `/cancelar-cambio-email`, `/baja` (desde el correo), `/no-autorizado`, `/verificar/:code` (QR), `/legal/terminos` y `/legal/privacidad` (pie; fuera del manual).
- `/perfil` (Mi perfil) no está en el menú de rol sino en el menú de usuario: se cubre en `first-day/complete-profile`.
- `/confirmar-responsable`: no tiene entrada en ningún menú ni enlace encontrado en esta lectura. Hay que confirmar quién llega y para qué; hasta entonces queda como «SIN SECCIÓN (verificar)».
- `corridas-de-facturacion`: no está en el menú (oculta) y se llega desde Facturación y desde notificaciones. Pendiente de rediseño.

## 6. Posiblemente obsoletas o no visibles

- `opciones` (alumno): redirige a `/perfil`. Hay test de la redirección (`legacyStudentOptionsRedirect.test.jsx`): candidata a quitar cuando no haya enlaces viejos.
- `listas-de-espera` y `listas-de-espera/:kind/:id`: redirecciones legadas a `inscripciones/listas-de-espera` (test `legacyWaitlistRedirect.test.jsx`). El manual no debe citarlas.
- `/mis-solicitudes`: redirige a `/catalogo?tab=mis-solicitudes`. El manual debe nombrar el catálogo.
- Inicio de **docente** y **preceptor**: aparece en `roleMenu.js` pero no tiene pantalla propia; el ítem se oculta (`visibleMenuItems`) y la ruta raíz redirige a la primera pantalla real. Inicio de **Admin de plataforma** está oculto y redirige a `primer-director`.
- Cobranza completa (pagos del alumno, facturación, descuentos, precios, emisión mensual, recordatorios, medios de pago, financiero, términos del pago anual y aprobaciones de facturación): gateada por el módulo de facturación apagado. Sección «pendiente de rediseño»; no se escriben pasos hasta el rediseño.
- Ninguna pantalla del menú usa hoy `Placeholder` (`PENDING_SCREENS` vacío en `roleMenu.test.jsx`).

## 7. Dudas para validar antes de escribir el contenido

- Qué roles ven el botón de baja de inscripción desde el legajo.
- Si el personal emite las cuatro constancias o solo algunas (la lectura de la pantalla de constancias fue superficial).
- Si el personal debe poder iniciar un cambio de email de un alumno.
- Si la inscripción a mesa la hace el alumno siempre o también el personal.
- Si `/confirmar-responsable` sigue vigente.
- Cambio de nombre de carpetas (inglés) frente a los nombres propuestos en el Issue.
