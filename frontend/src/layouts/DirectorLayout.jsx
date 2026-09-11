import { Home, CheckCircle2, ClipboardCheck, Users, UserCog } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/director/inicio', label: 'Inicio', icon: Home },
  { to: '/director/alumnos', label: 'Alumnos', icon: Users },
  { to: '/director/personal', label: 'Personal', icon: UserCog },
  { to: '/director/aprobaciones', label: 'Aprobaciones', icon: CheckCircle2 },
  { to: '/director/esquemas-evaluacion', label: 'Esquemas de evaluación', icon: ClipboardCheck },
];

export function DirectorLayout() {
  return <RoleShell titulo="Director" items={ITEMS} />;
}
