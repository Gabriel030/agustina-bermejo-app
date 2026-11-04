import { AlumnoInicio } from '@/features/workspace/pages/AlumnoInicio';
import { DocenteMisComisiones } from '@/features/workspace/pages/DocenteMisComisiones';
import { StaffSectionRoster } from '@/features/workspace/pages/StaffSectionRoster';
import { DirectorAprobaciones } from '@/features/workspace/pages/DirectorAprobaciones';

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['alumno'], path: 'inicio', element: <AlumnoInicio /> },
    { roles: ['docente'], path: 'mis-comisiones', element: <DocenteMisComisiones /> },
    { roles: ['preceptor'], path: 'oferta-academica/ciclos/:cycleId/comisiones/:sectionId/inscriptos', element: <StaffSectionRoster /> },
    { roles: ['director'], path: 'aprobaciones', element: <DirectorAprobaciones /> },
  ],
};
