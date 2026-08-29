import { Home, CheckCircle2 } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/director/inicio', label: 'Inicio', icon: Home },
  { to: '/director/aprobaciones', label: 'Aprobaciones', icon: CheckCircle2 },
];

export function DirectorLayout() {
  return <RoleShell titulo="Director" items={ITEMS} />;
}
