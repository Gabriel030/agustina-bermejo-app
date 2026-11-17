export const roles = {
  Director: {
    login: process.env.E31_DIRECTOR ?? 'director.demo',
    prefix: '/director',
    screens: [
      'inicio', 'alumnos', 'listas-de-espera', 'periodos', 'oferta-academica', 'oferta-academica/calendario',
      'gestion-academica', 'aprobaciones', 'cierres-de-comision', 'mesas-de-examen', 'esquemas-evaluacion',
      'financiero', 'financiero/anios-lectivos', 'facturacion', 'precios', 'descuentos', 'corridas-de-facturacion',
      'constancias', 'personal', 'importacion-inicial', 'ajustes-del-instituto',
    ],
    actions: [
      { name: 'Alumnos: abrir primer alumno', from: 'alumnos', click: 'main button:has-text("@")' },
      { name: 'Oferta académica: abrir primera carrera', from: 'oferta-academica', click: 'main button:has-text("Licenciatura")' },
      { name: 'Aprobaciones: abrir bandeja de facturación', from: 'aprobaciones', click: 'a[href$="/aprobaciones/facturacion"] >> nth=0' },
    ],
  },
  Administrativo: {
    login: process.env.E31_ADMIN ?? 'administrativo.demo',
    prefix: '/administrativo',
    screens: [
      'inicio', 'alumnos', 'solicitudes-de-asistencia', 'solicitudes-de-ingreso', 'listas-de-espera', 'periodos',
      'aprobaciones', 'cierres-de-comision', 'egresos-por-verificar', 'feriados', 'gestion-academica',
      'mesas-de-examen', 'facturacion', 'precios', 'descuentos', 'corridas-de-facturacion', 'constancias', 'personal',
    ],
    actions: [
      { name: 'Alumnos: abrir primer alumno', from: 'alumnos', click: 'main button:has-text("@")' },
      { name: 'Facturación: pestaña Estado de cuenta', from: 'facturacion', click: 'main button:has-text("Estado de cuenta")' },
    ],
  },
  Docente: {
    login: process.env.E31_TEACHER ?? 'perft12',
    prefix: '/docente',
    screens: ['inicio', 'mis-comisiones', 'tomar-asistencia', 'mesas-de-examen'],
    actions: [
      { name: 'Mis comisiones: abrir primera comisión', from: 'mis-comisiones', click: 'a[href*="/mis-comisiones/"]' },
    ],
  },
  Alumno: {
    login: process.env.E31_STUDENT ?? 'alumno.demo',
    prefix: '/alumno',
    screens: ['inicio', 'mis-carreras', 'mis-cursos', 'pagos', 'pagos/estado', 'pagos/notas-de-credito'],
    actions: [
      { name: 'Mis carreras: pestaña Notas', from: 'mis-carreras', click: 'main button:has-text("Notas")' },
    ],
  },
};
