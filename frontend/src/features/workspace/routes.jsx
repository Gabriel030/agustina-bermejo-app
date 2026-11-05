import { lazyPage } from '@/routes/lazyPage';
const AlumnoInicio = lazyPage(() => import('@/features/workspace/pages/AlumnoInicio'), 'AlumnoInicio');
const DocenteMisComisiones = lazyPage(() => import('@/features/workspace/pages/DocenteMisComisiones'), 'DocenteMisComisiones');
const StaffSectionRoster = lazyPage(() => import('@/features/workspace/pages/StaffSectionRoster'), 'StaffSectionRoster');
const DirectorAprobaciones = lazyPage(() => import('@/features/workspace/pages/DirectorAprobaciones'), 'DirectorAprobaciones');

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
