import { Home, CheckCircle2, ClipboardCheck } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/director/inicio', label: 'Inicio', icon: Home },
  { to: '/director/aprobaciones', label: 'Aprobaciones', icon: CheckCircle2 },
  { to: '/director/esquemas-evaluacion', label: 'Esquemas de evaluación', icon: ClipboardCheck },
];

export function DirectorLayout() {
  return <RoleShell titulo="Director" items={ITEMS} />;
}
