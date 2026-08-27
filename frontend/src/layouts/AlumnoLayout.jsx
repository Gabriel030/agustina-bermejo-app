import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/alumno/inicio', label: 'Inicio' },
  { to: '/alumno/mis-cursos', label: 'Mis cursos' },
  { to: '/alumno/notas', label: 'Notas' },
  { to: '/alumno/asistencia', label: 'Asistencia' },
  { to: '/alumno/mesas-de-examen', label: 'Mesas de examen' },
  { to: '/alumno/opciones', label: 'Opciones' },
];

export function AlumnoLayout() {
  return <RoleShell titulo="Alumno" items={ITEMS} />;
}
