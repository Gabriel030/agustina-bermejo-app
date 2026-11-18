import { lazyPage } from '@/routes/lazyPage';
const AlumnoInicio = lazyPage(() => import('@/features/workspace/pages/AlumnoInicio'), 'AlumnoInicio');
const DocenteMisComisiones = lazyPage(() => import('@/features/workspace/pages/DocenteMisComisiones'), 'DocenteMisComisiones');
const StaffSectionRoster = lazyPage(() => import('@/features/workspace/pages/StaffSectionRoster'), 'StaffSectionRoster');
const DirectorAprobaciones = lazyPage(() => import('@/features/workspace/pages/DirectorAprobaciones'), 'DirectorAprobaciones');
const DirectorInicio = lazyPage(() => import('@/features/workspace/pages/StaffHome'), 'DirectorInicio');
const AdministrativoInicio = lazyPage(() => import('@/features/workspace/pages/StaffHome'), 'AdministrativoInicio');

export const routes = {
  public: [],
  shared: [],
  role: [
    { roles: ['alumno'], path: 'inicio', element: <AlumnoInicio /> },
    { roles: ['director'], path: 'inicio', element: <DirectorInicio /> },
    { roles: ['administrativo'], path: 'inicio', element: <AdministrativoInicio /> },
    { roles: ['docente'], path: 'mis-comisiones', element: <DocenteMisComisiones /> },
    { roles: ['preceptor', 'director'], path: 'oferta-academica/ciclos/:cycleId/comisiones/:sectionId/inscriptos', element: <StaffSectionRoster /> },
    { roles: ['director'], path: 'aprobaciones', element: <DirectorAprobaciones /> },
  ],
};
