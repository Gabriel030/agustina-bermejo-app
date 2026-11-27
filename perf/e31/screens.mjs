const adminScreens = [
  'inicio', 'alumnos', 'alumnos/nuevo', 'solicitudes-de-asistencia', 'solicitudes-de-ingreso', 'inscripciones/listas-de-espera',
  'listas-de-espera', 'periodos', 'periodos/nuevo', 'aprobaciones', 'cierres-de-comision', 'egresos-por-verificar', 'feriados',
  'gestion-academica', 'gestion-academica/nueva', 'gestion-academica/carreras/nueva', 'gestion-academica/cursos/nueva',
  'mesas-de-examen', 'facturacion', 'precios', 'descuentos', 'descuentos/nuevo', 'descuentos/promociones/nueva',
  'corridas-de-facturacion', 'emision-mensual', 'recordatorios-de-pago', 'recordatorios-de-pago/mails-simulados', 'medios-de-pago',
  'financiero', 'constancias', 'constancias-por-excepcion', 'egresos', 'plantilla-de-certificados', 'firmantes', 'personal',
  'comunicaciones', 'comunicaciones/nuevo',
];

const directorScreens = [
  'inicio', 'alumnos', 'alumnos/nuevo', 'inscripciones/listas-de-espera', 'listas-de-espera', 'periodos', 'periodos/nuevo',
  'oferta-academica', 'oferta-academica/calendario', 'gestion-academica', 'aprobaciones', 'aprobaciones/configuracion-evaluacion',
  'aprobaciones/egresos', 'aprobaciones/excepciones', 'aprobaciones/excepciones-por-reclamo', 'aprobaciones/facturacion',
  'aprobaciones/oferta-academica', 'cierres-de-comision', 'mesas-de-examen', 'esquemas-evaluacion', 'financiero',
  'financiero/anios-lectivos', 'facturacion', 'precios', 'descuentos', 'descuentos/nuevo', 'descuentos/promociones/nueva',
  'corridas-de-facturacion', 'emision-mensual', 'recordatorios-de-pago', 'recordatorios-de-pago/mails-simulados', 'medios-de-pago',
  'terminos-del-pago-anual', 'constancias', 'constancias-por-excepcion', 'egresos', 'plantilla-de-certificados', 'firmantes',
  'personal', 'comunicaciones', 'comunicaciones/nuevo', 'importacion-inicial', 'importacion-inicial/nueva', 'ajustes-del-instituto',
  'auditoria', 'backups', 'permisos',
];

export const roles = {
  Director: {
    key: 'director',
    login: process.env.E31_DIRECTOR ?? 'director.demo',
    prefix: '/director',
    screens: directorScreens,
    details: [
      { name: 'Detalle de alumno', route: 'alumnos/:id', from: 'alumnos', click: 'main button:has-text("@")' },
      { name: 'Detalle de carrera', route: 'oferta-academica/carreras/:programaId', from: 'oferta-academica', click: 'main button:has-text("Licenciatura")' },
    ],
    actions: [
      { name: 'Alumnos: abrir primer alumno', from: 'alumnos', click: 'main button:has-text("@")' },
      { name: 'Oferta académica: abrir primera carrera', from: 'oferta-academica', click: 'main button:has-text("Licenciatura")' },
      { name: 'Aprobaciones: abrir bandeja de facturación', from: 'aprobaciones', click: 'a[href$="/aprobaciones/facturacion"] >> nth=0' },
    ],
  },
  Administrativo: {
    key: 'administrativo',
    login: process.env.E31_ADMIN ?? 'administrativo.demo',
    prefix: '/administrativo',
    screens: adminScreens,
    details: [],
    actions: [
      { name: 'Alumnos: abrir primer alumno', from: 'alumnos', click: 'main button:has-text("@")' },
      { name: 'Facturación: pestaña Estado de cuenta', from: 'facturacion', click: 'main button:has-text("Estado de cuenta")' },
    ],
  },
  Preceptor: {
    key: 'preceptor',
    login: process.env.E31_PRECEPTOR ?? 'preceptor.demo',
    prefix: '/preceptor',
    screens: [
      'inicio', 'alumnos', 'alumnos/nuevo', 'solicitudes-de-asistencia', 'solicitudes-de-ingreso', 'inscripciones/listas-de-espera',
      'listas-de-espera', 'periodos', 'periodos/nuevo', 'oferta-academica', 'oferta-academica/calendario',
      'oferta-academica/nuevo-ciclo', 'mesas-de-examen',
    ],
    details: [],
    actions: [
      { name: 'Alumnos: abrir primer alumno', from: 'alumnos', click: 'main button:has-text("@")' },
    ],
  },
  Docente: {
    key: 'docente',
    login: process.env.E31_TEACHER ?? 'perft12',
    prefix: '/docente',
    screens: ['inicio', 'mis-comisiones', 'tomar-asistencia', 'mesas-de-examen'],
    details: [
      { name: 'Detalle de comisión', route: 'mis-comisiones/:sectionId', from: 'mis-comisiones', click: 'a[href*="/mis-comisiones/"]' },
    ],
    actions: [
      { name: 'Mis comisiones: abrir primera comisión', from: 'mis-comisiones', click: 'a[href*="/mis-comisiones/"]' },
    ],
  },
  Alumno: {
    key: 'alumno',
    login: process.env.E31_STUDENT ?? 'alumno.demo',
    prefix: '/alumno',
    screens: ['inicio', 'calendario', 'mis-carreras', 'mis-cursos', 'mis-certificados', 'opciones', 'pagos', 'pagos/estado', 'pagos/notas-de-credito'],
    details: [
      { name: 'Detalle de curso', route: 'mis-cursos/:id', from: 'mis-cursos', click: 'a[href*="/alumno/mis-cursos/"]' },
    ],
    actions: [
      { name: 'Mis carreras: pestaña Notas', from: 'mis-carreras', click: 'main button:has-text("Notas")' },
    ],
  },
  Plataforma: {
    key: 'platform_admin',
    login: process.env.E31_PLATFORM ?? null,
    missingLoginReason: 'sin usuario de plataforma en docs/context/test-users.md ni en el seed perf; definir E31_PLATFORM para medirlo',
    prefix: '/platform_admin',
    screens: ['inicio', 'modulos', 'primer-director', 'auditoria', 'backups'],
    details: [],
    actions: [],
  },
};

export const excludedRoles = {
  subdirector: 'sin usuario demo; hereda las rutas de administrativo (ya medidas) y suma solo permisos',
};

export const parameterizedExclusion = 'solo se mide el detalle muestreado en `details`; el resto necesita datos específicos del seed';
