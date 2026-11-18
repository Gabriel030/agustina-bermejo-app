# Derechos de las personas sobre sus datos (Épica 29, C5)

Procedimiento interno para atender pedidos de acceso, rectificación y supresión de datos personales de alumnos, docentes y personal. Es un documento de proceso: **no cambia código**. Todo lo que hoy el sistema no puede hacer está en la sección 8 como paquete futuro.

**Estado: borrador técnico. Pendiente de validación de abogado (C4).** El repo no contiene artículos de la Ley 25.326 ni de su reglamentación (C4 lista la ley como referencia de los textos definitivos, sin citar artículos), así que este documento no cita ninguno. Cada punto marcado con **[ABOGADO]** requiere criterio legal antes de aplicarse como regla firme.

## 1. Quién responde y cómo entra un pedido

| Tema | Definición |
|---|---|
| Canal de entrada | Correo del instituto o pedido presencial en secretaría. El dato de contacto y el nombre del responsable de la base los define C4 **[ABOGADO]** (hoy no están en el repo) |
| Quién registra | ADMINISTRATIVO (carga el pedido y verifica identidad) |
| Quién decide | DIRECTOR (aprueba cualquier rectificación sensible, y toda negativa total o parcial de supresión) |
| Soporte técnico | Plataforma / quien opera el despliegue, solo para consultas SQL de la sección 4 y para purgas fuera de los jobs |
| Registro del pedido | Hoy no existe tabla de pedidos: se lleva una planilla fuera del sistema (fecha de recepción, quién pide, tipo, decisión, fecha de respuesta). Ver paquete F-2 |

Verificación de identidad antes de entregar o modificar algo: el pedido debe llegar desde el correo registrado en `person.email`, o presencialmente con el DNI que figura en `person.dni`. Para menores, responde el responsable que figura en la ficha del alumno (`student.guardian_*`) o el que confirmó la aceptación (`legal_guardian_confirmation`). La edad de referencia la define `LegalAge` (`backend/src/main/java/com/red_escuela/backend/identity/policy/LegalAge.java`) **[ABOGADO]** (qué se exige para menores).

## 2. Plazos

El repo no define plazos legales de respuesta. Propuesta operativa interna, a validar:

| Paso | Plazo propuesto |
|---|---|
| Acuse de recibo | 3 días hábiles |
| Respuesta de acceso | 10 días corridos **[ABOGADO]** |
| Respuesta de rectificación o supresión | 5 días hábiles **[ABOGADO]** |

Los plazos del sistema ya vigentes (retención nocturna, ver `docs/context/data-retention.md`) están en la sección 3.

## 3. Inventario de datos personales por entidad

Fuente: entidades JPA en `backend/src/main/java/com/red_escuela/backend/` y migraciones en `backend/src/main/resources/db/migration`. Los campos son los que figuran en la clase; no se revisó el contenido real de ninguna base.

