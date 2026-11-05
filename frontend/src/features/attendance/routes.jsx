import { lazyPage } from '@/routes/lazyPage';
const DocenteTomarAsistencia = lazyPage(() => import('@/features/attendance/pages/DocenteTomarAsistencia'), 'DocenteTomarAsistencia');
const SolicitudesAsistencia = lazyPage(() => import('@/features/attendance/pages/SolicitudesAsistencia'), 'SolicitudesAsistencia');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['docente'], path: 'tomar-asistencia', element: <DocenteTomarAsistencia /> },
    { roles: ['preceptor', 'administrativo'], path: 'solicitudes-de-asistencia', element: <SolicitudesAsistencia /> },
  ],
};
