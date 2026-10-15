import { AlumnoLayout } from '@/layouts/AlumnoLayout';
import { DocenteLayout } from '@/layouts/DocenteLayout';
import { PreceptorLayout } from '@/layouts/PreceptorLayout';
import { AdministrativoLayout } from '@/layouts/AdministrativoLayout';
import { DirectorLayout } from '@/layouts/DirectorLayout';

export const ROLE_CONFIG = {
  alumno: {
    Layout: AlumnoLayout,
    pages: ['inicio', 'mis-cursos', 'mis-carreras', 'pagos', 'opciones'],
  },
  docente: {
    Layout: DocenteLayout,
    pages: ['inicio', 'mis-comisiones', 'tomar-asistencia'],
  },
  preceptor: {
    Layout: PreceptorLayout,
    pages: ['inicio', 'alumnos', 'oferta-academica', 'mesas-de-examen', 'solicitudes-de-asistencia', 'periodos'],
  },
  administrativo: {
    Layout: AdministrativoLayout,
    pages: ['inicio', 'alumnos', 'personal', 'facturacion', 'financiero', 'constancias', 'solicitudes-de-asistencia', 'gestion-academica'],
  },
  director: {
    Layout: DirectorLayout,
    pages: ['inicio', 'alumnos', 'personal', 'aprobaciones', 'gestion-academica', 'esquemas-evaluacion', 'financiero'],
  },
};