| Entidad (tabla) | Datos personales | Quién los ve hoy | Retención |
|---|---|---|---|
| `Person` (`person`), identity | legajo, nombre, apellido, sexo, email, DNI y tipo de documento, domicilio (calle, ciudad, etc.), teléfono, fecha de nacimiento, nacionalidad, usuario, hash de contraseña, proveedor de login (LOCAL/Google) y `google_subject_id`, intentos fallidos y bloqueo, roles, avatar | La propia persona (`GET /me`); staff por `GET /students/{id}` (PRECEPTOR, ADMINISTRATIVO, DIRECTOR) y `GET /staff/{id}` (ADMINISTRATIVO, DIRECTOR) | Sin plazo: es el registro maestro y lo referencian notas, actas y certificados |
| `Student` (`student`), identity | institución, estado de alta/baja, motivo y detalle de baja, fecha de registro y quién registró, datos del responsable (`guardian_full_name`, `guardian_dni`, `guardian_relationship`, `guardian_phone`), `emergency_contact_phone` | Staff (idem `/students/{id}`) | Sin plazo |
| `StudentDocument` (`student_document`), enrollment | tipo de documento y `file_path` del archivo subido (DNI, certificados, etc.) | Staff; el alumno lista los suyos en `GET /me/documents` | Sin plazo |
| `StudentStatusChange` (`student_status_change`) | alumno, tipo de cambio (baja/reactivación), motivo, quién actuó, cuándo | Staff | Sin plazo |
| `ContactChangeLog` (`identity_contact_change_log`) | valor anterior y nuevo de email/teléfono, quién cambió, cuándo | Soporte (consulta SQL) | Sin plazo |
| `LegalAcceptance` (`legal_acceptance`) | persona, tipo (TERMS/PRIVACY), versión, fecha, `ip_hash` (hash, no la IP), resumen de user-agent | Staff: `GET /legal-acceptances` (ADMINISTRATIVO, DIRECTOR); la persona ve lo pendiente en `GET /me/legal-acceptances/pending` | Sin plazo (es la evidencia del consentimiento) |
| `LegalGuardianConfirmation` (`legal_guardian_confirmation`) | nombre y email del responsable de un menor, relación, hash del token, vencimiento y uso | Soporte | Sin plazo definido |
| `NewsletterPreference` (`newsletter_preference`) | persona, opt-in, fecha | La persona: `GET/PUT /me/newsletter-preference` | Mientras exista la persona |
| `NotificationPreference` (`notification_preference`) | canales y temas elegidos | La persona: `GET/PUT /me/notification-preferences` | Mientras exista la persona |
| `LoginAttempt` (`login_attempt`) | usuario intentado, IP (`ip_address`, en claro), resultado, fecha | Soporte | 12 meses, job `IdentityDataRetentionJob` 03:40 |
| `RefreshToken` (`refresh_token`) | hash del token, hash de user-agent, vencimiento | La persona: `GET /me/sessions` | Se borra al vencer (mismo job) |
| `PasswordResetToken`, `EmailVerificationToken` | hash del token, vencimiento | Nadie (interno) | Se borran al vencer (mismo job) |
| `Notification` (`notification`) y `notification_delivery` | mensaje (puede nombrar al alumno y su trámite), destinatario, estado de entrega | La persona: `GET /me/notifications` | 12 meses (`NotificationDataRetentionJob` 03:50); las sin leer con trámite pendiente se conservan hasta resolverse; recordatorios de cobranza 36 meses |
| `Attendance` (`attendance`) | asistencia por clase, motivo de justificación, `justification_attachment_url` (adjunto), quién justificó | La persona: `/me/section-enrollments/{id}/attendance`; docentes y staff | Sin plazo (historial académico) |
| `GradeItem` y auditoría (`GradeItemAudit`) | notas por examen y parcial, quién y cuándo | La persona: `/me/section-enrollments/{id}/grade-items` (solo publicadas) | Sin plazo (historial académico) |
| `CommissionClosure`, `CommissionClosureStudent`, `CommissionClosureAudit` | cierre de comisión con resultado por alumno (libro, folio, acta) y auditoría del cierre | Staff | Sin plazo (acta) |
| `ExamBoardRegistration`, `ExamBoardFinalResult` | inscripción a mesa y resultado final | Staff y docentes de la mesa | Sin plazo (acta de examen) |
| `CourseEnrollment`, `ProgramEnrollment`, `SubjectSectionEnrollment` y solicitudes (`ProgramEnrollmentApplication`) | inscripciones a carrera, materias y cursadas con su resultado final | La persona (`/me/course-enrollments/{id}`, `/me/program-enrollment-applications`) | Sin plazo |
| `GraduationRequest`, `GraduationCertificate`, `CertificateIssuance`, `RegularStudentCertificate`/`RegularCertificateRequest` | solicitud de egreso, número de registro, `legacy_title_number`, certificado con código, `pdf_sha256`, `pdf_path`, snapshot de los datos al emitir, revocación | La persona: `GET /me/regular-certificates` y su PDF; staff | Sin plazo (documento oficial) |
| Billing: facturas, pagos, reembolsos, notas de crédito, comprobantes de transferencia (`BankTransferReceiptRecord.file_path`), descuentos | importes, comprobantes subidos por el alumno, estado de cobro | La persona: `/me/payments`, `/me/refunds`, `/me/credit-notes`, `/me/student-discounts` | Sin plazo definido en el repo. Registros contables **[ABOGADO]** |
| `BroadcastRecipient` (`broadcast_recipient`) | destinatario de una comunicación masiva: dirección enmascarada, estado y motivo de exclusión | Dirección y Soporte | La dirección se anonimiza a los 12 meses de completado o cancelado el envío (`BroadcastRecipientDataRetentionJob`); estado y motivo se conservan; los totales del envío quedan congelados en `broadcast` |
| `Announcement` / `AnnouncementRecipient` | destinatarios y lecturas de comunicados | La persona: `/me/announcements` | Sin plazo |
| `audit_log` (`AuditLogEntry`) | quién (id y rol), qué acción, sobre qué entidad, valores antes/después, motivo | DIRECTOR: `GET /audit-log` | A los 12 meses `AuditLogArchiveJob` las mueve a `audit_log_archive` (sin perder filas; `GET /audit-log` solo lee `audit_log`). `audit_log_archive` se purga a los 24 meses con `AuditLogRetentionJob` (apagado por defecto, `AUDIT_RETENTION_ENABLED`). Resumen: 12 meses en `audit_log`, luego `audit_log_archive` hasta 24 meses, después se purga |
| `InstituteSettingChangeLog`, `FeatureChangeLog`, `PlatformProvisioningLog` (platform) | cambios de configuración y quién los hizo (id de persona) | Plataforma | Sin plazo |

