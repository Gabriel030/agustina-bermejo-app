export const PASSWORD = 'Test1234';

export const ROLES = {
  alumno: { username: 'alumno.demo', home: '/alumno/inicio', foreignRoute: '/director/aprobaciones' },
  docente: { username: 'docente.demo', home: '/docente/inicio', foreignRoute: '/alumno/pagos' },
  preceptor: { username: 'preceptor.demo', home: '/preceptor/inicio', foreignRoute: '/director/aprobaciones' },
  administrativo: { username: 'administrativo.demo', home: '/administrativo/inicio', foreignRoute: '/alumno/pagos' },
  director: { username: 'director.demo', home: '/director/inicio', foreignRoute: '/alumno/pagos' },
};

export const BACKEND_URL = `http://localhost:${process.env.E2E_BACKEND_PORT ?? '8090'}`;
