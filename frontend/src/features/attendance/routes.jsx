import { DocenteTomarAsistencia } from '@/pages/DocenteTomarAsistencia';
import { AdministrativoSolicitudesAsistencia } from '@/pages/AdministrativoSolicitudesAsistencia';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['docente'], path: 'tomar-asistencia', element: <DocenteTomarAsistencia /> },
    { roles: ['preceptor', 'administrativo'], path: 'solicitudes-de-asistencia', element: <AdministrativoSolicitudesAsistencia /> },
  ],
};