Archivos subidos (avatar, documentos del alumno, adjuntos de justificación, comprobantes, PDF de certificados) viven en el almacenamiento de archivos del despliegue (`FileStorageService`); la fila de base solo guarda la ruta. Cualquier acceso o supresión debe contemplar el archivo además de la fila.

## 4. Pedido de ACCESO

La persona puede obtener gran parte de sus datos sola; el resto lo arma staff.

1. **Autoservicio** (sin intervención): `GET /me` (perfil), `/me/documents`, `/me/notifications`, `/me/sessions`, `/me/legal-acceptances/pending`, `/me/newsletter-preference`, `/me/notification-preferences`, `/me/regular-certificates`, `/me/payments`, `/me/refunds`, `/me/credit-notes`, `/me/course-enrollments/{id}`, asistencia y notas publicadas por inscripción.
2. **Vista del staff** para entregar un resumen verificado: `GET /students/{id}` o `GET /staff/{id}`; aceptaciones legales: `GET /legal-acceptances?personId=` (ADMINISTRATIVO, DIRECTOR); auditoría: `GET /audit-log` (solo DIRECTOR), que filtra por acción, tipo de entidad e id de entidad, no por actor: lo que la persona hizo como actor requiere consulta SQL por `actor_person_id`.
3. **Lo que no tiene endpoint** y requiere consulta SQL de soporte: `identity_contact_change_log`, `login_attempt` (IP), `student_status_change`, `legal_guardian_confirmation`, `notification_delivery`. Consulta tipo, siempre por `person_id` previamente verificado, p. ej. `select * from identity_contact_change_log where person_id = :id`. El resultado se entrega en el mismo canal verificado.
4. **No se entrega** lo que contenga datos de terceros (la identidad de otros alumnos en actas, quién justificó, textos de auditoría de otras personas): se recorta antes de responder.
5. No existe hoy una exportación única (ver F-1). Mientras tanto, la respuesta es un conjunto de capturas/consultas armado por el ADMINISTRATIVO.

## 5. Pedido de RECTIFICACIÓN

| Dato | Cómo se corrige hoy |
|---|---|
| Email, teléfono, sexo, DNI, tipo de documento, domicilio | La persona misma: `PUT /me` (requiere contraseña actual cuando cambia credenciales). Queda en `identity_contact_change_log`. Staff: `PUT /staff/{id}` para personal |
| Nombre, apellido, fecha de nacimiento de un alumno | Hoy el alumno no los edita en `PUT /me`. Lo corrige staff con la ficha del alumno. Cambios sobre un dato que figura en un certificado o acta ya emitidos: ver abajo |
| Responsable y contacto de emergencia | Staff, en la ficha del alumno |
| Preferencias (novedades, canales) | La persona: `PUT /me/newsletter-preference`, `PUT /me/notification-preferences` |
| Notas publicadas | **No es rectificación de datos personales**: aplica la regla de corrección de notas (el Director corrige hasta fin del cuatrimestre, con motivo y aviso). No se atiende por este circuito |

