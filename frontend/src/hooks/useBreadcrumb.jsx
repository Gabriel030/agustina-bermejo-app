import { useLocation, useParams, useSearchParams } from 'react-router-dom';
import { useMiInscripcion } from '@/hooks/useAlumno';
import { useConfiguracionComision } from '@/hooks/useDocente';
import { useConfiguracionComisionStaff } from '@/hooks/useDirector';
import { useLegajoAlumno } from '@/features/gestion-personas/hooks/useLegajoAlumno';
import { Skeleton } from '@/components/Skeleton';

const ETIQUETA_TAB = {
  'plan-de-estudio': 'Plan de estudio',
  notas: 'Notas',
  'mesas-de-examen': 'Mesas de examen',
  asistencia: 'Asistencia',
};

const ETIQUETA_TAB_PADRON = {
  activos: 'Activos',
  prospectos: 'Prospectos',
  bajas: 'Bajas',
};

// Mapea la ruta actual a los items del breadcrumb (Épica 2 Fase 2). Vive acá
// (no en cada página) porque RoleShell es el único lugar que lo renderiza —
// ver comentario en RoleShell.jsx. Devuelve [] para cualquier ruta sin un
// patrón conocido (Inicio, Placeholder, etc.): Breadcrumb no renderiza nada
// en ese caso, no hace falta un breadcrumb para una pantalla de un solo nivel.
export function useBreadcrumb() {
  const location = useLocation();
  const params = useParams();
  const [searchParams] = useSearchParams();

  // Detalle de materia: única puerta de entrada conceptual es la pestaña Materias
  // (decisión de UX) — el breadcrumb siempre es Mis carreras > Materias > <materia>,
  // sin importar si técnicamente se llegó por otro link. Reusa el cache de
  // useMisInscripciones (mismo hook que ya consume AlumnoMateriaDetalle), sin fetch duplicado.
  const enDetalleMateria = /^\/alumno\/mis-carreras\/materias\/[^/]+$/.test(location.pathname);
  // enabled: enDetalleMateria — sin esto, este hook (que corre en RoleShell para
  // CUALQUIER rol) le pega a /me/section-enrollments (endpoint solo-Alumno) incluso
  // logueado como Preceptor/Docente/etc., rompiendo esas pantallas.
  const { data: inscripcion } = useMiInscripcion(params.id, enDetalleMateria);

  const enLegajoAlumno = /^\/(preceptor|administrativo|director)\/alumnos\/[^/]+$/.test(location.pathname);
  const { data: legajo } = useLegajoAlumno(params.id, enLegajoAlumno);

  const enDetalleComision = /^\/docente\/mis-comisiones\/[^/]+$/.test(location.pathname);
  const { data: comision } = useConfiguracionComision(enDetalleComision ? params.sectionId : undefined);

  const enRevisarConfiguracion = /^\/director\/revisar-configuracion\/[^/]+$/.test(location.pathname);
  const { data: configRevisar } = useConfiguracionComisionStaff(enRevisarConfiguracion ? params.sectionId : undefined);

  if (enLegajoAlumno) {
    const rolBase = location.pathname.split('/')[1];
    const tabOrigen = searchParams.get('tab');
    const items = [
      { label: 'Padrón de alumnos', to: `/${rolBase}/alumnos` },
    ];
    if (tabOrigen && ETIQUETA_TAB_PADRON[tabOrigen]) {
      items.push({ label: ETIQUETA_TAB_PADRON[tabOrigen], to: `/${rolBase}/alumnos?tab=${tabOrigen}` });
    }
    items.push({ label: legajo ? `${legajo.apellido}, ${legajo.nombre}` : <Skeleton className="h-4 w-24" /> });
    return items;
  }

  if (enDetalleComision) {
    return [
      { label: 'Mis comisiones', to: '/docente/mis-comisiones' },
      { label: comision ? `${comision.subjectName} · ${comision.sectionName}` : <Skeleton className="h-4 w-24" /> },
    ];
  }

  if (enRevisarConfiguracion) {
    return [
      { label: 'Aprobaciones', to: '/director/aprobaciones' },
      { label: configRevisar ? `${configRevisar.subjectName} · ${configRevisar.sectionName}` : <Skeleton className="h-4 w-24" /> },
    ];
  }

  if (enDetalleMateria) {
    return [
      { label: 'Mis carreras', to: '/alumno/mis-carreras' },
      { label: 'Materias', to: '/alumno/mis-carreras?tab=materias' },
      { label: inscripcion ? inscripcion.subjectName : <Skeleton className="h-4 w-24" /> },
    ];
  }

  if (location.pathname === '/alumno/mis-carreras') {
    // Materias es la pestaña por default de Mis carreras (mismo contenido que la raíz de
    // la sección) — mostrar un segundo nivel redundante ("Mis carreras > Materias") ahí
    // sería ruido. Para el resto de las pestañas sí tiene sentido, siguiendo el pedido
    // explícito del usuario: "si apreto en Notas dentro de Carreras que arriba se
    // muestre Mi Carrera > Notas".
    const tab = searchParams.get('tab') ?? 'materias';
    if (tab === 'materias') {
      return [{ label: 'Mis carreras' }];
    }
    return [{ label: 'Mis carreras', to: '/alumno/mis-carreras' }, { label: ETIQUETA_TAB[tab] ?? tab }];
  }

  return [];
}
