import { AlumnoInicio } from '@/features/workspace/pages/AlumnoInicio';
import { DocenteMisComisiones } from '@/features/workspace/pages/DocenteMisComisiones';
import { DirectorAprobaciones } from '@/features/workspace/pages/DirectorAprobaciones';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['alumno'], path: 'inicio', element: <AlumnoInicio /> },
    { roles: ['docente'], path: 'mis-comisiones', element: <DocenteMisComisiones /> },
    { roles: ['director'], path: 'aprobaciones', element: <DirectorAprobaciones /> },
  ],
};
