import { DocenteTomarAsistencia } from '@/features/attendance/pages/DocenteTomarAsistencia';
import { AdministrativoSolicitudesAsistencia } from '@/features/attendance/pages/AdministrativoSolicitudesAsistencia';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['docente'], path: 'tomar-asistencia', element: <DocenteTomarAsistencia /> },
    { roles: ['preceptor', 'administrativo'], path: 'solicitudes-de-asistencia', element: <AdministrativoSolicitudesAsistencia /> },
  ],
};
