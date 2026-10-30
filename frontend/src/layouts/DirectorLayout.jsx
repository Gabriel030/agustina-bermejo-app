import { BellRing, Home, CheckCircle2, ClipboardCheck, Users, UserCog, BarChart2, BookOpen, Receipt, Tag } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/director/inicio', label: 'Inicio', icon: Home },
  { to: '/director/alumnos', label: 'Alumnos', icon: Users },
  { to: '/director/personal', label: 'Personal', icon: UserCog },
  { to: '/director/aprobaciones', label: 'Aprobaciones', icon: CheckCircle2 },
  { to: '/director/gestion-academica', label: 'Banco de materias', icon: BookOpen },
  { to: '/director/esquemas-evaluacion', label: 'Esquemas de evaluación', icon: ClipboardCheck },
  { to: '/director/facturacion', label: 'Facturación', icon: Receipt },
  { to: '/director/descuentos', label: 'Descuentos', icon: Tag },
  { to: '/director/recordatorios-de-pago', label: 'Recordatorios de pago', icon: BellRing },
  { to: '/director/financiero', label: 'Financiero', icon: BarChart2 },
];

export function DirectorLayout() {
  return <RoleShell titulo="Director" items={ITEMS} />;
}
