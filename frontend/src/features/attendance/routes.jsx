import { DocenteTomarAsistencia } from '@/features/attendance/pages/DocenteTomarAsistencia';
import { SolicitudesAsistencia } from '@/features/attendance/pages/SolicitudesAsistencia';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['docente'], path: 'tomar-asistencia', element: <DocenteTomarAsistencia /> },
    { roles: ['preceptor', 'administrativo'], path: 'solicitudes-de-asistencia', element: <SolicitudesAsistencia /> },
  ],
};