**Certificados y actas ya emitidos:** el documento firmado/sellado (`pdf_sha256`, snapshot) no se reescribe. Si el error es del dato del alumno, se corrige en `person` y, para el certificado, se **revoca** (`revoked_at`) y se emite uno nuevo por el circuito normal. Que el acta histórica conserve el dato original o se anote al margen es criterio del instituto **[ABOGADO]**.

## 6. Pedido de SUPRESIÓN

### 6.1 Principio

El sistema es el registro del historial académico del alumno. La persona puede pedir que se borren sus datos, pero hay datos que el instituto debe seguir conservando por su función (actas, notas, certificados, contabilidad) y por trazabilidad. La decisión final de qué se conserva y por cuánto tiempo, y su fundamento legal, es de C4 **[ABOGADO]**. Lo siguiente es la clasificación técnica propuesta.

### 6.2 Qué se puede borrar o dejar de tratar

| Dato | Acción | Cómo |
|---|---|---|
| Newsletter / novedades por correo | Dejar de tratar | `PUT /me/newsletter-preference` con opt-in en falso |
| Notificaciones, entregas, intentos de login, tokens | Ya se purgan solos | Jobs de `docs/context/data-retention.md` (12 meses; tokens al vencer). Una supresión puede adelantar el borrado con SQL de soporte, previa aprobación del Director |
| Sesiones activas | Cerrar | `DELETE /me/sessions` |
| Avatar | Borrar archivo y ruta | SQL/archivo de soporte (no hay endpoint de borrado, solo de subida: `POST /me/avatar`) |
| Documentos subidos (`student_document`) y adjuntos de justificación, comprobantes de pago | Se pueden borrar si el pedido lo exige y el trámite asociado está cerrado | Soporte: borrar fila y archivo. Los comprobantes de pago pueden ser respaldo contable **[ABOGADO]** |
| Contacto del responsable, contacto de emergencia, teléfono, domicilio | Se pueden vaciar si la persona ya no tiene vínculo activo | Soporte (verificar antes en el esquema qué columnas aceptan nulo) |
| Acceso al sistema | Dar de baja | `POST /students/{id}/withdraw` o `POST /staff/{id}/withdraw` (ADMINISTRATIVO, DIRECTOR). Es una baja lógica: no borra datos |

### 6.3 Qué NO se puede borrar y por qué

| Dato | Por qué se conserva |
|---|---|
| `person` / `student` (identidad básica: nombre, apellido, DNI, legajo) | Las notas, actas, certificados, inscripciones y facturas apuntan a la persona por clave foránea; borrarla rompe el historial académico |
| Notas, cursadas, resultados finales, asistencia | Historial académico del alumno |
| Actas de cierre de comisión, libro y folio, actas de mesa | Documento oficial del instituto |
| Certificados emitidos, número de registro, título histórico, solicitudes de egreso | Documento oficial verificable (el QR y el código deben seguir resolviendo) |
| `legal_acceptance` | Es la prueba de que la persona aceptó términos y política y de qué versión |
| Facturas, pagos, reembolsos y notas de crédito | Registro de cobros **[ABOGADO]** (plazo contable) |
| `audit_log`, `student_status_change`, `identity_contact_change_log`, logs de plataforma | Trazabilidad de quién cambió qué; solo se purgan por política de retención, no a pedido (excepto que el abogado indique otra cosa) |

### 6.4 Anonimización

Hoy **no existe** una función de anonimización en el código (se buscó en `backend/src/main/java`). Una anonimización posible, a diseñar en el paquete F-3, sería sobre `person` y `student`: reemplazar nombre, apellido, email, DNI, teléfono, domicilio, nacimiento, nacionalidad, usuario y datos del responsable por valores neutros y no reversibles, desactivar el acceso, borrar archivos y desvincular el login de Google. Se conservaría el `id` (para que notas, actas y certificados sigan íntegros) y el legajo. Efecto colateral a decidir **[ABOGADO]**: los certificados y actas ya emitidos llevan el nombre de la persona en su snapshot y en el PDF; anonimizar la fila no los modifica, y los certificados ya no podrían asociarse a una persona identificable. Que esto cumpla el pedido de supresión, o que haya que conservar la identidad mientras dure la obligación de conservar el acta, es una decisión legal.

