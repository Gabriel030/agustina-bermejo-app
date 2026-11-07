/**
 * <b>Qué es</b>: resolución de audiencias: carga la foto de la sección y aplica cada segmento (asistencia, notas, estado de trabajos prácticos) para decidir qué alumnos reciben el aviso.
 *
 * <p><b>Expone</b>: {@code AudienceSegmentResolver}, que usan los especialistas y los casos de uso de audiencia.
 *
 * <p><b>No hacer</b>: leer repositorios de avisos; conocer la presentación HTTP.
 *
 * <p><b>Ejemplo</b>: {@code AttendanceResolver}.
 */
package com.red_escuela.backend.announcement.specialist.audience;
