import { Home, CalendarCheck } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/docente/inicio', label: 'Inicio', icon: Home },
  { to: '/docente/tomar-asistencia', label: 'Tomar asistencia', icon: CalendarCheck },
];

export function DocenteLayout() {
  return <RoleShell titulo="Docente" items={ITEMS} />;
}
