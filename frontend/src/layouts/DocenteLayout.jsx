import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/docente/inicio', label: 'Inicio' },
  { to: '/docente/tomar-asistencia', label: 'Tomar asistencia' },
];

export function DocenteLayout() {
  return <RoleShell titulo="Docente" items={ITEMS} />;
}