### 6.5 Flujo de decisión

1. ADMINISTRATIVO registra el pedido y verifica identidad (sección 1).
2. Verifica si hay trámites abiertos: deuda, inscripción vigente, solicitud de egreso en curso. Si los hay, se informa y se pospone lo que dependa de ellos.
3. DIRECTOR decide por categoría (6.2 / 6.3). Toda negativa parcial se informa con el motivo, citando la categoría de 6.3 en lenguaje llano.
4. Soporte ejecuta las acciones aprobadas y deja constancia en la planilla de pedidos (no hay endpoint de borrado que escriba en `audit_log`; ver F-2).
5. Se responde con la plantilla de la sección 7.

## 7. Plantillas de respuesta

Los datos entre corchetes son del instituto; el texto legal final lo define C4 **[ABOGADO]**.

**Acuse de recibo**

> Hola [nombre]. Recibimos tu pedido de [acceso / rectificación / supresión] de datos personales el [fecha]. Para atenderlo necesitamos confirmar tu identidad: [pedir DNI presencial o confirmar desde el correo registrado]. Te vamos a responder antes del [fecha límite]. [Responsable de la base de datos y contacto: completar con C4].

**Respuesta de acceso**

> Hola [nombre]. Te adjuntamos los datos personales que tenemos registrados sobre vos: [listado/adjuntos]. Quedaron fuera los datos que corresponden a otras personas. Si algo es incorrecto, podés pedir su corrección respondiendo este mensaje o desde tu perfil.

**Respuesta de rectificación**

> Hola [nombre]. Corregimos [dato] el [fecha]. [Si hay certificado o acta emitidos: explicar que el documento anterior se revocó y se emitió uno nuevo / que el acta conserva su contenido original].

**Respuesta de supresión (total o parcial)**

> Hola [nombre]. Eliminamos o dejamos de usar: [lista de 6.2 ejecutadas]. Conservamos [lista de 6.3 que corresponda] porque forman parte de tu historial académico y de documentos oficiales del instituto que debemos mantener ([motivo en lenguaje llano, texto a validar con el abogado]). [Si hubo anonimización: indicar alcance]. Si no estás de acuerdo, podés [vía de reclamo a definir con C4].

## 8. Cambios de código necesarios (paquetes futuros, no incluidos acá)

| Paquete | Qué entrega | Tamaño estimado | Riesgo |
|---|---|---|---|
| F-1 Exportación de mis datos | Endpoint `GET /me/data-export` (ZIP o JSON) que reúna perfil, aceptaciones legales, inscripciones, notas publicadas, asistencia, certificados, pagos y notificaciones; y su par para staff | 2 | S |
| F-2 Registro de pedidos de derechos | Tabla y pantalla mínima (tipo, estado, fechas, decisión) con registro en `audit_log` de cada acción de soporte | 1-2 | S |
| F-3 Anonimización de persona | Caso de uso para ADMINISTRATIVO/DIRECTOR con precondiciones (sin deuda ni trámites abiertos), borrado de archivos y rastro de auditoría; depende del criterio legal de 6.4 | 2 | D |
| F-4 Borrado de archivos propios | Endpoints para quitar avatar y documentos subidos | 1 | – |
| F-5 Retención de datos que hoy no la tienen | Plazos para `legal_guardian_confirmation` vencidas, `identity_contact_change_log`, comprobantes; encender y definir `AuditLogRetentionJob` | 1 | S |
| F-6 Rectificación de nombre y fecha de nacimiento por el alumno | Flujo de pedido con aprobación de staff (hoy `PUT /me` no los incluye) | 1 | – |

## 9. Pendiente de abogado (resumen, C4)

- Responsable de la base de datos, domicilio y canal oficial para ejercer derechos.
- Plazos legales de respuesta y de conservación (sección 2, facturación, actas, auditoría).
- Alcance legal de «supresión» frente a historial académico y si la anonimización la cumple (6.4).
- Tratamiento de menores y quién ejerce sus derechos (1).
- Texto final de la plantilla de respuesta y vía de reclamo (7).
