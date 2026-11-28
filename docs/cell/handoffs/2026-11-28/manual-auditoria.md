# Auditoría del manual de usuario (28-nov-2026)

## (a) Qué es "el manual empezado"
- Principal: «Agustina Bermejo — Documentación funcional» https://claude.ai/artifact/XqmE2kmZmsb4CQjZBJt1dK (Docs, fecha 2-oct, última edición 3-oct, rev 20). ~61k caracteres, 6 procesos, 64 pantallas, 61 imágenes (capturas reales), 4 tablas, casos de uso por proceso, usuarios de prueba con contraseñas, Anexo A (qué ve cada perfil), B (estado de cada pantalla), C (pantallas diseñadas que no están).
  Estructura: por PROCESO (Acceso, Captación, Organización académica, Cursada y evaluación, Cobranza, Excepciones y egreso), no por rol. Cada pantalla: Rol / Para qué sirve / Qué se puede hacer / Reglas / Qué pasa después / Pendiente.
- Compañero: «Recorrido funcional ISAB» https://claude.ai/artifact/LNYuyXvLTBLUUmju8Ldu3s (web, 3-oct, 55 pantallas, filtro por rol, navegable con flechas, capturas reales, 56 archivos).
- Complementarios (no son manual): «Reglas de negocio» (Docs, 3-oct, por flujo y por rol, con "Pendiente e inconsistencias"), «Guía de importación inicial» (borrador de spec 13-nov), «Roles y permisos» y «Flujo de permisos» (mockups 21-nov de la grilla de permisos), «Gestión Académica — Resumen» (3-oct; solo por título, no lo leí).
- No hay otro manual en las ~220 artifacts listadas (revisé títulos de todas las páginas). Tampoco hay docs/manual en el repo.

## (b) Vigencia por sección (contra origin/develop del 28-nov; validado por menús roleMenu.js, rutas, openapi.json 599 paths, 331 commits desde 3-oct, test-users.md; NO ejecuté la app)
| Sección | Estado | Qué cambió |
| Qué es la app / cómo leer | DESACTUALIZADA | Hay 7 roles (suma Subdirector y Admin de plataforma). |
| Roles y menús | DESACTUALIZADA | Ya no hay menú lateral: barra superior con grupos (Personas, Inscripciones, Académico, Facturación, Institución). Docente suma Mesas; Alumno suma Calendario, Mis certificados, Reuniones informativas. |
| Usuarios de prueba | DESACTUALIZADA | test-users.md (31-oct) tiene cuentas nuevas (egreso, cursos); además no va en un manual de usuarios. Contradicción login DNI/email entre manual y recorrido. |
| P1 Acceso y cuenta | VIGENTE (a verificar) | Falta preferencia de rol de entrada; email/Google, revisar. |
| P2 Captación | DESACTUALIZADA | Cohortes con ventana de admisión; lista de espera con posición; Solicitudes de ingreso ya tiene pantalla (decía «sin pantalla»); alta por personal a lista de espera; invitaciones por oleadas. |
| P3 Organización | DESACTUALIZADA fuerte | Cohorte con responsable (preceptor) obligatorio y checklist de apertura; plan de estudios versionado con estados/vencimiento; preceptor obligatorio al crear curso y al aprobar ciclo; asignación de preceptores por comisión; aprobación del ciclo en Aprobaciones (estaba como «sin pantalla»); «Banco de materias» ahora «Gestión académica»; feriados. |
| P4 Cursada y evaluación | DESACTUALIZADA fuerte | Fin de término con 14 días de gracia, cierre automático de matrículas y escalada a Dirección; Cierres de comisión; mesas se crean, tienen ventanas, inscripción, acta imprimible (decía «no se crean»); solicitudes de asistencia con historial «Resueltas» y «Revertida»; alcance del preceptor por comisión/cohorte. |
| P5 Cobranza | DESACTUALIZADA fuerte | Facturación v2: emisión mensual, precios, descuentos, recordatorios de pago (mora), medios de pago, términos del pago anual, corridas, reembolsos y notas de crédito. Pantallas del alumno (5.1–5.5) probablemente vigentes salvo diseño. |
| P6 Excepciones y egreso | DESACTUALIZADA | Egresos por verificar, Egresos, plantilla de certificados, firmantes, constancias (regular, examen, materias aprobadas) con verificación pública, constancia por excepción de deuda; «sin pantalla» ya no aplica. |
| Anexo A (qué ve cada perfil) | DESACTUALIZADA | Menús completos cambiaron. |
| Anexo B (estado de pantallas) | OBSOLETA | Conteos y pendientes ya no valen (mesas, constancias, ciclo, Períodos). |
| Anexo C (diseñadas, no existen) | OBSOLETA en su mayoría | Aprobación de ciclo, Mis cursos, Opciones resueltas o rediseñadas. |
| Imágenes (61 capturas) | OBSOLETA | Anteriores al rediseño de barra superior (Dirección B). |
| FALTA | FALTA | Importación inicial, Comunicaciones/avisos con acuse, Invitaciones, Auditoría, Backups, Ajustes del instituto, Roles y permisos (grilla), Admin de plataforma (módulos, primer director), Reuniones informativas, Calendario y Mis certificados del alumno, Lista de espera, Subdirector. |

## (c) Comparación con la estructura propuesta (por tarea/rol)
- Primer día: reutiliza P1 (acceso, perfil, recuperar clave); reescribir menús y barra superior; falta primer director/plataforma.
- Armar el año: base en P3 pero reescribir (cohorte, plan versionado, preceptores obligatorios, checklist); falta calendario anual y orden de pasos.
- Cada mes/cada año: casi nada existe; salvar del manual solo textos de asistencia y evaluación; falta emisión mensual, cierre de término/gracia, apertura de cohorte, importación.
- Alumnos: reutilizar P2 (padrón, legajo, alta) y P4 (asistencia, avisos); reescribir mesas, cierre, egreso/constancias.
- Cobranza y mora: P5 solo cubre lado alumno y comprobantes; reescribir entero.
- Permisos y roles: falta casi todo; hoy solo mockups y la sección «Por rol» de Reglas de negocio.
- Buscador y filtro por rol: el Recorrido ya tiene filtro por rol; el Docs no tiene buscador. Imágenes al final: el actual las intercala.
Reutilización estimada: ~30-40% del texto (bloques «Para qué sirve / Reglas»), 0% de capturas.

## (d) Recomendación y preguntas
Recomendación: épica nueva con la estructura por tarea, usando el manual viejo como cantera de textos y las Reglas de negocio como fuente de verdad; no seguir editando el doc actual.
Preguntas: ver el reporte entregado.
