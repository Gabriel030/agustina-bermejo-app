import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/director/inicio', label: 'Inicio' },
  { to: '/director/aprobaciones', label: 'Aprobaciones' },
];

export function DirectorLayout() {
  return <RoleShell titulo="Director" items={ITEMS} />;
}
