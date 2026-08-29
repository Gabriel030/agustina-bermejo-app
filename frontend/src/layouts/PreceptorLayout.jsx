import { Home, Users, BookMarked, FileCheck2, CalendarRange } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/preceptor/inicio', label: 'Inicio', icon: Home },
  { to: '/preceptor/alumnos', label: 'Alumnos', icon: Users },
  { to: '/preceptor/oferta-academica', label: 'Oferta académica', icon: BookMarked },
  { to: '/preceptor/mesas-de-examen', label: 'Mesas de examen', icon: FileCheck2 },
  { to: '/preceptor/periodos', label: 'Períodos', icon: CalendarRange },
];

export function PreceptorLayout() {
  return <RoleShell titulo="Preceptor" items={ITEMS} />;
}
