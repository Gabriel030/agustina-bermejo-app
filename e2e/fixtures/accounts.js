export const PASSWORD = 'Test1234';

export const ROLES = {
  alumno: { username: 'alumno.demo', home: '/alumno/inicio', foreignRoute: '/director/aprobaciones' },
  docente: { username: 'docente.demo', home: '/docente/inicio', foreignRoute: '/alumno/pagos' },
  preceptor: { username: 'preceptor.demo', home: '/preceptor/inicio', foreignRoute: '/director/aprobaciones' },
  administrativo: { username: 'administrativo.demo', home: '/administrativo/inicio', foreignRoute: '/alumno/pagos' },
  director: { username: 'director.demo', home: '/director/inicio', foreignRoute: '/alumno/pagos' },
  enrollmentStudent: { username: 'alumno.inscripcion.demo', home: '/alumno/inicio', foreignRoute: '/director/aprobaciones' },
  seatCompetitorStudent: { username: 'alumno.cupo.demo', home: '/alumno/inicio', foreignRoute: '/director/aprobaciones' },
  courseStudent: { username: 'alumno.cursos.demo', home: '/alumno/inicio', foreignRoute: '/director/aprobaciones' },
  gradesStudent: { username: 'alumno.notas.demo', home: '/alumno/inicio', foreignRoute: '/director/aprobaciones' },
};

export const BACKEND_URL = `http://localhost:${process.env.E2E_BACKEND_PORT ?? '8090'}`;
