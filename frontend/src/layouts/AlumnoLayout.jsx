import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/alumno/inicio', label: 'Inicio' },
  { to: '/alumno/mis-cursos', label: 'Mis cursos' },
  { to: '/alumno/notas', label: 'Notas' },
  { to: '/alumno/asistencia', label: 'Asistencia' },
  { to: '/alumno/mesas-de-examen', label: 'Mesas de examen' },
  // Ruta compartida fuera de RUTAS_POR_ROL (Épica 7 Fase 2): un Alumno ya
  // matriculado también puede sumarse a un curso suelto del catálogo.
  { to: '/catalogo', label: 'Catálogo' },
  { to: '/alumno/opciones', label: 'Opciones' },
];

export function AlumnoLayout() {
  return <RoleShell titulo="Alumno" items={ITEMS} />;
}
