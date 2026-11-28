---
title: Revisar el estado de los backups
section: institution
roles: [director, platform_admin]
status: borrador
screens: ["backups"]
last_review: 2026-11-28
---

## Para qué sirve

Para saber si las copias de seguridad de la base y de los archivos del instituto se están haciendo bien, y cuántos datos se perderían si hoy se cayera el servidor.

Esta pantalla solo muestra el estado. No hace backups ni restaura nada: restaurar lo hace la persona técnica en el servidor.

## Quién puede

Director y administrador de la plataforma. Se entra desde el menú Institución (Director) o desde «Backups» (plataforma).

## Pasos

1. Entrá a «Backups». Arriba ves un cartel de estado y la hora a la que se generó («Estado al…»).
2. Leé el cartel: «Todo en orden», «Atención», «Sin noticias» o «Sin configurar».
3. Mirá los tres datos clave: «Último backup completo», «Pérdida posible hoy» (contra el objetivo) y «Fallos seguidos».
4. En la pestaña «Detalle» ves «Último backup por nivel»: fecha, tamaño, verificación y cuántas copias se conservan. También ves la última prueba de restauración y las alertas activas.
5. En «Qué se respalda» ves qué entra y qué no entra en el backup, y cuántas copias se conservan de cada nivel.
6. En «Cómo restaurar» ves qué pasa en cada caso, quién lo hace y a quién avisar.
7. Si el cartel pide atención, avisá a soporte técnico con la fecha y lo que ves en pantalla.

## Reglas

- Los niveles son: «Del día», «Diaria», «Semanal», «Trimestral» y «Previa a actualización».
- La verificación de cada copia puede ser «Verificada», «Dañada» o «Sin verificar».
- Entran en el backup todas las tablas de la base (alumnos, cursos, materias, notas, asistencia, pagos e historial), los certificados emitidos y los archivos subidos.
- No entran la configuración del servidor ni las claves, que se guardan aparte.
- Hay alertas por backup fallido, backup viejo, copia dañada, prueba de restauración fallida, poco espacio en disco, backups sin cifrar y falta de archivos para respaldar.
- La pantalla recuerda hacer una prueba completa de restauración si pasaron más de 14 días desde la última.
- Los datos técnicos (ruta del disco, nombres de archivo y comandos de restauración) se muestran solo al administrador de la plataforma.

## Qué pasa después

- Consultar esta pantalla no cambia nada.
- El estado se actualiza con el que informa el servidor. Si no se actualiza hace tiempo, el cartel pasa a «Sin noticias».

## Errores comunes

- «Todavía no hay backups»: el servicio de backups no está activo.
- Cartel de «Atención»: hace demasiado que no hay un backup completo. Avisá a soporte técnico.
- Querer restaurar desde esta pantalla: no se puede. «Si no estás seguro, no restaures: avisá a soporte técnico.»

**Pendiente de verificar:**

- El título original de esta tarea decía «Hacer y restaurar backups». El código no tiene ningún botón para hacer ni restaurar: se renombró a «Revisar el estado de los backups». Gabriel debe confirmar el cambio y su nombre en el inventario.
- Cada cuánto se hacen los backups y el objetivo de pérdida (RPO) en horas: son datos que informa el servidor y no están fijados en esta guía.
