import { AlumnoInicio } from '@/pages/AlumnoInicio';
import { DocenteMisComisiones } from '@/pages/DocenteMisComisiones';
import { DirectorAprobaciones } from '@/pages/DirectorAprobaciones';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['alumno'], path: 'inicio', element: <AlumnoInicio /> },
    { roles: ['docente'], path: 'mis-comisiones', element: <DocenteMisComisiones /> },
    { roles: ['director'], path: 'aprobaciones', element: <DirectorAprobaciones /> },
  ],
};
